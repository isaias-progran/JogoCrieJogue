package br.com.termia.construajogue.editor;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.engine.Boxes;
import br.com.termia.construajogue.engine.Mesh;
import br.com.termia.construajogue.engine.Shader;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.RuntimeDoor;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Prévia orbital ES 3.0 do mapa; só redesenha quando a câmera muda. */
public final class EditorPreviewView extends GLSurfaceView {

    public interface Listener {
        void onPreviewError(String message);
    }

    private final PreviewRenderer previewRenderer;
    private float lastX;
    private float lastY;
    private float lastSpan;

    public EditorPreviewView(Context context, MapDocument doc,
                             PrefabCatalog catalog, Listener listener) {
        super(context);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        float[] data = buildPreviewData(doc, catalog);
        previewRenderer = new PreviewRenderer(data, listener);
        setRenderer(previewRenderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) lastSpan = span(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float next = span(event);
                    if (lastSpan > 8f && next > 8f) {
                        previewRenderer.zoom(lastSpan / next);
                    }
                    lastSpan = next;
                } else {
                    float x = event.getX();
                    float y = event.getY();
                    previewRenderer.orbit(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                requestRender();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastSpan = 0f;
                return true;
            default:
                return true;
        }
    }

    private static float span(MotionEvent event) {
        return (float) Math.hypot(event.getX(1) - event.getX(0),
                event.getY(1) - event.getY(0));
    }

    /** Junta cenário, folhas de porta e símbolos simples da lógica. */
    private static float[] buildPreviewData(MapDocument doc,
                                            PrefabCatalog catalog) {
        RuntimeLevel level = LevelCompiler.compile(doc, catalog);
        int extraBoxes = doc.markers.size();
        for (PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def != null && !PrefabDefinition.BEHAVIOR_STATIC
                    .equals(def.behavior)
                    && !PrefabDefinition.BEHAVIOR_DOOR.equals(def.behavior)
                    && !PrefabDefinition.BEHAVIOR_AUTO_DOOR
                    .equals(def.behavior)) {
                extraBoxes++;
            }
        }
        int doorsFloats = 0;
        for (RuntimeDoor door : level.doors()) {
            doorsFloats += door.vertexData.length;
        }
        float[] out = new float[level.vertexData().length + doorsFloats
                + extraBoxes * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        System.arraycopy(level.vertexData(), 0, out, cursor,
                level.vertexData().length);
        cursor += level.vertexData().length;
        for (RuntimeDoor door : level.doors()) {
            System.arraycopy(door.vertexData, 0, out, cursor,
                    door.vertexData.length);
            cursor += door.vertexData.length;
        }
        for (LogicMarker marker : doc.markers) {
            boolean spawn = LogicMarker.PLAYER_SPAWN.equals(marker.type);
            cursor = Boxes.emitCentered(out, cursor, marker.x,
                    marker.y + 0.55f, marker.z,
                    0.22f, 0.55f, 0.22f,
                    spawn ? 0.20f : 0.15f,
                    spawn ? 0.90f : 0.75f,
                    spawn ? 0.35f : 1.00f);
        }
        for (PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null || PrefabDefinition.BEHAVIOR_STATIC
                    .equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_DOOR.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_AUTO_DOOR
                    .equals(def.behavior)) continue;
            boolean human = PrefabDefinition.BEHAVIOR_NPC_HUMAN
                    .equals(def.behavior);
            float r = human ? 0.25f
                    : def.behavior.startsWith("pickup") ? 0.95f
                    : PrefabDefinition.BEHAVIOR_TERMINAL.equals(def.behavior)
                    ? 0.95f : 0.85f;
            float g = human ? 0.68f
                    : def.behavior.startsWith("pickup") ? 0.72f
                    : PrefabDefinition.BEHAVIOR_TERMINAL.equals(def.behavior)
                    ? 0.48f : 0.20f;
            float b = human ? 0.88f
                    : def.behavior.startsWith("pickup") ? 0.18f
                    : PrefabDefinition.BEHAVIOR_TERMINAL.equals(def.behavior)
                    ? 0.10f : 0.18f;
            float half = human ? 0.85f
                    : def.behavior.startsWith("pickup") ? 0.18f : 0.32f;
            float centerY = human ? p.transform.y + half
                    : Math.max(half, p.transform.y);
            cursor = Boxes.emitCentered(out, cursor, p.transform.x,
                    centerY, p.transform.z,
                    half, half, half, r, g, b);
        }
        return out;
    }

    private static final class PreviewRenderer implements Renderer {

        private static final String VERTEX = "#version 300 es\n"
                + "uniform mat4 uMvp;\n"
                + "layout(location=0) in vec3 aPos;\n"
                + "layout(location=1) in vec3 aNormal;\n"
                + "layout(location=2) in vec3 aColor;\n"
                + "out vec3 vNormal; out vec3 vColor;\n"
                + "void main(){vNormal=aNormal;vColor=aColor;"
                + "gl_Position=uMvp*vec4(aPos,1.0);}\n";
        private static final String FRAGMENT = "#version 300 es\n"
                + "precision mediump float;\n"
                + "in vec3 vNormal; in vec3 vColor; out vec4 outColor;\n"
                + "void main(){float m=floor(vColor.r/10.0+0.001);"
                + "vec3 base=vec3(vColor.r-m*10.0,vColor.gb);"
                + "float d=max(dot(normalize(vNormal),"
                + "normalize(vec3(.45,.8,.55))),0.0);"
                + "outColor=vec4(base*(.35+.65*d),1.0);}\n";

        private final float[] data;
        private final Listener listener;
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] mvp = new float[16];
        private float centerX;
        private float centerY;
        private float centerZ;
        private float distance;
        private float minDistance;
        private float yaw = 45f;
        private float pitch = 42f;
        private Mesh mesh;
        private int program;
        private int mvpLoc;
        private boolean ready;

        PreviewRenderer(float[] data, Listener listener) {
            this.data = data;
            this.listener = listener;
            frameData();
        }

        void orbit(float dx, float dy) {
            yaw = (yaw + dx * 0.45f) % 360f;
            pitch = Math.max(12f, Math.min(82f, pitch + dy * 0.35f));
        }

        void zoom(float factor) {
            distance = Math.max(minDistance,
                    Math.min(minDistance * 10f, distance * factor));
        }

        private void frameData() {
            if (data.length < 9) {
                minDistance = distance = 8f;
                return;
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i + 8 < data.length; i += 9) {
                minX = Math.min(minX, data[i]);
                minY = Math.min(minY, data[i + 1]);
                minZ = Math.min(minZ, data[i + 2]);
                maxX = Math.max(maxX, data[i]);
                maxY = Math.max(maxY, data[i + 1]);
                maxZ = Math.max(maxZ, data[i + 2]);
            }
            centerX = (minX + maxX) * 0.5f;
            centerY = (minY + maxY) * 0.5f;
            centerZ = (minZ + maxZ) * 0.5f;
            float span = Math.max(maxX - minX, maxZ - minZ);
            span = Math.max(span, maxY - minY);
            minDistance = Math.max(2.5f, span * 0.55f);
            distance = Math.max(6f, span * 1.15f);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            try {
                program = Shader.build(VERTEX, FRAGMENT);
                mvpLoc = GLES30.glGetUniformLocation(program, "uMvp");
                mesh = new Mesh(data);
                GLES30.glEnable(GLES30.GL_DEPTH_TEST);
                GLES30.glEnable(GLES30.GL_CULL_FACE);
                GLES30.glClearColor(0.055f, 0.075f, 0.095f, 1f);
                ready = true;
            } catch (RuntimeException failure) {
                ready = false;
                if (listener != null) listener.onPreviewError(
                        failure.getMessage());
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES30.glViewport(0, 0, width, height);
            Matrix.perspectiveM(projection, 0, 55f,
                    height == 0 ? 1f : (float) width / height,
                    0.1f, Math.max(100f, distance * 12f));
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT
                    | GLES30.GL_DEPTH_BUFFER_BIT);
            if (!ready) return;
            double ya = Math.toRadians(yaw);
            double pi = Math.toRadians(pitch);
            float horizontal = distance * (float) Math.cos(pi);
            float eyeX = centerX + horizontal * (float) Math.sin(ya);
            float eyeY = centerY + distance * (float) Math.sin(pi);
            float eyeZ = centerZ + horizontal * (float) Math.cos(ya);
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ,
                    centerX, centerY, centerZ, 0f, 1f, 0f);
            Matrix.multiplyMM(mvp, 0, projection, 0, view, 0);
            GLES30.glUseProgram(program);
            GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);
            mesh.draw();
        }
    }
}
