package br.com.termia.construajogue.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.util.Ids;

/**
 * Planta 2D (vista de topo, X→direita, Z→baixo) que edita o MapDocument
 * direto. Um dedo aplica a ferramenta; dois dedos fazem pan/zoom e
 * cancelam o gesto. Snap de 0.25m, área útil de ±32m (PLANO §9).
 */
public final class PlanEditorView extends View {

    public static final int TOOL_SELECT = 0;
    public static final int TOOL_FLOOR = 1;
    public static final int TOOL_WALL = 2;
    public static final int TOOL_BLOCK = 3;
    public static final int TOOL_SPAWN = 4;
    public static final int TOOL_EXIT = 5;

    /** O host guarda snapshots p/ undo e reage a mudanças/seleção. */
    public interface Host {
        void beforeChange();

        void afterChange();

        void selectionChanged(String description);
    }

    private static final float SNAP = 0.25f;
    private static final float AREA = 32f;
    private static final float WALL_HALF_T = 0.15f;
    private static final float WALL_HEIGHT = 3f;

    private final Host host;
    private MapDocument doc;
    private int tool = TOOL_FLOOR;

    private float scale = 30f;
    private float camX;
    private float camZ;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // gesto atual (1 dedo)
    private boolean dragging;
    private float startX;
    private float startZ;
    private float curX;
    private float curZ;
    // pan/zoom (2 dedos)
    private boolean panning;
    private float lastMidX;
    private float lastMidY;
    private float lastSpan;
    // seleção
    private StructureObject selectedStructure;
    private LogicMarker selectedMarker;
    private boolean movedSelection;
    private float grabDx;
    private float grabDz;

    public PlanEditorView(Context context, Host host) {
        super(context);
        this.host = host;
        stroke.setStyle(Paint.Style.STROKE);
        textPaint.setColor(0xFFAFC3D0);
        textPaint.setTextSize(28f);
        setBackgroundColor(0xFF10151B);
    }

    public void setDocument(MapDocument doc) {
        this.doc = doc;
        clearSelection();
        invalidate();
    }

    public void setTool(int tool) {
        this.tool = tool;
        dragging = false;
        if (tool != TOOL_SELECT) {
            clearSelection();
        }
        invalidate();
    }

    public int tool() {
        return tool;
    }

    public boolean hasSelection() {
        return selectedStructure != null;
    }

    public void deleteSelected() {
        if (selectedStructure == null) {
            return;
        }
        host.beforeChange();
        doc.structures.remove(selectedStructure);
        clearSelection();
        host.afterChange();
        invalidate();
    }

    private void clearSelection() {
        selectedStructure = null;
        selectedMarker = null;
        host.selectionChanged(null);
    }

    // ---- coordenadas ----

    private float toWorldX(float px) {
        return (px - getWidth() / 2f) / scale + camX;
    }

    private float toWorldZ(float py) {
        return (py - getHeight() / 2f) / scale + camZ;
    }

    private float toPxX(float wx) {
        return (wx - camX) * scale + getWidth() / 2f;
    }

    private float toPxY(float wz) {
        return (wz - camZ) * scale + getHeight() / 2f;
    }

    private static float snap(float v) {
        return Math.max(-AREA, Math.min(AREA,
                Math.round(v / SNAP) * SNAP));
    }

    // ---- toque ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                panning = false;
                startX = curX = snap(toWorldX(e.getX()));
                startZ = curZ = snap(toWorldZ(e.getY()));
                if (tool == TOOL_SELECT) {
                    pick(toWorldX(e.getX()), toWorldZ(e.getY()));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = false;
                panning = true;
                lastMidX = mid(e, true);
                lastMidY = mid(e, false);
                lastSpan = span(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (panning && e.getPointerCount() >= 2) {
                    float mx = mid(e, true);
                    float my = mid(e, false);
                    float sp = span(e);
                    camX -= (mx - lastMidX) / scale;
                    camZ -= (my - lastMidY) / scale;
                    if (lastSpan > 10f && sp > 10f) {
                        scale = Math.max(6f, Math.min(240f,
                                scale * sp / lastSpan));
                    }
                    lastMidX = mx;
                    lastMidY = my;
                    lastSpan = sp;
                    invalidate();
                } else if (dragging) {
                    curX = snap(toWorldX(e.getX()));
                    curZ = snap(toWorldZ(e.getY()));
                    if (tool == TOOL_SELECT) {
                        dragSelection();
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerCount() <= 2) {
                    panning = false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    finishGesture();
                    invalidate();
                }
                panning = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                panning = false;
                invalidate();
                return true;
            default:
                return false;
        }
    }

    private static float mid(MotionEvent e, boolean x) {
        return x ? (e.getX(0) + e.getX(1)) / 2f
                : (e.getY(0) + e.getY(1)) / 2f;
    }

    private static float span(MotionEvent e) {
        return (float) Math.hypot(e.getX(1) - e.getX(0),
                e.getY(1) - e.getY(0));
    }

    private void finishGesture() {
        switch (tool) {
            case TOOL_FLOOR:
                addRect(-0.15f, 0.15f, new float[]{0.30f, 0.33f, 0.38f});
                break;
            case TOOL_BLOCK:
                addRect(0.5f, 0.5f, new float[]{0.62f, 0.45f, 0.30f});
                break;
            case TOOL_WALL:
                addWall();
                break;
            case TOOL_SPAWN:
                placeMarker(LogicMarker.PLAYER_SPAWN, 0f);
                break;
            case TOOL_EXIT:
                placeMarker(LogicMarker.EXIT, 1.2f);
                break;
            case TOOL_SELECT:
                if (movedSelection) {
                    host.afterChange();
                    movedSelection = false;
                }
                break;
            default:
                break;
        }
    }

    /** Piso e bloco: retângulo arrastado no plano, altura fixa do papel. */
    private void addRect(float centerY, float halfY, float[] color) {
        float hx = Math.abs(curX - startX) / 2f;
        float hz = Math.abs(curZ - startZ) / 2f;
        if (hx < 0.25f || hz < 0.25f) {
            return;
        }
        host.beforeChange();
        StructureObject s = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        s.transform.x = (startX + curX) / 2f;
        s.transform.y = centerY;
        s.transform.z = (startZ + curZ) / 2f;
        s.half = new float[]{hx, halfY, hz};
        s.color = color.clone();
        doc.structures.add(s);
        host.afterChange();
    }

    /** Parede reta alinhada ao eixo dominante do arrasto, 3m de altura. */
    private void addWall() {
        float dx = Math.abs(curX - startX);
        float dz = Math.abs(curZ - startZ);
        if (Math.max(dx, dz) < 0.5f) {
            return;
        }
        host.beforeChange();
        StructureObject s = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        boolean horizontal = dx >= dz;
        // meia espessura extra nas pontas fecha os cantos entre paredes
        float half = (horizontal ? dx : dz) / 2f + WALL_HALF_T;
        s.transform.x = horizontal ? (startX + curX) / 2f : startX;
        s.transform.y = WALL_HEIGHT / 2f;
        s.transform.z = horizontal ? startZ : (startZ + curZ) / 2f;
        s.half = horizontal
                ? new float[]{half, WALL_HEIGHT / 2f, WALL_HALF_T}
                : new float[]{WALL_HALF_T, WALL_HEIGHT / 2f, half};
        s.color = new float[]{0.46f, 0.48f, 0.55f};
        doc.structures.add(s);
        host.afterChange();
    }

    /** Início e saída são únicos: reposiciona se já existir. */
    private void placeMarker(String type, float radius) {
        host.beforeChange();
        LogicMarker marker = doc.firstMarker(type);
        if (marker == null) {
            marker = new LogicMarker(Ids.create(), type);
            marker.radius = radius;
            doc.markers.add(marker);
        }
        marker.x = curX;
        marker.z = curZ;
        marker.y = 0f;
        host.afterChange();
    }

    private void pick(float wx, float wz) {
        movedSelection = false;
        selectedMarker = null;
        selectedStructure = null;
        for (LogicMarker m : doc.markers) {
            if (Math.hypot(wx - m.x, wz - m.z) < 24f / scale) {
                selectedMarker = m;
                grabDx = m.x - wx;
                grabDz = m.z - wz;
                host.selectionChanged(LogicMarker.PLAYER_SPAWN
                        .equals(m.type) ? "início" : "saída");
                return;
            }
        }
        for (int i = doc.structures.size() - 1; i >= 0; i--) {
            StructureObject s = doc.structures.get(i);
            if (Math.abs(wx - s.transform.x) <= s.half[0]
                    && Math.abs(wz - s.transform.z) <= s.half[2]) {
                selectedStructure = s;
                grabDx = s.transform.x - wx;
                grabDz = s.transform.z - wz;
                host.selectionChanged(String.format("%s  %.2f × %.2f m",
                        roleName(s), s.half[0] * 2f, s.half[2] * 2f));
                return;
            }
        }
        host.selectionChanged(null);
    }

    private void dragSelection() {
        if (selectedStructure == null && selectedMarker == null) {
            return;
        }
        if (!movedSelection) {
            host.beforeChange();
            movedSelection = true;
        }
        float nx = snap(curX + grabDx);
        float nz = snap(curZ + grabDz);
        if (selectedStructure != null) {
            selectedStructure.transform.x = nx;
            selectedStructure.transform.z = nz;
        } else {
            selectedMarker.x = nx;
            selectedMarker.z = nz;
        }
    }

    private static String roleName(StructureObject s) {
        if (s.half[1] <= 0.2f) {
            return "piso";
        }
        return s.transform.y - s.half[1] <= 0.01f
                && s.half[1] >= 1f ? "parede" : "bloco";
    }

    // ---- desenho ----

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (doc == null) {
            return;
        }
        drawGrid(canvas);
        for (StructureObject s : doc.structures) {
            drawStructure(canvas, s, s == selectedStructure);
        }
        if (dragging && tool != TOOL_SELECT) {
            drawPreview(canvas);
        }
        for (LogicMarker m : doc.markers) {
            drawMarker(canvas, m, m == selectedMarker);
        }
    }

    private void drawGrid(Canvas canvas) {
        float step = scale < 14f ? 4f : 1f;
        gridPaint.setColor(0xFF1D2630);
        float x0 = Math.max(-AREA, toWorldX(0));
        float x1 = Math.min(AREA, toWorldX(getWidth()));
        float z0 = Math.max(-AREA, toWorldZ(0));
        float z1 = Math.min(AREA, toWorldZ(getHeight()));
        for (float x = (float) Math.ceil(x0 / step) * step; x <= x1;
                x += step) {
            canvas.drawLine(toPxX(x), toPxY(z0), toPxX(x), toPxY(z1),
                    gridPaint);
        }
        for (float z = (float) Math.ceil(z0 / step) * step; z <= z1;
                z += step) {
            canvas.drawLine(toPxX(x0), toPxY(z), toPxX(x1), toPxY(z),
                    gridPaint);
        }
        gridPaint.setColor(0xFF2C3A48);
        canvas.drawLine(toPxX(0), toPxY(z0), toPxX(0), toPxY(z1), gridPaint);
        canvas.drawLine(toPxX(x0), toPxY(0), toPxX(x1), toPxY(0), gridPaint);
        // borda da área útil
        stroke.setColor(0xFF33475A);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(toPxX(-AREA), toPxY(-AREA), toPxX(AREA),
                toPxY(AREA), stroke);
    }

    private void drawStructure(Canvas canvas, StructureObject s,
                               boolean selected) {
        float l = toPxX(s.transform.x - s.half[0]);
        float t = toPxY(s.transform.z - s.half[2]);
        float r = toPxX(s.transform.x + s.half[0]);
        float b = toPxY(s.transform.z + s.half[2]);
        fill.setColor(Color.rgb((int) (s.color[0] * 255f),
                (int) (s.color[1] * 255f), (int) (s.color[2] * 255f)));
        canvas.drawRect(l, t, r, b, fill);
        stroke.setColor(selected ? 0xFFFFFFFF : 0x66000000);
        stroke.setStrokeWidth(selected ? 4f : 1.5f);
        canvas.drawRect(l, t, r, b, stroke);
    }

    private void drawMarker(Canvas canvas, LogicMarker m, boolean selected) {
        boolean spawn = LogicMarker.PLAYER_SPAWN.equals(m.type);
        float px = toPxX(m.x);
        float py = toPxY(m.z);
        fill.setColor(spawn ? 0xFF39B54A : 0xFF2E9BD6);
        canvas.drawCircle(px, py, 14f, fill);
        if (!spawn && m.radius > 0f) {
            stroke.setColor(0x882E9BD6);
            stroke.setStrokeWidth(2f);
            canvas.drawCircle(px, py, m.radius * scale, stroke);
        }
        if (selected) {
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(3f);
            canvas.drawCircle(px, py, 18f, stroke);
        }
        canvas.drawText(spawn ? "INÍCIO" : "SAÍDA", px + 18f, py - 12f,
                textPaint);
    }

    private void drawPreview(Canvas canvas) {
        stroke.setColor(0xFFE0C060);
        stroke.setStrokeWidth(3f);
        if (tool == TOOL_SPAWN || tool == TOOL_EXIT) {
            canvas.drawCircle(toPxX(curX), toPxY(curZ), 14f, stroke);
            return;
        }
        if (tool == TOOL_WALL) {
            boolean horizontal = Math.abs(curX - startX)
                    >= Math.abs(curZ - startZ);
            float ex = horizontal ? curX : startX;
            float ez = horizontal ? startZ : curZ;
            canvas.drawLine(toPxX(startX), toPxY(startZ), toPxX(ex),
                    toPxY(ez), stroke);
            return;
        }
        canvas.drawRect(toPxX(Math.min(startX, curX)),
                toPxY(Math.min(startZ, curZ)),
                toPxX(Math.max(startX, curX)),
                toPxY(Math.max(startZ, curZ)), stroke);
    }
}
