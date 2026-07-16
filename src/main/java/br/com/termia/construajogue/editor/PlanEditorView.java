package br.com.termia.construajogue.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import br.com.termia.construajogue.editor.tools.GroupSelection;
import br.com.termia.construajogue.editor.tools.OpeningTool;
import br.com.termia.construajogue.editor.tools.PaintTool;
import br.com.termia.construajogue.editor.tools.PrefabPlacementTool;
import br.com.termia.construajogue.editor.tools.StoryLevels;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapCopies;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;
import br.com.termia.construajogue.util.Ids;

import java.util.List;

/**
 * Planta 2D (vista de topo, X→direita, Z→baixo) que edita o MapDocument
 * direto. Um dedo aplica a ferramenta; dois dedos fazem pan/zoom e
 * cancelam o gesto. Snap de 0.25m, área útil de ±48m.
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
    public static final int TOOL_POINTS = 10;
    public static final int TOOL_GROUP = 11;

    /** O host guarda snapshots p/ undo e reage a mudanças/seleção. */
    public interface Host {
        void beforeChange();

        void afterChange();

        void selectionChanged(String description);

        /** O usuário trocou o pavimento visível/ativo. */
        void storyChanged(float baseY);

        /** Contorno fechado; o host decide (piso só / piso+paredes). */
        void polygonClosed(boolean floorRole);
    }

    private static final float SNAP = 0.25f;
    static final float AREA = 48f;
    static final float WALL_HALF_T = 0.15f;
    private static final float WALL_HEIGHT = 3f;

    private final Host host;
    private final PlanRenderer renderer = new PlanRenderer(this);
    MapDocument doc;
    int tool = TOOL_FLOOR;
    /** Altura do piso em que novas estruturas e peças são criadas. */
    float activeBaseY;
    private List<Float> storyLevels = new java.util.ArrayList<>();

    float scale = 30f;
    private float camX;
    private float camZ;


    // gesto atual (1 dedo)
    boolean dragging;
    float startX;
    float startZ;
    float curX;
    float curZ;
    // pan/zoom (2 dedos)
    private boolean panning;
    private float lastMidX;
    private float lastMidY;
    private float lastSpan;
    // seleção
    StructureObject selectedStructure;
    LogicMarker selectedMarker;
    PrefabInstance selectedPrefab;
    private boolean movedSelection;
    private float grabDx;
    private float grabDz;
    // peça armada para o próximo toque (ferramenta PEÇA)
    private PrefabDefinition activePrefab;
    // vão armado (ferramenta VÃO) e vão selecionado
    private String activeOpeningType;
    WallOpening selectedOpening;
    StructureObject selectedOpeningWall;
    // tinta armada (ferramenta PINTAR)
    private float[] activePaint;
    private boolean paintBucket;
    // próximo toque define a patrulha do inimigo selecionado
    private boolean routeMode;
    // aresta da estrutura selecionada sendo puxada (-1 nenhuma;
    // 0=minX, 1=maxX, 2=minZ, 3=maxZ)
    private int dragEdge = -1;
    // contorno por pontos (ferramenta DESENHO) e vértice arrastado
    final List<float[]> contour = new java.util.ArrayList<>();
    private String contourRole;
    private int dragPoint = -1;
    // último toque SEM snap (pintura decide o lado pela posição exata)
    private float rawX;
    private float rawZ;
    // seleção retangular/movimento de cômodo (regras em editor/tools)
    final GroupSelection group = new GroupSelection();
    boolean groupMoving;
    private float groupLastX;
    private float groupLastZ;

    public PlanEditorView(Context context, Host host) {
        super(context);
        this.host = host;
        setBackgroundColor(0xFF10151B);
    }

    public void setDocument(MapDocument doc) {
        this.doc = doc;
        storyLevels = StoryLevels.discover(doc);
        boolean found = false;
        for (float level : storyLevels) {
            if (Math.abs(level - activeBaseY) <= 0.08f) {
                found = true;
                break;
            }
        }
        if (!found) activeBaseY = 0f;
        clearSelection();
        invalidate();
    }

    public float activeBaseY() {
        return activeBaseY;
    }

    public List<Float> storyBases() {
        return new java.util.ArrayList<>(storyLevels);
    }

    /** Chamado pelo host depois de qualquer mutação do documento. */
    public void documentChanged() {
        storyLevels = StoryLevels.discover(doc);
        invalidate();
    }

    /** Troca o andar editado; objetos de outros pavimentos ficam ocultos. */
    public void setActiveBaseY(float value) {
        switchStory(value, true);
    }

    private void switchStory(float value, boolean clear) {
        activeBaseY = StoryLevels.normalize(Math.max(-20f,
                Math.min(100f, value)));
        if (clear) clearSelection();
        host.storyChanged(activeBaseY);
        invalidate();
    }

    /** Topo da laje selecionada, pronto para receber o andar seguinte. */
    public Float selectedCeilingTop() {
        return selectedStructure != null
                && StructureRoles.isCeiling(selectedStructure)
                ? StoryLevels.normalize(StoryLevels.top(selectedStructure))
                : null;
    }

    boolean visible(StructureObject value) {
        return StoryLevels.belongs(value, activeBaseY, storyLevels);
    }

    boolean visible(PrefabInstance value) {
        return StoryLevels.belongs(value, activeBaseY, storyLevels);
    }

    boolean visible(LogicMarker value) {
        return StoryLevels.belongs(value, activeBaseY);
    }

    public void setTool(int tool) {
        int previousTool = this.tool;
        this.tool = tool;
        dragging = false;
        routeMode = false;
        if (tool != TOOL_POINTS) {
            contour.clear();
        }
        if (tool == TOOL_SELECT) {
            clearSelection();
        } else if (tool != TOOL_GROUP) {
            clearSelection();
        } else if (tool == TOOL_GROUP) {
            if (previousTool != TOOL_GROUP) group.clear();
            selectedStructure = null;
            selectedMarker = null;
            selectedPrefab = null;
            selectedOpening = null;
            selectedOpeningWall = null;
        }
        invalidate();
    }

    /** Arma o desenho por pontos (contorno livre de piso/teto). */
    public void startContour(String role) {
        contourRole = role;
        contour.clear();
        setTool(TOOL_POINTS);
    }

    public int contourPoints() {
        return contour.size();
    }

    /** Remove o último ponto do contorno em andamento. */
    public void removeLastContourPoint() {
        if (!contour.isEmpty()) {
            contour.remove(contour.size() - 1);
            invalidate();
        }
    }

    public void cancelContour() {
        contour.clear();
        invalidate();
    }

    /**
     * Fecha o contorno: cria a laje poligonal e, se pedido, paredes
     * nos trechos retos alinhados aos eixos (diagonais ainda não têm
     * collider de parede — ficam sem).
     */
    public void finishContour(boolean withWalls) {
        if (contour.size() < 3) {
            cancelContour();
            return;
        }
        host.beforeChange();
        float[] polygon = new float[contour.size() * 2];
        for (int i = 0; i < contour.size(); i++) {
            polygon[i * 2] = contour.get(i)[0];
            polygon[i * 2 + 1] = contour.get(i)[1];
        }
        boolean ceiling = StructureObject.ROLE_CEILING.equals(contourRole);
        StructureObject s = new StructureObject(Ids.create(),
                StructureObject.KIND_POLY);
        s.role = contourRole;
        s.polygon = polygon;
        s.half = new float[]{0f, 0.15f, 0f};
        s.transform.y = activeBaseY
                + (ceiling ? WALL_HEIGHT + 0.15f : -0.15f);
        s.color = ceiling ? new float[]{0.38f, 0.41f, 0.48f}
                : new float[]{0.30f, 0.33f, 0.38f};
        s.syncPolyBounds();
        doc.structures.add(s);
        if (withWalls) {
            for (int i = 0; i < contour.size(); i++) {
                float[] a = contour.get(i);
                float[] b = contour.get((i + 1) % contour.size());
                addContourWall(a[0], a[1], b[0], b[1]);
            }
        }
        contour.clear();
        host.afterChange();
        host.selectionChanged((ceiling ? "teto" : "piso")
                + " de contorno criado");
        invalidate();
    }

    /**
     * Fecha a linha de paredes por pontos: uma parede por trecho
     * (retas e DIAGONAIS); `ring` liga o último ponto ao primeiro.
     */
    public void finishWallLine(boolean ring) {
        if (contour.size() < 2) {
            cancelContour();
            return;
        }
        host.beforeChange();
        int segments = contour.size() - (ring ? 0 : 1);
        for (int i = 0; i < segments; i++) {
            float[] a = contour.get(i);
            float[] b = contour.get((i + 1) % contour.size());
            addContourWall(a[0], a[1], b[0], b[1]);
        }
        contour.clear();
        host.afterChange();
        host.selectionChanged(segments + " parede(s) criadas");
        invalidate();
    }

    /**
     * Parede entre dois pontos. Reta (alinhada a um eixo) vira o bloco
     * de sempre; DIAGONAL vira laje poligonal em pé — visual liso;
     * colisão em caixinhas de 0,25 m (dá para deslizar encostado).
     */
    private void addContourWall(float ax, float az,
                                float bx, float bz) {
        float dx = Math.abs(bx - ax);
        float dz = Math.abs(bz - az);
        if (Math.max(dx, dz) < 0.4f) {
            return; // curto demais
        }
        if (Math.min(dx, dz) <= 0.01f) {
            boolean horizontal = dx >= dz;
            StructureObject s = new StructureObject(Ids.create(),
                    StructureObject.KIND_BLOCK);
            s.role = StructureObject.ROLE_WALL;
            float half = (horizontal ? dx : dz) / 2f + WALL_HALF_T;
            s.transform.x = (ax + bx) / 2f;
            s.transform.y = activeBaseY + WALL_HEIGHT / 2f;
            s.transform.z = (az + bz) / 2f;
            s.half = horizontal
                    ? new float[]{half, WALL_HEIGHT / 2f, WALL_HALF_T}
                    : new float[]{WALL_HALF_T, WALL_HEIGHT / 2f, half};
            s.color = new float[]{0.46f, 0.48f, 0.55f};
            doc.structures.add(s);
            return;
        }
        // diagonal: faixa de espessura 0,3 m ao longo do trecho
        float len = (float) Math.hypot(bx - ax, bz - az);
        float nx = -(bz - az) / len * WALL_HALF_T;
        float nz = (bx - ax) / len * WALL_HALF_T;
        StructureObject s = new StructureObject(Ids.create(),
                StructureObject.KIND_POLY);
        s.role = StructureObject.ROLE_WALL;
        s.polygon = new float[]{ax + nx, az + nz, bx + nx, bz + nz,
                bx - nx, bz - nz, ax - nx, az - nz};
        s.half = new float[]{0f, WALL_HEIGHT / 2f, 0f};
        s.transform.y = activeBaseY + WALL_HEIGHT / 2f;
        s.color = new float[]{0.46f, 0.48f, 0.55f};
        s.syncPolyBounds();
        doc.structures.add(s);
    }

    /** Inimigo selecionado? Então o próximo toque marca a patrulha. */
    public boolean startRouteMode() {
        if (selectedPrefab == null
                || !selectedPrefab.prefabId.startsWith("enemy.")
                || selectedPrefab.locked) {
            return false;
        }
        routeMode = true;
        return true;
    }

    public boolean selectedIsEnemy() {
        return selectedPrefab != null
                && selectedPrefab.prefabId.startsWith("enemy.");
    }

    public int tool() {
        return tool;
    }

    public boolean hasSelection() {
        return selectedStructure != null || selectedPrefab != null
                || selectedOpening != null || selectedMarker != null
                || !group.isEmpty();
    }

    public boolean hasGroupSelection() {
        return !group.isEmpty();
    }

    public StructureObject selectedStructure() {
        return selectedStructure;
    }

    public PrefabInstance selectedPrefab() {
        return selectedPrefab;
    }

    public LogicMarker selectedMarker() {
        return selectedMarker;
    }

    public boolean selectedLocked() {
        if (!group.isEmpty()) {
            return group.anyLocked();
        }
        if (selectedOpening != null) {
            return selectedOpening.locked
                    || (selectedOpeningWall != null
                    && selectedOpeningWall.locked);
        }
        if (selectedStructure != null) {
            return selectedStructure.locked;
        }
        if (selectedPrefab != null) {
            return selectedPrefab.locked;
        }
        return selectedMarker != null && selectedMarker.locked;
    }

    public boolean selectedAllLocked() {
        return !group.isEmpty() ? group.allLocked() : selectedLocked();
    }

    /** Arma a ferramenta PEÇA: o próximo toque solta esta peça. */
    public void setActivePrefab(PrefabDefinition def) {
        activePrefab = def;
        setTool(TOOL_PREFAB);
    }

    public PrefabDefinition activePrefab() {
        return activePrefab;
    }

    /**
     * Arma a ferramenta VÃO. Tipos: door/portal/window do WallOpening
     * ou o preset "window_bath" (janela alta e pequena de banheiro,
     * salva como window).
     */
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
        if (selectedLocked()) {
            host.selectionChanged("objeto travado — destrave para editar");
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
            selectedPrefab.transform.y = activeBaseY
                    + Math.max(0f, Math.min(10f, value));
            host.selectionChanged(describePrefab(selectedPrefab));
        }
        host.afterChange();
        invalidate();
    }

    /**
     * Gira a seleção 90°: peça acumula yaw (porta troca as meias
     * dimensões); estrutura troca largura × profundidade no lugar.
     */
    public void rotateSelected() {
        if (selectedPrefab == null && selectedStructure == null) {
            return;
        }
        if (selectedLocked()) {
            host.selectionChanged("objeto travado — destrave para girar");
            return;
        }
        mutateSelected(() -> {
            if (selectedStructure != null) {
                if (selectedStructure.polygon != null) {
                    // gira o contorno 90° em torno do centro
                    float cx = selectedStructure.transform.x;
                    float cz = selectedStructure.transform.z;
                    float[] p = selectedStructure.polygon;
                    for (int i = 0; i < p.length / 2; i++) {
                        float dx = p[i * 2] - cx;
                        float dz = p[i * 2 + 1] - cz;
                        p[i * 2] = cx - dz;
                        p[i * 2 + 1] = cz + dx;
                    }
                    selectedStructure.syncPolyBounds();
                    return;
                }
                float tmp = selectedStructure.half[0];
                selectedStructure.half[0] = selectedStructure.half[2];
                selectedStructure.half[2] = tmp;
                return;
            }
            PrefabInstance p = selectedPrefab;
            if (p.prefabId.startsWith("door.")) {
                float hx = p.floatProperty("halfX", 1.5f);
                p.properties.put("halfX", p.floatProperty("halfZ", 0.4f));
                p.properties.put("halfZ", hx);
            } else {
                p.transform.yaw = (p.transform.yaw + 90f) % 360f;
            }
        });
    }

    /** Mutação da seleção com undo + redesenho + status atualizado. */
    public void mutateSelected(Runnable change) {
        if (!hasSelection()) {
            return;
        }
        if (selectedLocked()) {
            host.selectionChanged("objeto travado — destrave para editar");
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
                ? String.format("  ·  peitoril %.2f m", o.sill) : "")
                + (o.locked ? "  ·  TRAVADO" : "");
    }

    private String describePrefab(PrefabInstance p) {
        return p.prefabId + String.format("  ·  %.2f m do chão",
                p.transform.y - activeBaseY)
                + (p.locked ? "  ·  TRAVADO" : "");
    }

    /** Duplica a seleção 0,5 m para baixo/direita e mantém o original. */
    public void duplicateSelected() {
        if (!hasSelection() || (selectedMarker != null && group.isEmpty())) {
            host.selectionChanged(selectedMarker == null
                    ? "nada selecionado"
                    : "início/saída são únicos e não podem ser duplicados");
            return;
        }
        host.beforeChange();
        if (!group.isEmpty()) {
            int count = group.duplicateInto(doc, 0.5f, 0.5f);
            host.afterChange();
            host.selectionChanged(count + " objeto(s) duplicado(s)");
            focusSelection();
            invalidate();
            return;
        } else if (selectedStructure != null) {
            StructureObject copy = MapCopies.structure(selectedStructure,
                    0.5f, 0.5f);
            doc.structures.add(copy);
            selectedStructure = copy;
        } else if (selectedPrefab != null) {
            PrefabInstance copy = MapCopies.prefab(selectedPrefab,
                    0.5f, 0.5f);
            doc.prefabs.add(copy);
            selectedPrefab = copy;
        } else {
            WallOpening copy = MapCopies.opening(selectedOpening);
            float half = WallGeometry.halfLength(selectedOpeningWall);
            float step = copy.width + 0.25f;
            float candidate = copy.offset + step;
            if (candidate + copy.width / 2f > half) {
                candidate = copy.offset - step;
            }
            copy.offset = Math.max(-half + copy.width / 2f,
                    Math.min(half - copy.width / 2f, candidate));
            selectedOpeningWall.openings.add(copy);
            selectedOpening = copy;
        }
        host.afterChange();
        host.selectionChanged("cópia criada");
        focusSelection();
        invalidate();
    }

    /** Trava/destrava o elemento selecionado sem impedir sua seleção. */
    public void toggleSelectedLock() {
        if (!hasSelection()) {
            return;
        }
        if (selectedOpening != null && selectedOpeningWall != null
                && selectedOpeningWall.locked) {
            host.selectionChanged("a parede está travada — destrave a parede");
            return;
        }
        host.beforeChange();
        if (!group.isEmpty()) {
            boolean lock = !group.allLocked();
            group.toggleLocks();
            host.afterChange();
            host.selectionChanged(group.size() + " objeto(s) "
                    + (lock ? "travados" : "destravados"));
            invalidate();
            return;
        }
        boolean value = !selectedLocked();
        if (selectedOpening != null) {
            selectedOpening.locked = value;
        } else if (selectedStructure != null) {
            selectedStructure.locked = value;
        } else if (selectedPrefab != null) {
            selectedPrefab.locked = value;
        } else {
            selectedMarker.locked = value;
        }
        host.afterChange();
        host.selectionChanged(value ? "objeto TRAVADO" : "objeto destravado");
        invalidate();
    }

    public void deleteSelected() {
        if (selectedStructure == null && selectedPrefab == null
                && selectedOpening == null && selectedMarker == null
                && group.isEmpty()) {
            return;
        }
        if (selectedLocked()) {
            host.selectionChanged("objeto travado — destrave para excluir");
            return;
        }
        host.beforeChange();
        if (!group.isEmpty()) {
            group.deleteFrom(doc);
        } else if (selectedOpening != null) {
            selectedOpeningWall.openings.remove(selectedOpening);
        } else if (selectedStructure != null) {
            doc.structures.remove(selectedStructure);
        } else if (selectedPrefab != null) {
            doc.prefabs.remove(selectedPrefab);
        } else {
            doc.markers.remove(selectedMarker);
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
        group.clear();
        host.selectionChanged(null);
    }

    /** Centraliza e aproxima a seleção atual. */
    public void focusSelection() {
        if (!hasSelection()) {
            focusAll();
            return;
        }
        if (!group.isEmpty()) {
            float[] b = group.bounds();
            frame(b[0], b[1], b[2], b[3]);
        } else if (selectedStructure != null) {
            frame(selectedStructure.transform.x - selectedStructure.half[0],
                    selectedStructure.transform.z - selectedStructure.half[2],
                    selectedStructure.transform.x + selectedStructure.half[0],
                    selectedStructure.transform.z + selectedStructure.half[2]);
        } else if (selectedPrefab != null) {
            float[] fp = PrefabMeshFactory.footprint(selectedPrefab.prefabId);
            float hx = fp == null ? 1f : fp[0];
            float hz = fp == null ? 1f : fp[1];
            frame(selectedPrefab.transform.x - hx,
                    selectedPrefab.transform.z - hz,
                    selectedPrefab.transform.x + hx,
                    selectedPrefab.transform.z + hz);
        } else if (selectedMarker != null) {
            float r = Math.max(1.5f, selectedMarker.radius);
            frame(selectedMarker.x - r, selectedMarker.z - r,
                    selectedMarker.x + r, selectedMarker.z + r);
        } else {
            float[] point = WallGeometry.pointAt(selectedOpeningWall,
                    selectedOpening.offset);
            float x = point[0];
            float z = point[1];
            frame(x - 1.5f, z - 1.5f, x + 1.5f, z + 1.5f);
        }
    }

    /** Enquadra todo o conteúdo do mapa. */
    public void focusAll() {
        float minX = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (StructureObject s : doc.structures) {
            if (!visible(s)) continue;
            minX = Math.min(minX, s.transform.x - s.half[0]);
            minZ = Math.min(minZ, s.transform.z - s.half[2]);
            maxX = Math.max(maxX, s.transform.x + s.half[0]);
            maxZ = Math.max(maxZ, s.transform.z + s.half[2]);
        }
        for (PrefabInstance p : doc.prefabs) {
            if (!visible(p)) continue;
            minX = Math.min(minX, p.transform.x - 1f);
            minZ = Math.min(minZ, p.transform.z - 1f);
            maxX = Math.max(maxX, p.transform.x + 1f);
            maxZ = Math.max(maxZ, p.transform.z + 1f);
        }
        for (LogicMarker m : doc.markers) {
            if (!visible(m)) continue;
            minX = Math.min(minX, m.x - Math.max(0.5f, m.radius));
            minZ = Math.min(minZ, m.z - Math.max(0.5f, m.radius));
            maxX = Math.max(maxX, m.x + Math.max(0.5f, m.radius));
            maxZ = Math.max(maxZ, m.z + Math.max(0.5f, m.radius));
        }
        if (minX == Float.MAX_VALUE) {
            frame(-4f, -4f, 4f, 4f);
        } else {
            frame(minX, minZ, maxX, maxZ);
        }
    }

    private void frame(float minX, float minZ, float maxX, float maxZ) {
        camX = (minX + maxX) / 2f;
        camZ = (minZ + maxZ) / 2f;
        float spanX = Math.max(2f, maxX - minX);
        float spanZ = Math.max(2f, maxZ - minZ);
        float w = Math.max(1f, getWidth() * 0.78f);
        float h = Math.max(1f, getHeight() * 0.62f);
        scale = Math.max(6f, Math.min(160f,
                Math.min(w / spanX, h / spanZ)));
        invalidate();
    }

    public int objectCount() {
        int openings = 0;
        for (StructureObject s : doc.structures) openings += s.openings.size();
        return doc.structures.size() + doc.prefabs.size() + doc.markers.size()
                + openings;
    }

    public String objectLabel(int index) {
        if (index < doc.structures.size()) {
            StructureObject s = doc.structures.get(index);
            return (s.locked ? "[TRAVADO] " : "")
                    + StructureRoles.name(s) + elevation(s.transform.y)
                    + " · " + shortId(s.id);
        }
        index -= doc.structures.size();
        if (index < doc.prefabs.size()) {
            PrefabInstance p = doc.prefabs.get(index);
            return (p.locked ? "[TRAVADO] " : "")
                    + p.prefabId + elevation(p.transform.y)
                    + " · " + shortId(p.id);
        }
        index -= doc.prefabs.size();
        if (index < doc.markers.size()) {
            LogicMarker m = doc.markers.get(index);
            return (m.locked ? "[TRAVADO] " : "")
                    + (LogicMarker.PLAYER_SPAWN.equals(m.type)
                    ? "Início" : "Saída") + elevation(m.y)
                    + " · " + shortId(m.id);
        }
        index -= doc.markers.size();
        for (StructureObject wall : doc.structures) {
            if (index < wall.openings.size()) {
                WallOpening opening = wall.openings.get(index);
                return (opening.locked ? "[TRAVADO] " : "")
                        + describeOpening(opening) + " · parede "
                        + shortId(wall.id) + elevation(wall.transform.y);
            }
            index -= wall.openings.size();
        }
        return "objeto desconhecido";
    }

    public void selectObject(int index) {
        clearSelection();
        if (index < doc.structures.size()) {
            selectedStructure = doc.structures.get(index);
            switchStory(StoryLevels.baseOf(selectedStructure,
                    storyLevels), false);
            host.selectionChanged(StructureRoles.describe(selectedStructure));
        } else {
            index -= doc.structures.size();
            if (index < doc.prefabs.size()) {
                selectedPrefab = doc.prefabs.get(index);
                switchStory(StoryLevels.baseOf(selectedPrefab,
                        storyBases()), false);
                host.selectionChanged(describePrefab(selectedPrefab));
            } else {
                index -= doc.prefabs.size();
                if (index < doc.markers.size()) {
                    selectedMarker = doc.markers.get(index);
                    switchStory(selectedMarker.y, false);
                    host.selectionChanged((LogicMarker.PLAYER_SPAWN.equals(
                            selectedMarker.type) ? "início" : "saída")
                            + (selectedMarker.locked
                            ? "  ·  TRAVADO" : ""));
                } else {
                    index -= doc.markers.size();
                    for (StructureObject wall : doc.structures) {
                        if (index < wall.openings.size()) {
                            selectedOpeningWall = wall;
                            selectedOpening = wall.openings.get(index);
                            switchStory(StoryLevels.baseOf(wall), false);
                            host.selectionChanged(
                                    describeOpening(selectedOpening));
                            break;
                        }
                        index -= wall.openings.size();
                    }
                }
            }
        }
        focusSelection();
        invalidate();
    }

    private static String shortId(String id) {
        if (id == null) {
            return "?";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String elevation(float value) {
        return String.format(" · Y %.2f m", value).replace('.', ',');
    }

    // ---- coordenadas ----

    float toWorldX(float px) {
        return (px - getWidth() / 2f) / scale + camX;
    }

    float toWorldZ(float py) {
        return (py - getHeight() / 2f) / scale + camZ;
    }

    float toPxX(float wx) {
        return (wx - camX) * scale + getWidth() / 2f;
    }

    float toPxY(float wz) {
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
        // laje/piso/bloco: gruda nas FACES das paredes (a face externa
        // fica fora da grade por causa da espessura — era impossível
        // alinhar a laje só com a grade)
        if (tool == TOOL_FLOOR || tool == TOOL_CEILING
                || tool == TOOL_BLOCK) {
            return faceOrGrid(xAxis ? wx : wz, xAxis, null);
        }
        return snap(xAxis ? wx : wz);
    }

    /**
     * Alinha à face de parede mais próxima nesse eixo (as duas faces
     * laterais E as pontas contam); longe de todas, cai na grade.
     */
    private float faceOrGrid(float v, boolean xAxis,
                             StructureObject exclude) {
        float best = Math.max(0.24f, 28f / scale);
        float hit = Float.NaN;
        for (StructureObject s : doc.structures) {
            if (!visible(s) || s == exclude || !StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            float c = xAxis ? s.transform.x : s.transform.z;
            float h = xAxis ? s.half[0] : s.half[2];
            float dLo = Math.abs(v - (c - h));
            if (dLo < best) {
                best = dLo;
                hit = c - h;
            }
            float dHi = Math.abs(v - (c + h));
            if (dHi < best) {
                best = dHi;
                hit = c + h;
            }
        }
        return Float.isNaN(hit) ? snap(v) : hit;
    }

    /**
     * Ponto de seleção de piso/teto/bloco (o 2D sobrepõe tudo; a
     * bolinha desambigua). Teto fica acima do centro, piso abaixo —
     * nunca coincidem.
     */
    float[] chipPos(StructureObject s) {
        float dz = 0f;
        String role = StructureRoles.roleOf(s);
        if (StructureObject.ROLE_CEILING.equals(role)) {
            dz = -30f / scale;
        } else if (StructureObject.ROLE_FLOOR.equals(role)) {
            dz = 30f / scale;
        }
        return new float[]{s.transform.x, s.transform.z + dz};
    }

    static int chipColor(StructureObject s) {
        switch (StructureRoles.roleOf(s)) {
            case StructureObject.ROLE_CEILING: return 0xFF8FA9C9;
            case StructureObject.ROLE_FLOOR: return 0xFF9AA5AD;
            default: return 0xFFC9A06C;
        }
    }

    /** Vértice do contorno selecionado perto do toque, ou -1. */
    private int polyPointAt(float wx, float wz) {
        float[] p = selectedStructure.polygon;
        float best = Math.max(0.3f, 26f / scale);
        int hit = -1;
        for (int i = 0; i < p.length / 2; i++) {
            float d = (float) Math.hypot(wx - p[i * 2],
                    wz - p[i * 2 + 1]);
            if (d < best) {
                best = d;
                hit = i;
            }
        }
        return hit;
    }

    /** Aresta da estrutura selecionada perto do toque, ou -1. */
    private int edgeAt(float wx, float wz) {
        StructureObject s = selectedStructure;
        float r = Math.max(0.2f, 24f / scale);
        float minX = s.transform.x - s.half[0];
        float maxX = s.transform.x + s.half[0];
        float minZ = s.transform.z - s.half[2];
        float maxZ = s.transform.z + s.half[2];
        int best = -1;
        float bd = r;
        if (wz > minZ - r && wz < maxZ + r) {
            if (Math.abs(wx - minX) < bd) {
                bd = Math.abs(wx - minX);
                best = 0;
            }
            if (Math.abs(wx - maxX) < bd) {
                bd = Math.abs(wx - maxX);
                best = 1;
            }
        }
        if (wx > minX - r && wx < maxX + r) {
            if (Math.abs(wz - minZ) < bd) {
                bd = Math.abs(wz - minZ);
                best = 2;
            }
            if (Math.abs(wz - maxZ) < bd) {
                best = 3;
            }
        }
        return best;
    }

    /** Puxa a aresta: só aquele lado move, grudando em face/grade. */
    private void dragEdgeTo() {
        StructureObject s = selectedStructure;
        boolean xAxis = dragEdge <= 1;
        float v = faceOrGrid(xAxis ? rawX : rawZ, xAxis, s);
        int hIdx = xAxis ? 0 : 2;
        float c = xAxis ? s.transform.x : s.transform.z;
        float h = s.half[hIdx];
        float lo = c - h;
        float hi = c + h;
        if (dragEdge == 0 || dragEdge == 2) {
            lo = Math.min(v, hi - 0.1f);
        } else {
            hi = Math.max(v, lo + 0.1f);
        }
        if (xAxis) {
            s.transform.x = (lo + hi) / 2f;
        } else {
            s.transform.z = (lo + hi) / 2f;
        }
        s.half[hIdx] = (hi - lo) / 2f;
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
            if (!visible(s) || s.polygon != null || !StructureObject.ROLE_WALL
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
            if (!visible(s) || s.polygon != null || !StructureObject.ROLE_WALL
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
                if (tool == TOOL_GROUP) {
                    groupMoving = !group.isEmpty()
                            && group.containsPoint(rawX, rawZ);
                    groupLastX = curX;
                    groupLastZ = curZ;
                    movedSelection = false;
                    if (!groupMoving) {
                        group.clear();
                        host.selectionChanged("arraste ao redor dos objetos");
                    } else if (group.anyLocked()) {
                        host.selectionChanged("a seleção contém objeto "
                                + "travado — destrave para mover");
                    }
                } else if (tool == TOOL_SELECT) {
                    dragEdge = -1;
                    dragPoint = -1;
                    if (!selectedLocked() && selectedStructure != null
                            && selectedStructure.polygon != null) {
                        dragPoint = polyPointAt(rawX, rawZ);
                    } else if (!selectedLocked()
                            && selectedStructure != null) {
                        dragEdge = edgeAt(rawX, rawZ);
                    }
                    if (dragEdge < 0 && dragPoint < 0) {
                        pick(toWorldX(e.getX()), toWorldZ(e.getY()));
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                commitMovedSelection();
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
                    } else if (tool == TOOL_GROUP && groupMoving
                            && !group.anyLocked()) {
                        float dx = curX - groupLastX;
                        float dz = curZ - groupLastZ;
                        if (dx != 0f || dz != 0f) {
                            if (!movedSelection) {
                                host.beforeChange();
                                movedSelection = true;
                            }
                            group.translate(dx, dz);
                            groupLastX = curX;
                            groupLastZ = curZ;
                            host.selectionChanged(group.size()
                                    + " objeto(s) movendo");
                        }
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
                dragEdge = -1;
                dragPoint = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                commitMovedSelection();
                dragging = false;
                panning = false;
                groupMoving = false;
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
        if (routeMode && selectedPrefab != null) {
            applyRoute();
            return;
        }
        switch (tool) {
            case TOOL_FLOOR:
                addRect(StructureObject.ROLE_FLOOR,
                        activeBaseY - 0.15f, 0.15f,
                        new float[]{0.30f, 0.33f, 0.38f});
                break;
            case TOOL_BLOCK:
                addRect(StructureObject.ROLE_BLOCK,
                        activeBaseY + 0.5f, 0.5f,
                        new float[]{0.62f, 0.45f, 0.30f});
                break;
            case TOOL_CEILING:
                // placa de 0.3m com a base na altura padrão da parede
                addRect(StructureObject.ROLE_CEILING,
                        activeBaseY + WALL_HEIGHT + 0.15f, 0.15f,
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
            case TOOL_POINTS: {
                boolean wallLine =
                        StructureObject.ROLE_WALL.equals(contourRole);
                float grab = Math.max(0.4f, 26f / scale);
                // tocar no primeiro ponto fecha o contorno/anel
                if (contour.size() >= 3) {
                    float[] first = contour.get(0);
                    if (Math.hypot(curX - first[0], curZ - first[1])
                            < grab) {
                        if (wallLine) {
                            finishWallLine(true);
                        } else {
                            host.polygonClosed(!StructureObject
                                    .ROLE_CEILING.equals(contourRole));
                        }
                        break;
                    }
                }
                // paredes: tocar no ÚLTIMO ponto encerra a linha aberta
                if (wallLine && contour.size() >= 2) {
                    float[] last = contour.get(contour.size() - 1);
                    if (Math.hypot(curX - last[0], curZ - last[1])
                            < grab) {
                        finishWallLine(false);
                        break;
                    }
                }
                contour.add(new float[]{curX, curZ});
                host.selectionChanged(contour.size() + " ponto(s) — "
                        + (wallLine
                        ? "toque no último p/ terminar, no primeiro p/ fechar"
                        : "toque no primeiro para fechar"));
                break;
            }
            case TOOL_SELECT:
                commitMovedSelection();
                break;
            case TOOL_GROUP:
                if (groupMoving) {
                    commitMovedSelection();
                } else {
                    group.select(doc, startX, startZ, curX, curZ,
                            activeBaseY);
                    host.selectionChanged(group.isEmpty()
                            ? "nenhum objeto na área"
                            : group.size() + " objeto(s) selecionado(s) — "
                            + "arraste dentro da moldura para mover");
                }
                groupMoving = false;
                break;
            default:
                break;
        }
    }

    /** Fecha a transação iniciada no primeiro deslocamento do gesto. */
    private void commitMovedSelection() {
        if (movedSelection) {
            host.afterChange();
            movedSelection = false;
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
        s.transform.y = activeBaseY + WALL_HEIGHT / 2f;
        s.transform.z = horizontal ? startZ : (startZ + curZ) / 2f;
        s.half = horizontal
                ? new float[]{half, WALL_HEIGHT / 2f, WALL_HALF_T}
                : new float[]{WALL_HALF_T, WALL_HEIGHT / 2f, half};
        s.color = new float[]{0.46f, 0.48f, 0.55f};
        doc.structures.add(s);
        host.afterChange();
    }

    /**
     * Define a patrulha do inimigo selecionado no ponto tocado; tocar
     * no próprio inimigo remove a rota (fica de guarda parado).
     */
    private void applyRoute() {
        routeMode = false;
        final PrefabInstance p = selectedPrefab;
        boolean clear = Math.hypot(curX - p.transform.x,
                curZ - p.transform.z) < Math.max(0.4f, 24f / scale);
        mutateSelected(() -> {
            if (clear) {
                p.properties.remove("patrolX");
                p.properties.remove("patrolZ");
            } else {
                p.properties.put("patrolX", curX);
                p.properties.put("patrolZ", curZ);
            }
        });
        host.selectionChanged(clear ? "inimigo de guarda (parado)"
                : String.format("patrulha até %.1f, %.1f", curX, curZ));
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
        // bolinha desambigua piso × teto × bloco também na pintura
        StructureObject chip = null;
        float chipBest = 20f / scale;
        for (StructureObject s : doc.structures) {
            if (!visible(s) || StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            float[] cp = chipPos(s);
            float d = (float) Math.hypot(wx - cp[0], wz - cp[1]);
            if (d < chipBest) {
                chipBest = d;
                chip = s;
            }
        }
        if (chip != null) {
            if (chip.locked) {
                host.selectionChanged("objeto travado — destrave para pintar");
                return;
            }
            host.beforeChange();
            chip.color = activePaint.clone();
            chip.color2 = null;
            chip.color3 = null;
            host.afterChange();
            host.selectionChanged(StructureRoles.name(chip) + " pintado");
            invalidate();
            return;
        }
        StructureObject target = structureAt(wx, wz);
        if (target == null) {
            host.selectionChanged("toque numa estrutura para pintar");
            return;
        }
        if (target.locked) {
            host.selectionChanged("objeto travado — destrave para pintar");
            return;
        }
        host.beforeChange();
        PaintTool.apply(doc, target, wx, wz, activePaint, paintBucket);
        host.afterChange();
        invalidate();
    }

    /** Estrutura sob o ponto (teto por último, como na seleção). */
    private StructureObject structureAt(float wx, float wz) {
        for (int pass = 0; pass < 2; pass++) {
            boolean wantCeiling = pass == 1;
            for (int i = doc.structures.size() - 1; i >= 0; i--) {
                StructureObject s = doc.structures.get(i);
                if (!visible(s)
                        || StructureRoles.isCeiling(s) != wantCeiling) {
                    continue;
                }
                if (structureContains(s, wx, wz)) {
                    return s;
                }
            }
        }
        return null;
    }

    private boolean structureContains(StructureObject s, float wx, float wz) {
        if (WallGeometry.diagonal(s)) {
            return WallGeometry.distanceTo(s, wx, wz)
                    <= WallGeometry.thickness(s) * 0.5f
                    + Math.max(0.08f, 8f / scale);
        }
        return Math.abs(wx - s.transform.x) <= s.half[0]
                && Math.abs(wz - s.transform.z) <= s.half[2];
    }

    /** Recorta o vão armado na parede tocada. */
    private void placeOpening() {
        if (activeOpeningType == null) {
            return;
        }
        StructureObject wall = OpeningTool.wallAt(doc, curX, curZ,
                Math.max(0.3f, 24f / scale), activeBaseY);
        if (wall == null) {
            host.selectionChanged("toque em cima de uma parede");
            return;
        }
        if (wall.locked) {
            host.selectionChanged("parede travada — destrave para criar o vão");
            return;
        }
        WallOpening o = OpeningTool.create(activeOpeningType, wall,
                curX, curZ);
        host.beforeChange();
        wall.openings.add(o);
        host.afterChange();
        host.selectionChanged(describeOpening(o));
    }

    /** Solta a peça armada onde o dedo terminou, com altura típica. */
    private void placePrefab() {
        if (activePrefab == null) {
            return;
        }
        // tocar numa peça existente seleciona (não empilha outra em cima)
        for (int i = doc.prefabs.size() - 1; i >= 0; i--) {
            PrefabInstance existing = doc.prefabs.get(i);
            if (!visible(existing)) continue;
            if (Math.hypot(curX - existing.transform.x,
                    curZ - existing.transform.z) < 24f / scale) {
                selectedPrefab = existing;
                host.selectionChanged(describePrefab(existing));
                invalidate();
                return;
            }
        }
        host.beforeChange();
        PrefabInstance p = PrefabPlacementTool.create(activePrefab,
                curX, curZ, activeBaseY);
        doc.prefabs.add(p);
        PrefabPlacementTool.autoLinkDoors(doc);
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
        marker.y = activeBaseY;
        host.afterChange();
    }

    private void pick(float wx, float wz) {
        movedSelection = false;
        group.clear();
        selectedMarker = null;
        selectedStructure = null;
        selectedPrefab = null;
        selectedOpening = null;
        selectedOpeningWall = null;
        // bolinhas de seleção têm prioridade máxima: escolhem o objeto
        // exato (teto × piso × bloco) mesmo com tudo sobreposto
        float chipRadius = 20f / scale;
        StructureObject chipHit = null;
        float chipBest = chipRadius;
        for (StructureObject s : doc.structures) {
            if (!visible(s) || StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            float[] cp = chipPos(s);
            float d = (float) Math.hypot(wx - cp[0], wz - cp[1]);
            if (d < chipBest) {
                chipBest = d;
                chipHit = s;
            }
        }
        if (chipHit != null) {
            selectedStructure = chipHit;
            grabDx = chipHit.transform.x - wx;
            grabDz = chipHit.transform.z - wz;
            host.selectionChanged(StructureRoles.describe(chipHit));
            return;
        }
        // peças primeiro: ícones pequenos por cima das estruturas
        for (int i = doc.prefabs.size() - 1; i >= 0; i--) {
            PrefabInstance p = doc.prefabs.get(i);
            if (!visible(p)) continue;
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
            if (!visible(m)) continue;
            if (Math.hypot(wx - m.x, wz - m.z) < 24f / scale) {
                selectedMarker = m;
                grabDx = m.x - wx;
                grabDz = m.z - wz;
                host.selectionChanged((LogicMarker.PLAYER_SPAWN
                        .equals(m.type) ? "início" : "saída")
                        + (m.locked ? "  ·  TRAVADO" : ""));
                return;
            }
        }
        // vãos antes das paredes: são menores e moram em cima delas
        for (StructureObject s : doc.structures) {
            if (!visible(s) || !StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            for (WallOpening o : s.openings) {
                float[] point = WallGeometry.pointAt(s, o.offset);
                float ox = point[0];
                float oz = point[1];
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
                if (!visible(s)
                        || StructureRoles.isCeiling(s) != wantCeiling) {
                    continue;
                }
                if (structureContains(s, wx, wz)) {
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
        if (selectedLocked()) {
            return;
        }
        if (!movedSelection) {
            host.beforeChange();
            movedSelection = true;
        }
        if (dragEdge >= 0 && selectedStructure != null) {
            dragEdgeTo();
            host.selectionChanged(
                    StructureRoles.describe(selectedStructure));
            return;
        }
        if (dragPoint >= 0 && selectedStructure != null
                && selectedStructure.polygon != null) {
            // puxar vértice do contorno
            selectedStructure.polygon[dragPoint * 2] =
                    faceOrGrid(rawX, true, selectedStructure);
            selectedStructure.polygon[dragPoint * 2 + 1] =
                    faceOrGrid(rawZ, false, selectedStructure);
            selectedStructure.syncPolyBounds();
            return;
        }
        if (selectedOpening != null) {
            // vão desliza pela própria parede
            OpeningTool.move(selectedOpening, selectedOpeningWall,
                    curX, curZ, true);
            return;
        }
        float nx = snap(curX + grabDx);
        float nz = snap(curZ + grabDz);
        if (selectedStructure != null) {
            if (selectedStructure.polygon != null) {
                // contorno viaja junto com o centro
                float ddx = nx - selectedStructure.transform.x;
                float ddz = nz - selectedStructure.transform.z;
                float[] p = selectedStructure.polygon;
                for (int i = 0; i < p.length / 2; i++) {
                    p[i * 2] += ddx;
                    p[i * 2 + 1] += ddz;
                }
            }
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
        renderer.draw(canvas);
    }
}
