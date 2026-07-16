package br.com.termia.construajogue.compiler;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.geometry.Triangulator;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras da seção 7 do PLANO aplicáveis à Fase 1 (mapas de blocos +
 * prefabs). Erros bloqueiam o teste; avisos não. Portas e terminais são
 * múltiplos e ligados por controllerId.
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
        checkColor(issues, "cor da neblina", doc.fogColor);
        if (doc.ambient < 0f || doc.ambient > 1f) {
            error(issues, "ambiente.faixa", "luz ambiente deve ficar entre 0 e 1");
        }
        if (doc.fogFar <= 0f) {
            error(issues, "neblina.faixa", "alcance da neblina deve ser positivo");
        }
        if (!"none".equals(doc.sky) && !"day".equals(doc.sky)
                && !"dusk".equals(doc.sky) && !"night".equals(doc.sky)) {
            error(issues, "ceu.tipo", "tipo de céu desconhecido: " + doc.sky);
        }
        if (!"auto".equals(doc.soundscape)
                && !"outdoor".equals(doc.soundscape)
                && !"tunnel".equals(doc.soundscape)
                && !"industrial".equals(doc.soundscape)) {
            error(issues, "som.tipo", "paisagem sonora desconhecida: "
                    + doc.soundscape);
        }

        if (doc.structures.isEmpty()) {
            error(issues, "estrutura.nenhuma",
                    "o mapa não tem nenhuma estrutura");
        }
        for (StructureObject s : doc.structures) {
            String label = "estrutura " + shortId(s.id);
            checkId(issues, ids, s.id, label);
            if (!knownMaterial(s.material)) {
                error(issues, "material.desconhecido", label
                        + ": material '" + s.material + "' não existe");
            }
            if (StructureObject.KIND_POLY.equals(s.kind)) {
                if (s.polygon == null || s.polygon.length < 6
                        || (s.polygon.length & 1) != 0) {
                    error(issues, "contorno.pontos",
                            label + ": contorno precisa de pares x/z para 3+ pontos");
                    continue;
                }
                checkFloats(issues, label + " (contorno)", s.polygon,
                        s.polygon.length);
                checkFloats(issues, label + " (posição)", new float[]{
                        s.transform.x, s.transform.y, s.transform.z}, 3);
                if (Triangulator.selfIntersects(s.polygon)) {
                    error(issues, "contorno.cruzado",
                            label + ": o contorno cruza a si mesmo");
                }
                if (Math.abs(Triangulator.area2(s.polygon)) < 0.2f) {
                    error(issues, "contorno.area",
                            label + ": contorno sem área");
                }
                if (s.half == null || s.half[1] <= 0f) {
                    error(issues, "estrutura.dimensao",
                            label + ": espessura deve ser positiva");
                }
                checkFloats(issues, label + " (cor)", s.color, 3);
                checkColor(issues, label + " (cor)", s.color);
                if (s.color2 != null) {
                    checkFloats(issues, label + " (cor do lado)", s.color2, 3);
                    checkColor(issues, label + " (cor do lado)", s.color2);
                }
                if (s.color3 != null) {
                    checkFloats(issues, label + " (cor do lado)", s.color3, 3);
                    checkColor(issues, label + " (cor do lado)", s.color3);
                }
                if (s.half != null && !s.openings.isEmpty()) {
                    checkOpenings(issues, ids, s, label);
                }
                continue;
            }
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
            checkColor(issues, label + " (cor)", s.color);
            if (s.color2 != null) {
                checkFloats(issues, label + " (cor do lado)", s.color2, 3);
                checkColor(issues, label + " (cor do lado)", s.color2);
            }
            if (s.color3 != null) {
                checkFloats(issues, label + " (cor do lado)", s.color3, 3);
                checkColor(issues, label + " (cor do lado)", s.color3);
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
        int objectiveTokens = 0;
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
                    p.transform.x, p.transform.y, p.transform.z,
                    p.transform.yaw}, 4);
            checkNumber(issues, label + " (escala)", p.scale);
            if (p.scale <= 0f) {
                error(issues, "peca.escala", label
                        + ": escala deve ser positiva");
            }
            for (Map.Entry<String, Object> entry : p.properties.entrySet()) {
                if (!def.allowsProperty(entry.getKey())) {
                    error(issues, "peca.propriedade", label
                            + ": propriedade '" + entry.getKey()
                            + "' não permitida");
                }
                boolean textProperty = "controllerId".equals(entry.getKey())
                        || (PrefabDefinition.BEHAVIOR_NPC_HUMAN
                        .equals(def.behavior)
                        && isNpcTextProperty(entry.getKey()));
                if (textProperty && (!(entry.getValue() instanceof String)
                        || blank((String) entry.getValue()))) {
                    error(issues, "peca.propriedade", label
                            + ": propriedade '" + entry.getKey()
                            + "' deve ser um texto válido");
                } else if (textProperty && ((String) entry.getValue()).length()
                        > npcTextLimit(entry.getKey())) {
                    error(issues, "peca.propriedade", label
                            + ": texto '" + entry.getKey() + "' é longo demais");
                } else if (!textProperty
                        && !(entry.getValue() instanceof Number)) {
                    error(issues, "peca.propriedade", label
                            + ": propriedade '" + entry.getKey()
                            + "' deve ser numérica");
                } else if (entry.getValue() instanceof Number
                        && !finite(((Number) entry.getValue()).floatValue())) {
                    error(issues, "peca.numero", label
                            + ": propriedade '" + entry.getKey()
                            + "' com número inválido");
                }
            }
            switch (def.behavior) {
                case PrefabDefinition.BEHAVIOR_DRONE:
                case PrefabDefinition.BEHAVIOR_DRONE_DORMANT:
                case PrefabDefinition.BEHAVIOR_MUTANT:
                case PrefabDefinition.BEHAVIOR_TURRET:
                case PrefabDefinition.BEHAVIOR_KAMIKAZE:
                case PrefabDefinition.BEHAVIOR_BOSS:
                    enemies++;
                    break;
                case PrefabDefinition.BEHAVIOR_NPC_HUMAN:
                    itemsAndMarkers++;
                    break;
                case PrefabDefinition.BEHAVIOR_PICKUP_HEALTH:
                case PrefabDefinition.BEHAVIOR_PICKUP_AMMO:
                case PrefabDefinition.BEHAVIOR_PICKUP_SPECIAL:
                    itemsAndMarkers++;
                    break;
                case PrefabDefinition.BEHAVIOR_PICKUP_TOKEN:
                    objectiveTokens++;
                    itemsAndMarkers++;
                    break;
                case PrefabDefinition.BEHAVIOR_TERMINAL:
                    terminals.add(p);
                    break;
                case PrefabDefinition.BEHAVIOR_DOOR:
                case PrefabDefinition.BEHAVIOR_AUTO_DOOR:
                    doors.add(p);
                    break;
                case PrefabDefinition.BEHAVIOR_STATIC:
                    if (PrefabMeshFactory.parts(p.prefabId) == null) {
                        error(issues, "peca.malha", label
                                + ": peça estática sem malha registrada");
                    }
                    break;
                default:
                    break;
            }
        }

        Set<Integer> terminalOrders = new HashSet<>();
        int highestTerminalOrder = 0;
        for (PrefabInstance terminal : terminals) {
            int order = Math.round(terminal.floatProperty("order", 0f));
            float rawOrder = terminal.floatProperty("order", 0f);
            if (Math.abs(rawOrder - order) > 0.001f) {
                error(issues, "terminal.ordem", "ordem do terminal deve "
                        + "ser um número inteiro");
            }
            if (order < 0) {
                error(issues, "terminal.ordem", "terminal "
                        + shortId(terminal.id) + ": ordem deve ser positiva");
            } else if (order > 0 && !terminalOrders.add(order)) {
                error(issues, "terminal.ordem", "ordem de terminal "
                        + order + " repetida");
            }
            highestTerminalOrder = Math.max(highestTerminalOrder, order);
        }
        for (int order = 1; order <= highestTerminalOrder; order++) {
            if (!terminalOrders.contains(order)) {
                error(issues, "terminal.ordem", "sequência de terminais "
                        + "não tem a ordem " + order);
            }
        }
        for (PrefabInstance door : doors) {
            PrefabDefinition doorDef = catalog.find(door.prefabId);
            if (doorDef != null && PrefabDefinition.BEHAVIOR_DOOR
                    .equals(doorDef.behavior)) {
                String controller = door.stringProperty("controllerId");
                PrefabInstance target = doc.findInstance(controller);
                PrefabDefinition targetDef = target == null ? null
                        : catalog.find(target.prefabId);
                if (targetDef == null || !PrefabDefinition.BEHAVIOR_TERMINAL
                        .equals(targetDef.behavior)) {
                    error(issues, "porta.terminal", "porta "
                            + shortId(door.id)
                            + " não aponta para um terminal existente");
                }
            }
            if (door.floatProperty("halfX", 0f) <= 0f
                    || door.floatProperty("halfY", 0f) <= 0f
                    || door.floatProperty("halfZ", 0f) <= 0f) {
                error(issues, "porta.dimensao", "porta " + shortId(door.id)
                        + ": halfX/halfY/halfZ devem ser positivos");
            }
        }
        for (PrefabInstance p : doc.prefabs) {
            if (p.prefabId.startsWith("prop.lamp.")
                    && p.floatProperty("lightRadius", 6f) <= 0f) {
                error(issues, "luz.raio", "raio da luminária deve ser positivo");
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
            } else {
                error(issues, "marcador.tipo", "tipo de marcador desconhecido: "
                        + m.type);
            }
        }
        if (spawns != 1) {
            error(issues, "inicio.unico", spawns == 0
                    ? "o mapa precisa de um início"
                    : "o mapa tem mais de um início");
        }
        String objective = doc.objective == null
                ? ObjectiveSpec.REACH_EXIT : doc.objective.type;
        if (ObjectiveSpec.REACH_EXIT.equals(objective) && exits != 1) {
            error(issues, "saida.unica", exits == 0
                    ? "o objetivo exige uma saída"
                    : "só uma saída por mapa");
        } else if (!ObjectiveSpec.REACH_EXIT.equals(objective) && exits > 1) {
            error(issues, "saida.unica", "só uma saída por mapa");
        }

        validateObjective(issues, doc, enemies, objectiveTokens);

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
        float halfLen = WallGeometry.halfLength(s);
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

    private static void checkColor(List<ValidationIssue> issues,
                                   String label, float[] color) {
        if (color == null || color.length != 3) return;
        for (float component : color) {
            if (component < 0f || component > 1f) {
                error(issues, "cor.faixa", label
                        + ": componentes devem ficar entre 0 e 1");
                return;
            }
        }
    }

    private static boolean knownMaterial(String material) {
        String value = material == null ? "plain" : material;
        return "plain".equals(value) || "brick".equals(value)
                || "wood".equals(value) || "checker".equals(value)
                || "metal".equals(value) || "water".equals(value)
                || "lava".equals(value) || "asphalt".equals(value);
    }

    private static boolean isNpcTextProperty(String name) {
        return "name".equals(name) || "role".equals(name)
                || "greeting".equals(name) || "background".equals(name);
    }

    private static int npcTextLimit(String name) {
        if ("name".equals(name)) return 48;
        if ("role".equals(name)) return 80;
        if ("greeting".equals(name)) return 240;
        if ("background".equals(name)) return 600;
        // controllerId e qualquer futuro texto restrito continuam pequenos.
        return 128;
    }

    private static void validateObjective(List<ValidationIssue> issues,
                                          MapDocument doc, int enemies,
                                          int tokens) {
        ObjectiveSpec o = doc.objective == null
                ? new ObjectiveSpec() : doc.objective;
        if (!ObjectiveSpec.REACH_EXIT.equals(o.type)
                && !ObjectiveSpec.ELIMINATE_ALL.equals(o.type)
                && !ObjectiveSpec.COLLECT.equals(o.type)
                && !ObjectiveSpec.SURVIVE.equals(o.type)) {
            error(issues, "objetivo.tipo",
                    "tipo de objetivo desconhecido: " + o.type);
        }
        if (ObjectiveSpec.ELIMINATE_ALL.equals(o.type) && enemies == 0) {
            error(issues, "objetivo.inimigos",
                    "eliminar todos exige ao menos um inimigo");
        }
        if (ObjectiveSpec.COLLECT.equals(o.type)) {
            if (o.target <= 0) {
                error(issues, "objetivo.alvo",
                        "coletar exige quantidade maior que zero");
            } else if (tokens < o.target) {
                error(issues, "objetivo.fichas", "há " + tokens
                        + " fichas, mas o objetivo pede " + o.target);
            }
        }
        if (ObjectiveSpec.SURVIVE.equals(o.type)
                && o.durationSeconds <= 0f) {
            error(issues, "objetivo.duracao",
                    "sobreviver exige duração maior que zero");
        }
        if (ObjectiveSpec.SURVIVE.equals(o.type)
                && o.timeLimitSeconds > 0f
                && o.timeLimitSeconds <= o.durationSeconds) {
            error(issues, "objetivo.tempo",
                    "o tempo-limite deve ser maior que a sobrevivência");
        }
        checkNumber(issues, "objetivo", o.durationSeconds);
        checkNumber(issues, "objetivo", o.timeLimitSeconds);
        checkNumber(issues, "objetivo", o.twoStarSeconds);
        checkNumber(issues, "objetivo", o.threeStarSeconds);
        if (o.timeLimitSeconds < 0f || o.twoStarSeconds < 0f
                || o.threeStarSeconds < 0f) {
            error(issues, "objetivo.tempo", "tempos não podem ser negativos");
        }
        if (o.twoStarSeconds > 0f && o.threeStarSeconds > 0f
                && o.threeStarSeconds > o.twoStarSeconds) {
            error(issues, "objetivo.estrelas",
                    "meta de 3 estrelas deve ser menor que a de 2");
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
