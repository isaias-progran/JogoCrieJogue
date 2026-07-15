package br.com.termia.construajogue.compiler;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras da seção 7 do PLANO aplicáveis à Fase 1 (mapas de blocos +
 * prefabs). Erros bloqueiam o teste; avisos não. Fase 1 exige exatamente
 * um início e uma saída e aceita no máximo um terminal e uma porta
 * (limite atual do RuntimeLevel).
 */
public final class MapValidator {

    /** Mesmas medidas do Player (raio/altura da cápsula). */
    private static final float PLAYER_RADIUS = 0.35f;
    private static final float PLAYER_HEIGHT = 1.75f;

    private MapValidator() {
    }

    public static List<ValidationIssue> validate(MapDocument doc,
                                                 PrefabCatalog catalog) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        if (blank(doc.id)) {
            error(issues, "mapa.id", "o mapa precisa de um id");
        }
        checkNumber(issues, "ambiente", doc.ambient);
        checkNumber(issues, "neblina", doc.fogFar);
        checkFloats(issues, "cor da neblina", doc.fogColor, 3);

        if (doc.structures.isEmpty()) {
            error(issues, "estrutura.nenhuma",
                    "o mapa não tem nenhuma estrutura");
        }
        for (StructureObject s : doc.structures) {
            String label = "estrutura " + shortId(s.id);
            checkId(issues, ids, s.id, label);
            if (!StructureObject.KIND_BLOCK.equals(s.kind)) {
                error(issues, "estrutura.tipo",
                        label + ": tipo '" + s.kind
                                + "' ainda não suportado");
                continue;
            }
            checkFloats(issues, label + " (posição)", new float[]{
                    s.transform.x, s.transform.y, s.transform.z}, 3);
            checkFloats(issues, label + " (meias dimensões)", s.half, 3);
            checkFloats(issues, label + " (cor)", s.color, 3);
            if (s.color2 != null) {
                checkFloats(issues, label + " (cor do lado)", s.color2, 3);
            }
            if (s.half != null && (s.half[0] <= 0f || s.half[1] <= 0f
                    || s.half[2] <= 0f)) {
                error(issues, "estrutura.dimensao",
                        label + ": dimensões devem ser positivas");
            }
            if (s.half != null && !s.openings.isEmpty()) {
                checkOpenings(issues, ids, s, label);
            }
        }

        int enemies = 0;
        int itemsAndMarkers = doc.markers.size();
        List<PrefabInstance> doors = new ArrayList<>();
        List<PrefabInstance> terminals = new ArrayList<>();
        for (PrefabInstance p : doc.prefabs) {
            String label = "peça " + shortId(p.id);
            checkId(issues, ids, p.id, label);
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null) {
                error(issues, "peca.desconhecida", label
                        + ": prefab '" + p.prefabId + "' não existe");
                continue;
            }
            label = def.name + " " + shortId(p.id);
            checkFloats(issues, label + " (posição)", new float[]{
                    p.transform.x, p.transform.y, p.transform.z}, 3);
            for (Map.Entry<String, Object> entry : p.properties.entrySet()) {
                if (!def.allowsProperty(entry.getKey())) {
                    error(issues, "peca.propriedade", label
                            + ": propriedade '" + entry.getKey()
                            + "' não permitida");
                }
                if (entry.getValue() instanceof Float
                        && !finite((Float) entry.getValue())) {
                    error(issues, "peca.numero", label
                            + ": propriedade '" + entry.getKey()
                            + "' com número inválido");
                }
            }
            switch (def.behavior) {
                case PrefabDefinition.BEHAVIOR_DRONE:
                case PrefabDefinition.BEHAVIOR_DRONE_DORMANT:
                case PrefabDefinition.BEHAVIOR_MUTANT:
                    enemies++;
                    break;
                case PrefabDefinition.BEHAVIOR_PICKUP_HEALTH:
                case PrefabDefinition.BEHAVIOR_PICKUP_AMMO:
                    itemsAndMarkers++;
                    break;
                case PrefabDefinition.BEHAVIOR_TERMINAL:
                    terminals.add(p);
                    break;
                case PrefabDefinition.BEHAVIOR_DOOR:
                    doors.add(p);
                    break;
                default:
                    break;
            }
        }

        if (terminals.size() > 1) {
            error(issues, "terminal.varios",
                    "só um terminal por mapa na fase atual");
        }
        if (doors.size() > 1) {
            error(issues, "porta.varias",
                    "só uma porta por mapa na fase atual");
        }
        for (PrefabInstance door : doors) {
            String controller = door.stringProperty("controllerId");
            PrefabInstance target = doc.findInstance(controller);
            PrefabDefinition targetDef = target == null ? null
                    : catalog.find(target.prefabId);
            if (targetDef == null || !PrefabDefinition.BEHAVIOR_TERMINAL
                    .equals(targetDef.behavior)) {
                error(issues, "porta.terminal", "porta " + shortId(door.id)
                        + " não aponta para um terminal existente");
            }
            if (door.floatProperty("halfX", 0f) <= 0f
                    || door.floatProperty("halfY", 0f) <= 0f
                    || door.floatProperty("halfZ", 0f) <= 0f) {
                error(issues, "porta.dimensao", "porta " + shortId(door.id)
                        + ": halfX/halfY/halfZ devem ser positivos");
            }
        }

        int spawns = 0;
        int exits = 0;
        LogicMarker spawn = null;
        for (LogicMarker m : doc.markers) {
            checkId(issues, ids, m.id, "marcador " + shortId(m.id));
            checkFloats(issues, "marcador " + shortId(m.id), new float[]{
                    m.x, m.y, m.z, m.yaw, m.radius}, 5);
            if (LogicMarker.PLAYER_SPAWN.equals(m.type)) {
                spawns++;
                spawn = m;
            } else if (LogicMarker.EXIT.equals(m.type)) {
                exits++;
                if (m.radius <= 0f) {
                    error(issues, "saida.raio",
                            "a saída precisa de raio positivo");
                }
            }
        }
        if (spawns != 1) {
            error(issues, "inicio.unico", spawns == 0
                    ? "o mapa precisa de um início"
                    : "o mapa tem mais de um início");
        }
        if (exits != 1) {
            error(issues, "saida.unica", exits == 0
                    ? "o mapa precisa de uma saída"
                    : "só uma saída por mapa na fase atual");
        }

        if (spawn != null) {
            checkSpawnFree(issues, doc, spawn);
        }

        if (doc.structures.size() > 80) {
            warning(issues, "limite.estruturas",
                    "mais de 80 estruturas: desempenho pode cair");
        }
        if (doc.prefabs.size() > 200) {
            warning(issues, "limite.pecas",
                    "mais de 200 peças: desempenho pode cair");
        }
        if (enemies > 24) {
            warning(issues, "limite.inimigos",
                    "mais de 24 inimigos: desempenho pode cair");
        }
        if (itemsAndMarkers > 64) {
            warning(issues, "limite.itens",
                    "mais de 64 itens/marcadores");
        }
        return issues;
    }

    public static boolean hasError(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                return true;
            }
        }
        return false;
    }

    private static void checkSpawnFree(List<ValidationIssue> issues,
                                       MapDocument doc, LogicMarker spawn) {
        float[] pos = {spawn.x, spawn.y, spawn.z};
        float[] bounds = new float[6];
        for (StructureObject s : doc.structures) {
            if (s.half == null
                    || !StructureObject.KIND_BLOCK.equals(s.kind)) {
                continue;
            }
            LegacyLevelLoader.toBounds(s.transform.x, s.transform.y,
                    s.transform.z, s.half[0], s.half[1], s.half[2], bounds);
            if (Collision.overlaps(pos, PLAYER_RADIUS, PLAYER_HEIGHT,
                    bounds)) {
                error(issues, "inicio.bloqueado",
                        "o início está dentro de uma estrutura");
                return;
            }
        }
    }

    /** Vão precisa caber na parede e não sobrepor outro vão. */
    private static void checkOpenings(List<ValidationIssue> issues,
                                      Set<String> ids, StructureObject s,
                                      String label) {
        float halfLen = Math.max(s.half[0], s.half[2]);
        float wallHeight = s.half[1] * 2f;
        List<WallOpening> sorted = new ArrayList<>(s.openings);
        sorted.sort((a, c) -> Float.compare(a.offset, c.offset));
        float lastEnd = Float.NEGATIVE_INFINITY;
        for (WallOpening o : sorted) {
            checkId(issues, ids, o.id, label + " (vão)");
            checkFloats(issues, label + " (vão)", new float[]{
                    o.offset, o.width, o.height, o.sill}, 4);
            if (o.width <= 0f || o.height <= 0f || o.sill < 0f) {
                error(issues, "vao.dimensao",
                        label + ": vão com medidas inválidas");
                continue;
            }
            if (Math.abs(o.offset) + o.width / 2f > halfLen) {
                error(issues, "vao.fora",
                        label + ": vão sai do comprimento da parede");
            }
            if (o.sill + o.height > wallHeight + 0.001f) {
                error(issues, "vao.altura",
                        label + ": vão mais alto que a parede");
            }
            if (o.offset - o.width / 2f < lastEnd) {
                error(issues, "vao.sobreposto",
                        label + ": vãos sobrepostos");
            }
            lastEnd = o.offset + o.width / 2f;
        }
    }

    private static void checkId(List<ValidationIssue> issues,
                                Set<String> ids, String id, String label) {
        if (blank(id)) {
            error(issues, "id.vazio", label + ": id vazio");
        } else if (!ids.add(id)) {
            error(issues, "id.duplicado", label + ": id repetido");
        }
    }

    private static void checkFloats(List<ValidationIssue> issues,
                                    String label, float[] values, int count) {
        if (values == null || values.length != count) {
            error(issues, "numero.faltando",
                    label + ": valores obrigatórios ausentes");
            return;
        }
        for (float value : values) {
            if (!finite(value)) {
                error(issues, "numero.invalido",
                        label + ": número inválido");
                return;
            }
        }
    }

    private static void checkNumber(List<ValidationIssue> issues,
                                    String label, float value) {
        if (!finite(value)) {
            error(issues, "numero.invalido", label + ": número inválido");
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String shortId(String id) {
        return id == null ? "?" : id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static void error(List<ValidationIssue> issues, String code,
                              String message) {
        issues.add(new ValidationIssue(ValidationIssue.ERROR, code, message));
    }

    private static void warning(List<ValidationIssue> issues, String code,
                                String message) {
        issues.add(new ValidationIssue(ValidationIssue.WARNING, code,
                message));
    }
}
