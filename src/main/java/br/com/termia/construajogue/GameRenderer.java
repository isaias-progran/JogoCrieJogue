package br.com.termia.construajogue;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import br.com.termia.construajogue.engine.FpsCamera;
import br.com.termia.construajogue.engine.Mesh;
import br.com.termia.construajogue.engine.Shader;
import br.com.termia.construajogue.engine.Sky;
import br.com.termia.construajogue.game.Enemy;
import br.com.termia.construajogue.game.GameState;
import br.com.termia.construajogue.game.GameResult;
import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.runtime.LevelProvider;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.runtime.RuntimeDoor;
import br.com.termia.construajogue.runtime.RuntimeNpc;
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

    /** Avisos vindos da thread GL; a Activity leva para a thread de UI. */
    public interface Listener {
        void onGlReady(String detail);

        void onGlError(String message);

        /** Uma vez por segundo, com a contagem de quadros do último segundo. */
        void onFps(int fps);

        void onGameResult(GameResult result);

        /** A Activity abre o diálogo fora da thread GL. */
        void onNpcInteraction(RuntimeNpc npc, String mapName);

        /** A Activity fala a saudação sem abrir diálogo. */
        void onNpcGreeting(RuntimeNpc npc);
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
            + "uniform float uTime;\n"
            + "uniform int uLightCount;\n"
            + "uniform vec4 uLightPosRadius[4];\n"
            + "uniform vec3 uLightColor[4];\n"
            + "uniform float uAlpha;\n"
            + "out vec4 outColor;\n"
            + "const vec3 LIGHT = vec3(0.37139, 0.74278, 0.55708);\n"
            + "void main() {\n"
            + "  vec3 n = normalize(vNormal);\n"
            + "  float material = floor(vColor.r / 10.0 + 0.001);\n"
            + "  vec3 base = vec3(vColor.r - material * 10.0, vColor.gb);\n"
            + "  vec2 uv = abs(n.y) > 0.7 ? vWorld.xz : "
            + "(abs(n.x) > 0.7 ? vWorld.zy : vWorld.xy);\n"
            + "  if (material > 0.5 && material < 1.5) {\n"
            + "    float row = floor(uv.y * 2.0);\n"
            + "    vec2 q = fract(vec2(uv.x * 2.0 + mod(row, 2.0) * 0.5, "
            + "uv.y * 2.0));\n"
            + "    float mortar = step(0.08, q.x) * step(0.10, q.y);\n"
            + "    base *= mix(0.48, 1.08, mortar);\n"
            + "  } else if (material > 1.5 && material < 2.5) {\n"
            + "    float grain = 0.82 + 0.18 * sin(uv.x * 18.0 "
            + "+ sin(uv.y * 3.0) * 2.0);\n"
            + "    base *= grain;\n"
            + "  } else if (material > 2.5 && material < 3.5) {\n"
            + "    float tile = mod(floor(uv.x * 2.0) + floor(uv.y * 2.0), 2.0);\n"
            + "    base *= mix(0.42, 1.18, tile);\n"
            + "  } else if (material > 3.5 && material < 4.5) {\n"
            + "    base *= 0.82 + 0.18 * abs(sin(uv.y * 28.0));\n"
            + "  } else if (material > 4.5 && material < 5.5) {\n"
            + "    float wave = sin((uv.x + uv.y) * 5.0 + uTime * 2.2);\n"
            + "    base = mix(base * 0.72, vec3(0.12, 0.52, 0.90), "
            + "0.28 + wave * 0.08);\n"
            + "  } else if (material > 5.5 && material < 6.5) {\n"
            + "    float flow = sin(uv.x * 7.0 + uTime * 3.0) "
            + "+ sin(uv.y * 5.0 - uTime * 2.0);\n"
            + "    base = mix(base, vec3(1.25, 0.48, 0.05), "
            + "0.35 + 0.12 * flow);\n"
            + "  } else if (material > 6.5 && material < 7.5) {\n"
            + "    vec2 cell = floor(uv * 14.0);\n"
            + "    float grain = fract(sin(dot(cell, "
            + "vec2(12.9898, 78.233))) * 43758.5453);\n"
            + "    float seam = smoothstep(0.965, 1.0, "
            + "abs(sin(uv.x * 1.7 + sin(uv.y * 1.9))));\n"
            + "    base *= (0.78 + 0.20 * grain) * (1.0 - seam * 0.20);\n"
            + "  }\n"
            + "  float diff = max(dot(n, LIGHT), 0.0);\n"
            + "  vec3 col = base * uTint * (uAmbient + (1.0 - uAmbient) * diff);\n"
            + "  for (int i = 0; i < 4; i++) {\n"
            + "    if (i >= uLightCount) break;\n"
            + "    vec3 delta = uLightPosRadius[i].xyz - vWorld;\n"
            + "    float d = length(delta);\n"
            + "    float radius = uLightPosRadius[i].w;\n"
            + "    float att = pow(max(0.0, 1.0 - d / radius), 2.0);\n"
            + "    float ndl = max(dot(n, delta / max(d, 0.001)), 0.0);\n"
            + "    col += base * uTint * uLightColor[i] * att "
            + "* (0.18 + 0.82 * ndl);\n"
            + "  }\n"
            + "  if (material > 5.5 && material < 6.5) "
            + "col += base * 0.55;\n"
            + "  if (uGrid > 0.5 && abs(n.y) > 0.9) {\n" // grade de 1m
            + "    vec2 g = abs(fract(vWorld.xz) - 0.5);\n"
            + "    float line = smoothstep(0.46, 0.5, max(g.x, g.y));\n"
            + "    col += vec3(0.04, 0.07, 0.10) * line;\n"
            + "  }\n"
            + "  float dist = length(vWorld - uEye);\n"
            + "  float fog = smoothstep(uFogFar * 0.35, uFogFar, dist);\n"
            + "  outColor = vec4(mix(col, uFogColor, fog), uAlpha);\n"
            + "}\n";

    private final Listener listener;
    private final TouchControls controls;
    private final LevelProvider levels;
    private final Sounds sounds;
    private final Hud hud;
    private final FpsCamera camera = new FpsCamera();
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] viewProj = new float[16];
    private final float[] skyView = new float[16];
    private final float[] skyViewProj = new float[16];
    private final float[] eye = new float[3];

    private RuntimeLevel level;
    private GameState game;
    private Mesh levelMesh;
    private Mesh[] doorMeshes = new Mesh[0];
    private GameMeshes meshes;
    private Sky sky;
    private int program;
    private int viewProjLoc;
    private int offsetLoc;
    private int eyeLoc;
    private int ambientLoc;
    private int fogColorLoc;
    private int fogFarLoc;
    private int tintLoc;
    private int gridLoc;
    private int timeLoc;
    private int lightCountLoc;
    private int lightPosRadiusLoc;
    private int lightColorLoc;
    private int alphaLoc;
    private final float[] lightPosRadius = new float[16];
    private final float[] lightColor = new float[12];
    private final int[] nearestLights = new int[4];
    private final float[] nearestLightDist = new float[4];
    private boolean ready;
    private long lastFrame;
    private int frames;
    private long fpsWindowStart;
    private float gameTime;
    private int levelIndex;

    public GameRenderer(Listener listener, TouchControls controls,
                        LevelProvider levels, Sounds sounds, Hud hud) {
        this.listener = listener;
        this.controls = controls;
        this.levels = levels;
        this.sounds = sounds;
        this.hud = hud;
    }

    /** Carrega somente dados/estado; os VBOs dependem do contexto atual. */
    private void loadLevelState(int index, float priorTime) throws IOException {
        RuntimeLevel loaded = levels.load(index);
        sounds.setAmbient(loaded.skyMode(), loaded.soundscapeMode());
        GameState next = new GameState(loaded, camera, controls, sounds, hud,
                index + 1, levels.count(), priorTime);
        level = loaded;
        game = next;
        levelIndex = index;
    }

    private void uploadLevelMeshes(boolean replacing) {
        if (replacing) {
            if (levelMesh != null) {
                levelMesh.release();
            }
            for (Mesh mesh : doorMeshes) {
                if (mesh != null) mesh.release();
            }
        }
        levelMesh = new Mesh(level.vertexData());
        RuntimeDoor[] doors = level.doors();
        doorMeshes = new Mesh[doors.length];
        for (int i = 0; i < doors.length; i++) {
            doorMeshes[i] = new Mesh(doors[i].vertexData);
        }
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
            timeLoc = GLES30.glGetUniformLocation(program, "uTime");
            lightCountLoc = GLES30.glGetUniformLocation(program,
                    "uLightCount");
            lightPosRadiusLoc = GLES30.glGetUniformLocation(program,
                    "uLightPosRadius[0]");
            lightColorLoc = GLES30.glGetUniformLocation(program,
                    "uLightColor[0]");
            alphaLoc = GLES30.glGetUniformLocation(program, "uAlpha");
            uploadLevelMeshes(false);
            meshes = new GameMeshes();
            sky = new Sky();
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
        RuntimeNpc greetingNpc = game.takeNpcGreeting();
        if (greetingNpc != null && listener != null) {
            listener.onNpcGreeting(greetingNpc);
        }
        RuntimeNpc requestedNpc = game.takeNpcInteraction();
        if (requestedNpc != null && listener != null) {
            listener.onNpcInteraction(requestedNpc, level.mapName());
        }
        GameResult result = game.takeResult();
        if (result != null && listener != null) {
            listener.onGameResult(result);
        }
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

        // skybox primeiro: view sem translação, sempre atrás de tudo
        if (level.skyMode() != RuntimeLevel.SKY_NONE) {
            System.arraycopy(view, 0, skyView, 0, 16);
            skyView[12] = 0f;
            skyView[13] = 0f;
            skyView[14] = 0f;
            Matrix.multiplyMM(skyViewProj, 0, projection, 0, skyView, 0);
            sky.draw(skyViewProj, level.skyMode(), fog);
        }

        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false, viewProj, 0);
        GLES30.glUniform3f(eyeLoc, eye[0], eye[1], eye[2]);
        GLES30.glUniform1f(ambientLoc, level.ambient());
        GLES30.glUniform3f(fogColorLoc, fog[0], fog[1], fog[2]);
        GLES30.glUniform1f(fogFarLoc, level.fogFar());
        GLES30.glUniform1f(timeLoc, gameTime);
        GLES30.glUniform1f(alphaLoc, 1f);
        uploadNearestLights();

        // cenário
        GLES30.glUniform3f(offsetLoc, 0f, 0f, 0f);
        GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
        GLES30.glUniform1f(gridLoc, 1f);
        levelMesh.draw();
        GLES30.glUniform1f(gridLoc, 0f);

        // sombras blob baratas: leitura espacial sem mapa de sombras
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,
                GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
        GLES30.glUniform1f(alphaLoc, 0.34f);
        GLES30.glUniform3f(tintLoc, 0.5f, 0.5f, 0.5f);
        float playerRayY = game.playerY() + 0.1f;
        float playerDown = br.com.termia.construajogue.engine.Raycast.hitBoxes(
                game.playerX(), playerRayY, game.playerZ(),
                0f, -1f, 0f, level.colliders());
        float playerGround = playerDown
                == br.com.termia.construajogue.engine.Raycast.MISS
                ? game.playerY() - 0.01f
                : playerRayY - playerDown + 0.015f;
        GLES30.glUniform3f(offsetLoc, game.playerX(),
                playerGround, game.playerZ());
        meshes.shadow.draw();
        for (Enemy enemy : game.enemies()) {
            float ground = enemy.y() - 0.01f;
            float down = br.com.termia.construajogue.engine.Raycast.hitBoxes(
                    enemy.x(), enemy.y(), enemy.z(), 0f, -1f, 0f,
                    level.colliders());
            if (down != br.com.termia.construajogue.engine.Raycast.MISS) {
                ground = enemy.y() - down + 0.015f;
            }
            GLES30.glUniform3f(offsetLoc, enemy.x(), ground, enemy.z());
            meshes.shadow.draw();
        }
        for (RuntimeNpc npc : level.npcs()) {
            GLES30.glUniform3f(offsetLoc, npc.x, npc.y + 0.015f, npc.z);
            meshes.shadow.draw();
        }
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glUniform1f(alphaLoc, 1f);

        // portas dinâmicas: portão desce; folha automática desliza
        RuntimeDoor[] doors = level.doors();
        for (int i = 0; i < doorMeshes.length; i++) {
            float progress = game.doorProgress(i);
            GLES30.glUniform3f(offsetLoc, doors[i].moveX * progress,
                    doors[i].moveY * progress,
                    doors[i].moveZ * progress);
            doorMeshes[i].draw();
        }

        // luz de status do terminal: laranja pulsando -> verde fixo
        br.com.termia.construajogue.runtime.RuntimeTerminal[] terminals =
                level.terminals();
        for (int i = 0; i < terminals.length; i++) {
            GLES30.glUniform3f(offsetLoc, terminals[i].x, terminals[i].y,
                    terminals[i].z);
            if (game.terminalActive(i)) {
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
            } else if (item[0] == RuntimeLevel.ITEM_AMMO) {
                meshes.ammo.draw();
            } else if (item[0] == RuntimeLevel.ITEM_SPECIAL) {
                meshes.special.draw();
            } else {
                meshes.token.draw();
            }
        }

        // Pessoas amigáveis respiram paradas e saltitam ao acompanhar.
        GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
        for (RuntimeNpc npc : level.npcs()) {
            float speed = npc.moving ? 8.5f : 1.8f;
            float amplitude = npc.moving ? 0.045f : 0.008f;
            float breathe = amplitude * (float) Math.sin(
                    gameTime * speed + npc.x * 0.3f);
            GLES30.glUniform3f(offsetLoc, npc.x, npc.y + breathe, npc.z);
            meshes.human.draw();
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
            } else if (enemy.type() == Enemy.TYPE_BOSS) {
                GLES30.glUniform3f(tintLoc, 1.15f, 0.55f, 1.25f);
            } else if (enemy.type() == Enemy.TYPE_KAMIKAZE) {
                GLES30.glUniform3f(tintLoc, 1.35f, 0.65f, 0.25f);
            } else {
                GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
            }
            if (enemy.type() == Enemy.TYPE_MUTANT) {
                meshes.mutant.draw();
            } else if (enemy.type() == Enemy.TYPE_TURRET) {
                meshes.turret.draw();
            } else if (enemy.type() == Enemy.TYPE_KAMIKAZE) {
                meshes.kamikaze.draw();
            } else if (enemy.type() == Enemy.TYPE_BOSS) {
                meshes.boss.draw();
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
        GLES30.glUniform1i(lightCountLoc, 0);
        GLES30.glUniform1f(alphaLoc, 1f);
        GLES30.glUniform3f(offsetLoc, 0f, 0f, game.recoil());
        GLES30.glUniform3f(tintLoc, 1f, 1f, 1f);
        meshes.gun.draw();
        if (game.muzzleVisible()) {
            GLES30.glUniform3f(tintLoc, 2.5f, 2.2f, 1.2f);
            meshes.flash.draw();
        }
    }

    /** Escolhe no máximo quatro luzes mais próximas sem alocar no quadro. */
    private void uploadNearestLights() {
        float[][] lights = level.lights();
        int count = Math.min(4, lights.length);
        for (int i = 0; i < 4; i++) {
            nearestLights[i] = -1;
            nearestLightDist[i] = Float.MAX_VALUE;
        }
        for (int i = 0; i < lights.length; i++) {
            float dx = lights[i][0] - eye[0];
            float dy = lights[i][1] - eye[1];
            float dz = lights[i][2] - eye[2];
            float d = dx * dx + dy * dy + dz * dz;
            for (int slot = 0; slot < 4; slot++) {
                if (d < nearestLightDist[slot]) {
                    for (int move = 3; move > slot; move--) {
                        nearestLightDist[move] = nearestLightDist[move - 1];
                        nearestLights[move] = nearestLights[move - 1];
                    }
                    nearestLightDist[slot] = d;
                    nearestLights[slot] = i;
                    break;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            float[] l = lights[nearestLights[i]];
            lightPosRadius[i * 4] = l[0];
            lightPosRadius[i * 4 + 1] = l[1];
            lightPosRadius[i * 4 + 2] = l[2];
            lightPosRadius[i * 4 + 3] = Math.max(0.1f, l[6]);
            lightColor[i * 3] = l[3];
            lightColor[i * 3 + 1] = l[4];
            lightColor[i * 3 + 2] = l[5];
        }
        GLES30.glUniform1i(lightCountLoc, count);
        if (count > 0) {
            GLES30.glUniform4fv(lightPosRadiusLoc, count,
                    lightPosRadius, 0);
            GLES30.glUniform3fv(lightColorLoc, count, lightColor, 0);
        }
    }
}
