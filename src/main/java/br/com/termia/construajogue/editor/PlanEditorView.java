package br.com.termia.construajogue.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;
import br.com.termia.construajogue.util.Ids;

import java.util.List;

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
    public static final int TOOL_CEILING = 6;
    public static final int TOOL_PREFAB = 7;
    public static final int TOOL_OPENING = 8;
    public static final int TOOL_PAINT = 9;

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
    private final Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
    private PrefabInstance selectedPrefab;
    private boolean movedSelection;
    private float grabDx;
    private float grabDz;
    // peça armada para o próximo toque (ferramenta PEÇA)
    private PrefabDefinition activePrefab;
    // vão armado (ferramenta VÃO) e vão selecionado
    private String activeOpeningType;
    private WallOpening selectedOpening;
    private StructureObject selectedOpeningWall;
    // tinta armada (ferramenta PINTAR)
    private float[] activePaint;
    private boolean paintBucket;
    // último toque SEM snap (pintura decide o lado pela posição exata)
    private float rawX;
    private float rawZ;

    public PlanEditorView(Context context, Host host) {
        super(context);
        this.host = host;
        stroke.setStyle(Paint.Style.STROKE);
        textPaint.setColor(0xFFAFC3D0);
        textPaint.setTextSize(28f);
        measurePaint.setColor(0xFFF2E3A0);
        measurePaint.setTextSize(26f);
        measurePaint.setFakeBoldText(true);
        measurePaint.setShadowLayer(4f, 0f, 0f, 0xFF000000);
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
        return selectedStructure != null || selectedPrefab != null
                || selectedOpening != null;
    }

    public StructureObject selectedStructure() {
        return selectedStructure;
    }

    public PrefabInstance selectedPrefab() {
        return selectedPrefab;
    }

    /** Arma a ferramenta PEÇA: o próximo toque solta esta peça. */
    public void setActivePrefab(PrefabDefinition def) {
        activePrefab = def;
        setTool(TOOL_PREFAB);
    }

    public PrefabDefinition activePrefab() {
        return activePrefab;
    }

    /** Arma a ferramenta VÃO (porta/portal/janela sobre parede). */
    public void setActiveOpening(String type) {
        activeOpeningType = type;
        setTool(TOOL_OPENING);
    }

    public WallOpening selectedOpening() {
        return selectedOpening;
    }

    /** Aplica o valor do diálogo ALTURA (sentido depende do papel). */
    public void applySelectedHeight(float value) {
        if (selectedStructure == null && selectedPrefab == null
                && selectedOpening == null) {
            return;
        }
        host.beforeChange();
        if (selectedOpening != null) {
            float wallHeight = selectedOpeningWall.half[1] * 2f;
            selectedOpening.height = Math.max(0.3f, Math.min(
                    wallHeight - selectedOpening.sill, value));
            host.selectionChanged(describeOpening(selectedOpening));
        } else if (selectedStructure != null) {
            StructureRoles.applyHeight(selectedStructure, value);
            host.selectionChanged(
                    StructureRoles.describe(selectedStructure));
        } else {
            // peça: ALTURA = distância do chão (voo do drone, item…)
            selectedPrefab.transform.y =
                    Math.max(0f, Math.min(10f, value));
            host.selectionChanged(describePrefab(selectedPrefab));
        }
        host.afterChange();
        invalidate();
    }

    /** Mutação da seleção com undo + redesenho + status atualizado. */
    public void mutateSelected(Runnable change) {
        if (!hasSelection()) {
            return;
        }
        host.beforeChange();
        change.run();
        host.afterChange();
        if (selectedOpening != null) {
            host.selectionChanged(describeOpening(selectedOpening));
        } else if (selectedStructure != null) {
            host.selectionChanged(
                    StructureRoles.describe(selectedStructure));
        } else if (selectedPrefab != null) {
            host.selectionChanged(describePrefab(selectedPrefab));
        }
        invalidate();
    }

    private static String describeOpening(WallOpening o) {
        String name = WallOpening.WINDOW.equals(o.type) ? "janela"
                : WallOpening.DOOR.equals(o.type) ? "porta" : "portal";
        return String.format("%s  %.2f × %.2f m", name, o.width, o.height)
                + (o.sill > 0f
                ? String.format("  ·  peitoril %.2f m", o.sill) : "");
    }

    private String describePrefab(PrefabInstance p) {
        return p.prefabId + String.format("  ·  %.2f m do chão",
                p.transform.y);
    }

    public void deleteSelected() {
        if (selectedStructure == null && selectedPrefab == null
                && selectedOpening == null) {
            return;
        }
        host.beforeChange();
        if (selectedOpening != null) {
            selectedOpeningWall.openings.remove(selectedOpening);
        } else if (selectedStructure != null) {
            doc.structures.remove(selectedStructure);
        } else {
            doc.prefabs.remove(selectedPrefab);
        }
        clearSelection();
        host.afterChange();
        invalidate();
    }

    private void clearSelection() {
        selectedStructure = null;
        selectedMarker = null;
        selectedPrefab = null;
        selectedOpening = null;
        selectedOpeningWall = null;
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

    /**
     * Snap do ponto tocado com a ferramenta PAREDE: a ponta de uma
     * parede existente tem prioridade; depois o CORPO de uma parede
     * (junção em T — o ponto cai exatamente na linha central dela);
     * por fim a grade.
     */
    private float snapPoint(float wx, float wz, boolean xAxis) {
        if (tool == TOOL_WALL) {
            float[] end = nearWallEnd(wx, wz);
            if (end == null) {
                end = nearWallBody(wx, wz);
            }
            if (end != null) {
                return xAxis ? end[0] : end[1];
            }
        }
        return snap(xAxis ? wx : wz);
    }

    /**
     * Ponto sobre a linha central da parede mais próxima (junção em T):
     * a coordenada perpendicular vem da parede, a longitudinal segue a
     * grade, limitada ao trecho entre as pontas.
     */
    private float[] nearWallBody(float wx, float wz) {
        float best = Math.max(0.4f, 36f / scale);
        float[] hit = null;
        for (StructureObject s : doc.structures) {
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            boolean horizontal = s.half[0] >= s.half[2];
            float len = (horizontal ? s.half[0] : s.half[2]) - WALL_HALF_T;
            if (horizontal) {
                float along = Math.max(s.transform.x - len,
                        Math.min(s.transform.x + len, snap(wx)));
                float dist = (float) Math.hypot(wx - along,
                        wz - s.transform.z);
                if (dist < best) {
                    best = dist;
                    hit = new float[]{along, s.transform.z};
                }
            } else {
                float along = Math.max(s.transform.z - len,
                        Math.min(s.transform.z + len, snap(wz)));
                float dist = (float) Math.hypot(wx - s.transform.x,
                        wz - along);
                if (dist < best) {
                    best = dist;
                    hit = new float[]{s.transform.x, along};
                }
            }
        }
        return hit;
    }

    /** Ponta de parede mais próxima dentro do raio de captura, ou null. */
    private float[] nearWallEnd(float wx, float wz) {
        float best = Math.max(0.4f, 36f / scale);
        float[] hit = null;
        for (StructureObject s : doc.structures) {
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            boolean horizontal = s.half[0] >= s.half[2];
            float len = (horizontal ? s.half[0] : s.half[2]) - WALL_HALF_T;
            for (int side = -1; side <= 1; side += 2) {
                float ex = horizontal
                        ? s.transform.x + side * len : s.transform.x;
                float ez = horizontal
                        ? s.transform.z : s.transform.z + side * len;
                float dist = (float) Math.hypot(wx - ex, wz - ez);
                if (dist < best) {
                    best = dist;
                    hit = new float[]{ex, ez};
                }
            }
        }
        return hit;
    }

    // ---- toque ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                panning = false;
                rawX = toWorldX(e.getX());
                rawZ = toWorldZ(e.getY());
                startX = curX = snapPoint(toWorldX(e.getX()),
                        toWorldZ(e.getY()), true);
                startZ = curZ = snapPoint(toWorldX(e.getX()),
                        toWorldZ(e.getY()), false);
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
                    rawX = toWorldX(e.getX());
                    rawZ = toWorldZ(e.getY());
                    curX = snapPoint(toWorldX(e.getX()),
                            toWorldZ(e.getY()), true);
                    curZ = snapPoint(toWorldX(e.getX()),
                            toWorldZ(e.getY()), false);
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
                addRect(StructureObject.ROLE_FLOOR, -0.15f, 0.15f,
                        new float[]{0.30f, 0.33f, 0.38f});
                break;
            case TOOL_BLOCK:
                addRect(StructureObject.ROLE_BLOCK, 0.5f, 0.5f,
                        new float[]{0.62f, 0.45f, 0.30f});
                break;
            case TOOL_CEILING:
                // placa de 0.3m com a base na altura padrão da parede
                addRect(StructureObject.ROLE_CEILING,
                        WALL_HEIGHT + 0.15f, 0.15f,
                        new float[]{0.38f, 0.41f, 0.48f});
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
            case TOOL_PREFAB:
                placePrefab();
                break;
            case TOOL_OPENING:
                placeOpening();
                break;
            case TOOL_PAINT:
                paintAt(rawX, rawZ);
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

    /** Piso/bloco/teto: retângulo arrastado, altura padrão do papel. */
    private void addRect(String role, float centerY, float halfY,
                         float[] color) {
        float hx = Math.abs(curX - startX) / 2f;
        float hz = Math.abs(curZ - startZ) / 2f;
        if (hx < 0.25f || hz < 0.25f) {
            return;
        }
        host.beforeChange();
        StructureObject s = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        s.role = role;
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
        s.role = StructureObject.ROLE_WALL;
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

    /** Arma a ferramenta PINTAR. `bucket` = paredes ligadas juntas. */
    public void setActivePaint(float[] color, boolean bucket) {
        activePaint = color.clone();
        paintBucket = bucket;
        setTool(TOOL_PAINT);
    }

    /**
     * Pinta o que foi tocado. Parede: pinta o LADO voltado para o dedo
     * (toque no terço do meio pinta os dois). Balde: percorre as paredes
     * conectadas e pinta em cada uma a face voltada para o ponto tocado
     * — tocar dentro do cômodo pinta o interior inteiro, tocar fora
     * pinta o exterior.
     */
    private void paintAt(float wx, float wz) {
        if (activePaint == null) {
            return;
        }
        StructureObject target = structureAt(wx, wz);
        if (target == null) {
            host.selectionChanged("toque numa estrutura para pintar");
            return;
        }
        boolean wall = StructureObject.ROLE_WALL
                .equals(StructureRoles.roleOf(target));
        host.beforeChange();
        if (!wall) {
            target.color = activePaint.clone();
            target.color2 = null;
        } else if (paintBucket) {
            for (StructureObject w : connectedWalls(target)) {
                paintWallSide(w, wx, wz, false);
            }
        } else {
            paintWallSide(target, wx, wz, true);
        }
        host.afterChange();
        invalidate();
    }

    /**
     * Pinta a face da parede voltada para (wx, wz). `allowBoth`: toque
     * no terço central pinta a parede inteira de uma cor só.
     */
    private void paintWallSide(StructureObject s, float wx, float wz,
                               boolean allowBoth) {
        boolean thinX = s.half[0] < s.half[2];
        float d = thinX ? wx - s.transform.x : wz - s.transform.z;
        float half = thinX ? s.half[0] : s.half[2];
        if (allowBoth && Math.abs(d) <= half * 0.34f) {
            s.color = activePaint.clone();
            s.color2 = null;
        } else if (d > 0f) {
            s.color2 = activePaint.clone();
        } else {
            if (s.color2 == null) {
                s.color2 = s.color.clone(); // preserva o outro lado
            }
            s.color = activePaint.clone();
        }
    }

    /** Paredes conectadas à inicial (encostadas em XZ), via varredura. */
    private List<StructureObject> connectedWalls(StructureObject start) {
        List<StructureObject> found = new java.util.ArrayList<>();
        found.add(start);
        for (int i = 0; i < found.size(); i++) {
            StructureObject a = found.get(i);
            for (StructureObject s : doc.structures) {
                if (found.contains(s) || !StructureObject.ROLE_WALL
                        .equals(StructureRoles.roleOf(s))) {
                    continue;
                }
                if (Math.abs(a.transform.x - s.transform.x)
                        <= a.half[0] + s.half[0] + 0.05f
                        && Math.abs(a.transform.z - s.transform.z)
                        <= a.half[2] + s.half[2] + 0.05f) {
                    found.add(s);
                }
            }
        }
        return found;
    }

    /** Estrutura sob o ponto (teto por último, como na seleção). */
    private StructureObject structureAt(float wx, float wz) {
        for (int pass = 0; pass < 2; pass++) {
            boolean wantCeiling = pass == 1;
            for (int i = doc.structures.size() - 1; i >= 0; i--) {
                StructureObject s = doc.structures.get(i);
                if (StructureRoles.isCeiling(s) != wantCeiling) {
                    continue;
                }
                if (Math.abs(wx - s.transform.x) <= s.half[0]
                        && Math.abs(wz - s.transform.z) <= s.half[2]) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Recorta o vão armado na parede tocada. */
    private void placeOpening() {
        if (activeOpeningType == null) {
            return;
        }
        StructureObject wall = wallAt(curX, curZ);
        if (wall == null) {
            host.selectionChanged("toque em cima de uma parede");
            return;
        }
        boolean window = WallOpening.WINDOW.equals(activeOpeningType);
        boolean portal = WallOpening.PORTAL.equals(activeOpeningType);
        WallOpening o = new WallOpening(Ids.create(), activeOpeningType);
        o.width = window ? 1.2f : portal ? 1.6f : 1.0f;
        o.sill = window ? 0.9f : 0f;
        float wallHeight = wall.half[1] * 2f;
        o.height = portal ? wallHeight
                : Math.min(window ? 1.2f : 2.1f, wallHeight - o.sill);
        boolean alongX = wall.half[0] >= wall.half[2];
        float along = alongX ? curX : curZ;
        float centerAlong = alongX ? wall.transform.x : wall.transform.z;
        float halfLen = Math.max(wall.half[0], wall.half[2]);
        o.offset = Math.max(-halfLen + o.width / 2f,
                Math.min(halfLen - o.width / 2f, along - centerAlong));
        host.beforeChange();
        wall.openings.add(o);
        host.afterChange();
        host.selectionChanged(describeOpening(o));
    }

    /** Parede cujo corpo está sob (ou bem perto de) o ponto tocado. */
    private StructureObject wallAt(float wx, float wz) {
        float slack = Math.max(0.3f, 24f / scale);
        StructureObject best = null;
        float bestDist = Float.MAX_VALUE;
        for (StructureObject s : doc.structures) {
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            float dx = Math.max(0f,
                    Math.abs(wx - s.transform.x) - s.half[0]);
            float dz = Math.max(0f,
                    Math.abs(wz - s.transform.z) - s.half[2]);
            float dist = (float) Math.hypot(dx, dz);
            if (dist <= slack && dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    /** Solta a peça armada onde o dedo terminou, com altura típica. */
    private void placePrefab() {
        if (activePrefab == null) {
            return;
        }
        // tocar numa peça existente seleciona (não empilha outra em cima)
        for (int i = doc.prefabs.size() - 1; i >= 0; i--) {
            PrefabInstance existing = doc.prefabs.get(i);
            if (Math.hypot(curX - existing.transform.x,
                    curZ - existing.transform.z) < 24f / scale) {
                selectedPrefab = existing;
                host.selectionChanged(describePrefab(existing));
                invalidate();
                return;
            }
        }
        host.beforeChange();
        PrefabInstance p = new PrefabInstance(Ids.create(),
                activePrefab.id);
        p.transform.x = curX;
        p.transform.z = curZ;
        p.transform.y = defaultY(activePrefab);
        if (PrefabDefinition.BEHAVIOR_DOOR.equals(activePrefab.behavior)) {
            p.properties.put("halfX", 1.5f);
            p.properties.put("halfY", 1.4f);
            p.properties.put("halfZ", 0.4f);
        }
        doc.prefabs.add(p);
        autoLinkDoor();
        host.afterChange();
    }

    /** Alturas típicas da campanha original (base do chão em y=0). */
    private static float defaultY(PrefabDefinition def) {
        switch (def.behavior) {
            case PrefabDefinition.BEHAVIOR_DRONE:
            case PrefabDefinition.BEHAVIOR_DRONE_DORMANT:
                return 1.8f;
            case PrefabDefinition.BEHAVIOR_MUTANT:
                return 0.85f;
            case PrefabDefinition.BEHAVIOR_TERMINAL:
            case PrefabDefinition.BEHAVIOR_DOOR:
                return 1.4f;
            case PrefabDefinition.BEHAVIOR_STATIC:
                // pendurada: nasce na altura padrão do teto
                return "prop.lamp.ceiling".equals(def.id) ? 3.0f : 0f;
            default:
                return 0.5f; // itens balançando perto do chão
        }
    }

    /** Uma porta e um terminal no mapa: liga automaticamente. */
    private void autoLinkDoor() {
        PrefabInstance door = null;
        PrefabInstance terminal = null;
        for (PrefabInstance p : doc.prefabs) {
            if (p.prefabId.startsWith("door.")) {
                door = p;
            } else if (p.prefabId.startsWith("terminal.")) {
                terminal = p;
            }
        }
        if (door != null && terminal != null) {
            door.properties.put("controllerId", terminal.id);
        }
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
        selectedPrefab = null;
        selectedOpening = null;
        selectedOpeningWall = null;
        // peças primeiro: ícones pequenos por cima das estruturas
        for (int i = doc.prefabs.size() - 1; i >= 0; i--) {
            PrefabInstance p = doc.prefabs.get(i);
            if (Math.hypot(wx - p.transform.x, wz - p.transform.z)
                    < 24f / scale) {
                selectedPrefab = p;
                grabDx = p.transform.x - wx;
                grabDz = p.transform.z - wz;
                host.selectionChanged(describePrefab(p));
                return;
            }
        }
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
        // vãos antes das paredes: são menores e moram em cima delas
        for (StructureObject s : doc.structures) {
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            boolean alongX = s.half[0] >= s.half[2];
            for (WallOpening o : s.openings) {
                float ox = alongX ? s.transform.x + o.offset : s.transform.x;
                float oz = alongX ? s.transform.z : s.transform.z + o.offset;
                if (Math.hypot(wx - ox, wz - oz)
                        < Math.max(o.width / 2f, 20f / scale)) {
                    selectedOpening = o;
                    selectedOpeningWall = s;
                    host.selectionChanged(describeOpening(o));
                    return;
                }
            }
        }
        // teto por último: translúcido, não pode roubar todo toque
        for (int pass = 0; pass < 2; pass++) {
            boolean wantCeiling = pass == 1;
            for (int i = doc.structures.size() - 1; i >= 0; i--) {
                StructureObject s = doc.structures.get(i);
                if (StructureRoles.isCeiling(s) != wantCeiling) {
                    continue;
                }
                if (Math.abs(wx - s.transform.x) <= s.half[0]
                        && Math.abs(wz - s.transform.z) <= s.half[2]) {
                    selectedStructure = s;
                    grabDx = s.transform.x - wx;
                    grabDz = s.transform.z - wz;
                    host.selectionChanged(StructureRoles.describe(s));
                    return;
                }
            }
        }
        host.selectionChanged(null);
    }

    private void dragSelection() {
        if (selectedStructure == null && selectedMarker == null
                && selectedPrefab == null && selectedOpening == null) {
            return;
        }
        if (!movedSelection) {
            host.beforeChange();
            movedSelection = true;
        }
        if (selectedOpening != null) {
            // vão desliza pela própria parede
            StructureObject wall = selectedOpeningWall;
            boolean alongX = wall.half[0] >= wall.half[2];
            float along = snap(alongX ? curX : curZ);
            float centerAlong = alongX
                    ? wall.transform.x : wall.transform.z;
            float halfLen = Math.max(wall.half[0], wall.half[2]);
            float w2 = selectedOpening.width / 2f;
            selectedOpening.offset = Math.max(-halfLen + w2,
                    Math.min(halfLen - w2, along - centerAlong));
            return;
        }
        float nx = snap(curX + grabDx);
        float nz = snap(curZ + grabDz);
        if (selectedStructure != null) {
            selectedStructure.transform.x = nx;
            selectedStructure.transform.z = nz;
        } else if (selectedPrefab != null) {
            selectedPrefab.transform.x = nx;
            selectedPrefab.transform.z = nz;
        } else {
            selectedMarker.x = nx;
            selectedMarker.z = nz;
        }
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
            if (!StructureRoles.isCeiling(s)) {
                drawStructure(canvas, s, s == selectedStructure, false);
            }
        }
        for (StructureObject s : doc.structures) {
            drawOpenings(canvas, s);
        }
        // tetos por cima de tudo, translúcidos
        for (StructureObject s : doc.structures) {
            if (StructureRoles.isCeiling(s)) {
                drawStructure(canvas, s, s == selectedStructure, true);
            }
        }
        if (tool == TOOL_WALL) {
            drawWallAnchors(canvas);
        }
        if (dragging && tool != TOOL_SELECT) {
            drawPreview(canvas);
        }
        for (PrefabInstance p : doc.prefabs) {
            drawPrefab(canvas, p, p == selectedPrefab);
        }
        for (LogicMarker m : doc.markers) {
            drawMarker(canvas, m, m == selectedMarker);
        }
        drawMeasureLabels(canvas);
    }

    /** Vãos sobre a parede: porta marrom, portal escuro, janela azul. */
    private void drawOpenings(Canvas canvas, StructureObject s) {
        if (s.openings.isEmpty()) {
            return;
        }
        boolean alongX = s.half[0] >= s.half[2];
        for (WallOpening o : s.openings) {
            float cx = alongX ? s.transform.x + o.offset : s.transform.x;
            float cz = alongX ? s.transform.z : s.transform.z + o.offset;
            float hx = alongX ? o.width / 2f : s.half[0];
            float hz = alongX ? s.half[2] : o.width / 2f;
            fill.setColor(WallOpening.WINDOW.equals(o.type) ? 0xFF4A90C2
                    : WallOpening.DOOR.equals(o.type) ? 0xFF8A6238
                    : 0xFF10151B);
            canvas.drawRect(toPxX(cx - hx), toPxY(cz - hz),
                    toPxX(cx + hx), toPxY(cz + hz), fill);
            if (o == selectedOpening) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(3f);
                canvas.drawRect(toPxX(cx - hx), toPxY(cz - hz),
                        toPxX(cx + hx), toPxY(cz + hz), stroke);
            }
        }
    }

    /** Ícone da peça: bolinha colorida com letra (porta vira barra). */
    private void drawPrefab(Canvas canvas, PrefabInstance p,
                            boolean selected) {
        float px = toPxX(p.transform.x);
        float py = toPxY(p.transform.z);
        String letter;
        int color;
        float[] footprint = PrefabMeshFactory.footprint(p.prefabId);
        if (footprint != null) {
            // móvel/objeto: pegada real no plano + ponto central
            float hx = footprint[0];
            float hz = footprint[1];
            fill.setColor(0x8858728A);
            canvas.drawRect(toPxX(p.transform.x - hx),
                    toPxY(p.transform.z - hz),
                    toPxX(p.transform.x + hx),
                    toPxY(p.transform.z + hz), fill);
            stroke.setColor(p.prefabId.startsWith("prop.lamp")
                    ? 0xFFF2E3A0 : 0xFF8FA9C9);
            stroke.setStrokeWidth(2f);
            canvas.drawRect(toPxX(p.transform.x - hx),
                    toPxY(p.transform.z - hz),
                    toPxX(p.transform.x + hx),
                    toPxY(p.transform.z + hz), stroke);
            if (selected) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(3f);
                canvas.drawCircle(px, py, 18f, stroke);
            }
            return;
        }
        if (p.prefabId.startsWith("enemy.mutant")) {
            letter = "M";
            color = 0xFFB05CC9;
        } else if (p.prefabId.startsWith("enemy.drone.wave")) {
            letter = "Z";
            color = 0xFF8A8F9C;
        } else if (p.prefabId.startsWith("enemy.")) {
            letter = "D";
            color = 0xFFE05555;
        } else if (p.prefabId.startsWith("pickup.health")) {
            letter = "+";
            color = 0xFF39B54A;
        } else if (p.prefabId.startsWith("pickup.")) {
            letter = "A";
            color = 0xFFD9B23C;
        } else if (p.prefabId.startsWith("terminal.")) {
            letter = "T";
            color = 0xFFE08030;
        } else if (p.prefabId.startsWith("door.")) {
            letter = "";
            color = 0xFF7580A0;
            float hx = Math.max(0.5f, p.floatProperty("halfX", 1.5f));
            float hz = Math.max(0.2f, p.floatProperty("halfZ", 0.4f));
            fill.setColor(color);
            canvas.drawRect(toPxX(p.transform.x - hx),
                    toPxY(p.transform.z - hz),
                    toPxX(p.transform.x + hx),
                    toPxY(p.transform.z + hz), fill);
        } else {
            letter = "?";
            color = 0xFF888888;
        }
        if (!letter.isEmpty()) {
            fill.setColor(color);
            canvas.drawCircle(px, py, 13f, fill);
            textPaint.setColor(0xFF10151B);
            canvas.drawText(letter, px - 8f, py + 9f, textPaint);
            textPaint.setColor(0xFFAFC3D0);
        }
        if (selected) {
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(3f);
            canvas.drawCircle(px, py, 18f, stroke);
        }
    }

    /** Pontas de parede onde o toque gruda com a ferramenta PAREDE. */
    private void drawWallAnchors(Canvas canvas) {
        stroke.setColor(0xFFE0C060);
        stroke.setStrokeWidth(2.5f);
        for (StructureObject s : doc.structures) {
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            boolean horizontal = s.half[0] >= s.half[2];
            float len = (horizontal ? s.half[0] : s.half[2]) - WALL_HALF_T;
            for (int side = -1; side <= 1; side += 2) {
                float ex = horizontal
                        ? s.transform.x + side * len : s.transform.x;
                float ez = horizontal
                        ? s.transform.z : s.transform.z + side * len;
                canvas.drawCircle(toPxX(ex), toPxY(ez), 9f, stroke);
            }
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
                               boolean selected, boolean translucent) {
        float l = toPxX(s.transform.x - s.half[0]);
        float t = toPxY(s.transform.z - s.half[2]);
        float r = toPxX(s.transform.x + s.half[0]);
        float b = toPxY(s.transform.z + s.half[2]);
        fill.setColor(Color.argb(translucent ? 70 : 255,
                (int) (s.color[0] * 255f), (int) (s.color[1] * 255f),
                (int) (s.color[2] * 255f)));
        canvas.drawRect(l, t, r, b, fill);
        if (s.color2 != null) {
            // metade do lado positivo do eixo fino mostra a outra cor
            fill.setColor(Color.argb(translucent ? 70 : 255,
                    (int) (s.color2[0] * 255f),
                    (int) (s.color2[1] * 255f),
                    (int) (s.color2[2] * 255f)));
            boolean thinX = s.half[0] < s.half[2];
            if (thinX) {
                canvas.drawRect(toPxX(s.transform.x), t, r, b, fill);
            } else {
                canvas.drawRect(l, toPxY(s.transform.z), r, b, fill);
            }
        }
        stroke.setColor(selected ? 0xFFFFFFFF
                : translucent ? 0x998FA9C9 : 0x66000000);
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
        if (tool == TOOL_SPAWN || tool == TOOL_EXIT
                || tool == TOOL_PREFAB || tool == TOOL_OPENING) {
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
            float len = horizontal ? Math.abs(curX - startX)
                    : Math.abs(curZ - startZ);
            canvas.drawText(meters(len),
                    toPxX((startX + ex) / 2f) + 12f,
                    toPxY((startZ + ez) / 2f) - 12f, measurePaint);
            return;
        }
        canvas.drawRect(toPxX(Math.min(startX, curX)),
                toPxY(Math.min(startZ, curZ)),
                toPxX(Math.max(startX, curX)),
                toPxY(Math.max(startZ, curZ)), stroke);
        // cota ao vivo do retângulo (medida interna do cômodo)
        canvas.drawText(meters(Math.abs(curX - startX)) + " × "
                        + meters(Math.abs(curZ - startZ)),
                toPxX((startX + curX) / 2f) - 60f,
                toPxY((startZ + curZ) / 2f), measurePaint);
    }

    private static String meters(float value) {
        return String.format("%.2f m", value).replace('.', ',');
    }

    /**
     * Cotas permanentes: comprimento das paredes e L×P das demais
     * estruturas, quando o zoom dá espaço (ou quando selecionadas).
     */
    private void drawMeasureLabels(Canvas canvas) {
        for (StructureObject s : doc.structures) {
            boolean selected = s == selectedStructure;
            if (StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                float len = Math.max(s.half[0], s.half[2]) * 2f;
                if (!selected && (scale < 16f || len * scale < 110f)) {
                    continue;
                }
                boolean alongX = s.half[0] >= s.half[2];
                canvas.drawText(meters(len),
                        toPxX(s.transform.x) - 34f,
                        toPxY(s.transform.z) + (alongX ? -14f : 0f),
                        measurePaint);
            } else {
                if (!selected && (scale < 16f
                        || s.half[0] * 2f * scale < 130f
                        || s.half[2] * 2f * scale < 60f)) {
                    continue;
                }
                canvas.drawText(meters(s.half[0] * 2f) + " × "
                                + meters(s.half[2] * 2f),
                        toPxX(s.transform.x) - 58f,
                        toPxY(s.transform.z) + 8f, measurePaint);
            }
        }
        if (selectedOpening != null) {
            StructureObject wall = selectedOpeningWall;
            boolean alongX = wall.half[0] >= wall.half[2];
            float ox = alongX ? wall.transform.x + selectedOpening.offset
                    : wall.transform.x;
            float oz = alongX ? wall.transform.z
                    : wall.transform.z + selectedOpening.offset;
            canvas.drawText(meters(selectedOpening.width) + " × "
                            + meters(selectedOpening.height),
                    toPxX(ox) - 50f, toPxY(oz) - 20f, measurePaint);
        }
    }
}
