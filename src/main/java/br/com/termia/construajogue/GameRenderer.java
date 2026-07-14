package br.com.termia.construajogue;

import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import br.com.termia.construajogue.engine.FpsCamera;
import br.com.termia.construajogue.engine.Mesh;
import br.com.termia.construajogue.engine.Shader;
import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.game.Enemy;
import br.com.termia.construajogue.game.GameState;
import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.ui.Hud;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer da campanha de dois setores; a lógica mora no GameState. Um
 * único shader: uOffset desloca a malha (inimigos,
 * itens, portão, recuo da arma), uTint dá flash/emissivo/estado, uGrid liga
 * a grade só no cenário. A arma é desenhada em espaço de visão com depth
 * limpo. Nenhum `new` em onDrawFrame.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {

    private static final String[] LEVEL_PATHS = {
            "maps/arena.json", "levels/labirinto.txt"
    };

    /** Avisos vindos da thread GL; a Activity leva para a thread de UI. */
    public interface Listener {
        void onGlReady(String detail);

        void onGlError(String message);

        /** Uma vez por segundo, com a contagem de quadros do último segundo. */
        void onFps(int fps);
    }

    private static final String SCENE_VERTEX = ""
            + "#version 300 es\n"
            + "uniform mat4 uViewProj;\n"
            + "uniform vec3 uOffset;\n"
            + "layout(location = 0) in vec3 aPos;\n"
            + "layout(location = 1) in vec3 aNormal;\n"
            + "layout(location = 2) in vec3 aColor;\n"
            + "out vec3 vWorld;\n"
            + "out vec3 vNormal;\n"
            + "out vec3 vColor;\n"
            + "void main() {\n"
            + "  vWorld = aPos + uOffset;\n"
            + "  vNormal = aNormal;\n"
            + "  vColor = aColor;\n"
            + "  gl_Position = uViewProj * vec4(vWorld, 1.0);\n"
            + "}\n";

    private static final String SCENE_FRAGMENT = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 vWorld;\n"
            + "in vec3 vNormal;\n"
            + "in vec3 vColor;\n"
            + "uniform vec3 uEye;\n"
            + "uniform float uAmbient;\n"
            + "uniform vec3 uFogColor;\n"
            + "uniform float uFogFar;\n"
            + "uniform vec3 uTint;\n"
            + "uniform float uGrid;\n"
            + "out vec4 outColor;\n"
            + "const vec3 LIGHT = vec3(0.37139, 0.74278, 0.55708);\n"
            + "void main() {\n"
            + "  vec3 n = normalize(vNormal);\n"
            + "  float diff = max(dot(n, LIGHT), 0.0);\n"
            + "  vec3 col = vColor * uTint * (uAmbient + (1.0 - uAmbient) * diff);\n"
            + "  if (uGrid > 0.5 && abs(n.y) > 0.9) {\n" // grade de 1m
            + "    vec2 g = abs(fract(vWorld.xz) - 0.5);\n"
            + "    float line = smoothstep(0.46, 0.5, max(g.x, g.y));\n"
            + "    col += vec3(0.04, 0.07, 0.10) * line;\n"
            + "  }\n"
            + "  float dist = length(vWorld - uEye);\n"
            + "  float fog = smoothstep(uFogFar * 0.35, uFogFar, dist);\n"
            + "  outColor = vec4(mix(col, uFogColor, fog), 1.0);\n"
            + "}\n";

    private final Listener listener;
    private final TouchControls controls;
    private final AssetManager assets;
    private final Sounds sounds;
    private final Hud hud;
    private final FpsCamera camera = new FpsCamera();
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] viewProj = new float[16];
    private final float[] eye = new float[3];

    private RuntimeLevel level;
    private GameState game;
    private PrefabCatalog catalog;
    private Mesh levelMesh;
    private Mesh doorMesh;
    private GameMeshes meshes;
    private int program;
    private int viewProjLoc;
    private int offsetLoc;
    private int eyeLoc;
    private int ambientLoc;
    private int fogColorLoc;
    private int fogFarLoc;
    private int tintLoc;
    private int gridLoc;
    private boolean ready;
    private long lastFrame;
    private int frames;
    private long fpsWindowStart;
    private float gameTime;
    private float doorHeight;
    private int levelIndex;

    public GameRenderer(Listener listener, TouchControls controls,
                        AssetManager assets, Sounds sounds, Hud hud) {
        this.listener = listener;
        this.controls = controls;
        this.assets = assets;
        this.sounds = sounds;
        this.hud = hud;
    }

    /** Carrega somente dados/estado; os VBOs dependem do contexto atual. */
    private void loadLevelState(int index, float priorTime) throws IOException {
        RuntimeLevel loaded = loadLevel(LEVEL_PATHS[index]);
        GameState next = new GameState(loaded, camera, controls, sounds, hud,
                index + 1, LEVEL_PATHS.length, priorTime);
        level = loaded;
        game = next;
        levelIndex = index;
        doorHeight = level.doorOriginal() == null ? 0f
                : level.doorOriginal()[4] - level.doorOriginal()[1];
    }

    /** .json = documento validado e compilado; .txt = formato legado. */
    private RuntimeLevel loadLevel(String path) throws IOException {
        if (!path.endsWith(".json")) {
            return LegacyLevelLoader.load(assets.open(path), path);
        }
        if (catalog == null) {
            catalog = PrefabCatalog.load(assets.open("prefabs/catalog.json"));
        }
        MapDocument doc = MapJson.read(readAsset(path));
        java.util.List<ValidationIssue> issues =
                MapValidator.validate(doc, catalog);
        if (MapValidator.hasError(issues)) {
            for (ValidationIssue issue : issues) {
                if (issue.isError()) {
                    throw new IOException(path + ": " + issue);
                }
            }
        }
        return LevelCompiler.compile(doc, catalog);
    }

    private String readAsset(String path) throws IOException {
        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = assets.open(path)) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
        }
        return new String(buffer.toByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private void uploadLevelMeshes(boolean replacing) {
        if (replacing) {
            if (levelMesh != null) {
                levelMesh.release();
            }
            if (doorMesh != null) {
                doorMesh.release();
            }
        }
        levelMesh = new Mesh(level.vertexData());
        doorMesh = level.doorVertexData() == null ? null
                : new Mesh(level.doorVertexData());
        float[] fog = level.fogColor();
        GLES30.glClearColor(fog[0], fog[1], fog[2], 1f);
    }

    private void switchLevel(int index, float priorTime) throws IOException {
        loadLevelState(index, priorTime);
        uploadLevelMeshes(true);
        if (listener != null) {
            listener.onGlReady("setor " + (index + 1) + ": "
                    + level.boxCount() + " caixas, "
                    + game.enemies().length + " inimigos");
        }
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            if (level == null) {
                loadLevelState(0, 0f);
            }
            program = Shader.build(SCENE_VERTEX, SCENE_FRAGMENT);
            viewProjLoc = GLES30.glGetUniformLocation(program, "uViewProj");
            offsetLoc = GLES30.glGetUniformLocation(program, "uOffset");
            eyeLoc = GLES30.glGetUniformLocation(program, "uEye");
            ambientLoc = GLES30.glGetUniformLocation(program, "uAmbient");
            fogColorLoc = GLES30.glGetUniformLocation(program, "uFogColor");
            fogFarLoc = GLES30.glGetUniformLocation(program, "uFogFar");
            tintLoc = GLES30.glGetUniformLocation(program, "uTint");
            gridLoc = GLES30.glGetUniformLocation(program, "uGrid");
            uploadLevelMeshes(false);
            meshes = new GameMeshes();
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glEnable(GLES30.GL_CULL_FACE);
            ready = true;
            lastFrame = 0;
            frames = 0;
            fpsWindowStart = SystemClock.uptimeMillis();
            if (listener != null) {
                listener.onGlReady("setor " + (levelIndex + 1) + ": "
                        + level.boxCount() + " caixas, "
                        + game.enemies().length + " inimigos");
            }
        } catch (IOException | RuntimeException failure) {
            ready = false;
            if (listener != null) {
                listener.onGlError(failure.getMessage());
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        float aspect = height == 0 ? 1f : (float) width / height;
        Matrix.perspectiveM(projection, 0, 70f, aspect, 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        if (!ready) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        // primeiro quadro (ou volta de pausa longa) não pode dar dt gigante
        float dt = lastFrame == 0 ? 0.016f
                : Math.min((now - lastFrame) / 1000f, 0.05f);
        lastFrame = now;
        if (!controls.paused()) {
            gameTime += dt;
        }

        game.update(dt, gameTime);
        try {
            if (game.takeAdvanceRequest()) {
                switchLevel(levelIndex + 1, game.elapsedCampaignTime());
            } else if (game.takeCampaignRestartRequest()) {
                gameTime = 0f;
                switchLevel(0, 0f);
            }
        } catch (IOException | RuntimeException failure) {
            ready = false;
            if (listener != null) {
                listener.onGlError(failure.getMessage());
            }
            return;
        }
        drawScene();

        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR && listener != null) {
            ready = false; // não repetir o erro a cada frame
            listener.onGlError("Erro GL 0x" + Integer.toHexString(error));
            return;
        }
        frames++;
        if (now - fpsWindowStart >= 1000L) {
            if (listener != null) {
                listener.onFps(frames);
            }
            frames = 0;
            fpsWindowStart = now;
        }
    }

    private void drawScene() {
        camera.computeView(view);
        Matrix.multiplyMM(viewProj, 0, projection, 0, view, 0);
        camera.eyeInto(eye);
        float[] fog = level.fogColor();

        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false, viewProj, 0);
        GLES30.glUniform3f(eyeLoc, eye[0], eye[1], eye[2]);
        GLES30.glUniform1f(ambientLoc, level.ambient());
        GLES30.glUniform3f(fogColorLoc, fog[0], fog[1], fog[2]);
        GLES30.glUniform1f(fogFarLoc, level.fogFar());

        // cenário
        GLES30.glUniform3f(offsetLoc, 0f, 0f, 0f);
        GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
        GLES30.glUniform1f(gridLoc, 1f);
        levelMesh.draw();
        GLES30.glUniform1f(gridLoc, 0f);

        // portão descendo no chão
        if (doorMesh != null) {
            GLES30.glUniform3f(offsetLoc, 0f,
                    -game.doorProgress() * doorHeight, 0f);
            doorMesh.draw();
        }

        // luz de status do terminal: laranja pulsando -> verde fixo
        float[] terminal = level.terminal();
        if (terminal != null) {
            GLES30.glUniform3f(offsetLoc, terminal[0], terminal[1],
                    terminal[2]);
            if (game.terminalActive()) {
                GLES30.glUniform3f(tintLoc, 0.3f, 1.6f, 0.5f);
            } else {
                float pulse = 1.2f + 0.6f * (float) Math.sin(gameTime * 4.0);
                GLES30.glUniform3f(tintLoc, pulse, pulse * 0.5f, 0.15f);
            }
            meshes.terminalLight.draw();
        }

        // itens balançando
        for (float[] item : game.items()) {
            if (item[4] != 0f) {
                continue;
            }
            float bob = 0.06f * (float) Math.sin(gameTime * 2.5 + item[1]);
            GLES30.glUniform3f(offsetLoc, item[1], item[2] + bob, item[3]);
            GLES30.glUniform3f(tintLoc, 1.2f, 1.2f, 1.2f);
            if (item[0] == RuntimeLevel.ITEM_HEALTH) {
                meshes.health.draw();
            } else {
                meshes.ammo.draw();
            }
        }

        // inimigos: mesma linguagem de flash/aviso, malhas distintas
        for (Enemy enemy : game.enemies()) {
            GLES30.glUniform3f(offsetLoc, enemy.x(), enemy.y(), enemy.z());
            if (enemy.flashing()) {
                GLES30.glUniform3f(tintLoc, 2.4f, 2.4f, 2.4f);
            } else if (enemy.telegraphing()) {
                float pulse = 1.4f + 0.8f * (float) Math.sin(gameTime * 25.0);
                GLES30.glUniform3f(tintLoc, pulse, pulse * 0.55f, 0.3f);
            } else if (enemy.wreck()) {
                GLES30.glUniform3f(tintLoc, 0.35f, 0.35f, 0.35f);
            } else if (enemy.dormant()) {
                GLES30.glUniform3f(tintLoc, 0.45f, 0.45f, 0.5f);
            } else if (enemy.type() == Enemy.TYPE_MUTANT) {
                GLES30.glUniform3f(tintLoc, 0.9f, 1.15f, 0.9f);
            } else {
                GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
            }
            if (enemy.type() == Enemy.TYPE_MUTANT) {
                meshes.mutant.draw();
            } else {
                meshes.drone.draw();
            }
        }

        // fagulhas de impacto
        GLES30.glUniform3f(tintLoc, 2.2f, 2.2f, 2.2f);
        for (float[] impact : game.impacts()) {
            if (impact[3] > 0f) {
                GLES30.glUniform3f(offsetLoc, impact[0], impact[1], impact[2]);
                meshes.impact.draw();
            }
        }

        // arma em espaço de visão: depth limpo p/ nunca entrar na parede
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false, projection, 0);
        GLES30.glUniform3f(eyeLoc, 0f, 0f, 0f);
        GLES30.glUniform1f(fogFarLoc, 1000f); // sem neblina na arma
        GLES30.glUniform3f(offsetLoc, 0f, 0f, game.recoil());
        GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
        meshes.gun.draw();
        if (game.muzzleVisible()) {
            GLES30.glUniform3f(tintLoc, 2.5f, 2.2f, 1.2f);
            meshes.flash.draw();
        }
    }
}
