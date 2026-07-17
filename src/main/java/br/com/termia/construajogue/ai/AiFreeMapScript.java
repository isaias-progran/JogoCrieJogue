package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.editor.tools.PrefabPlacementTool;
import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.util.Ids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Modo LIVRE: a IA desenha o mapa com coordenadas reais num roteiro de
 * comandos, um por linha. O parser traduz cada linha em operações normais
 * do editor; linha inválida vira aviso, nunca código. MapValidator e
 * LevelCompiler continuam sendo o portão antes de salvar.
 */
public final class AiFreeMapScript {

    /** Limites de proteção de memória; o validador avisa bem antes. */
    private static final int MAX_STRUCTURES = 500;
    private static final int MAX_PREFABS = 400;
    private static final float GRID = 48f;
    private static final List<String> NUMERIC_PROPS = Arrays.asList(
            "order", "halfX", "halfY", "halfZ", "lightR", "lightG",
            "lightB", "lightRadius", "lightOffsetY");
    private static final List<String> TEXT_PROPS = Arrays.asList(
            "name", "role", "greeting", "background", "combatLine1",
            "combatLine2", "combatLine3");

    private AiFreeMapScript() {
    }

    public static final class Result {
        public final MapDocument document;
        public final List<String> warnings = new ArrayList<>();

        Result(MapDocument document) {
            this.document = document;
        }
    }

    /** Ponteiros de contexto do roteiro (última parede/peça). Cada
     *  `usar` recebe um Cursor próprio: estado não vaza do macro. */
    static final class Cursor {
        StructureObject wall;
        PrefabInstance prefab;
        PrefabDefinition def;
    }

    /** Estouro de estruturas/peças: interrompe o `usar` inteiro
     *  (macro-bomba) em vez de tentar linha por linha. */
    static final class LimitReached extends IllegalArgumentException {
        LimitReached(String message) {
            super(message);
        }
    }

    public static Result parse(String script, PrefabCatalog catalog) {
        if (script == null || script.trim().isEmpty()) {
            throw new IllegalArgumentException("a IA devolveu roteiro vazio");
        }
        MapDocument doc = new MapDocument();
        doc.id = Ids.create();
        doc.name = "Mapa livre da IA";
        doc.sky = "day";
        Result result = new Result(doc);
        Cursor cursor = new Cursor();
        AiFreeMacros macros = new AiFreeMacros();
        String[] lines = script.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")
                    || line.startsWith("```")) continue;
            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase(Locale.ROOT);
            try {
                if (macros.handle(command, parts, i + 1, line, doc,
                        catalog, result)) continue;
                if (!execute(command, parts, line, doc, catalog, result,
                        cursor)) {
                    warn(result, "linha " + (i + 1)
                            + ": comando desconhecido '" + command + "'");
                }
            } catch (RuntimeException bad) {
                warn(result, "linha " + (i + 1) + " ignorada ("
                        + command + "): " + bad.getMessage());
            }
        }
        macros.endOfScript(result);
        finish(doc, catalog, result);
        return result;
    }

    /**
     * Executa uma linha já separada em partes; devolve false para
     * comando desconhecido. Chamado pelo roteiro principal e pela
     * expansão de macros (AiFreeMacros), que passa um Cursor próprio.
     */
    static boolean execute(String command, String[] parts, String line,
                           MapDocument doc, PrefabCatalog catalog,
                           Result result, Cursor cursor) {
        if ("nome".equals(command) || "titulo".equals(command)) {
            String value = clip(rest(line), 48);
            if (!value.isEmpty()) doc.name = value;
        } else if ("ceu".equals(command)) {
            doc.sky = oneOf(parts[1], "day", "dusk", "night", "none");
        } else if ("som".equals(command)) {
            doc.soundscape = oneOf(parts[1], "auto", "outdoor",
                    "tunnel", "industrial");
        } else if ("ambiente".equals(command)) {
            doc.ambient = clamp(number(parts[1]), 0.05f, 1f);
        } else if ("neblina".equals(command)) {
            doc.fogColor = color(parts, 1);
            doc.fogFar = clamp(number(parts[4]), 10f, 160f);
        } else if ("objetivo".equals(command)) {
            objective(doc, parts);
        } else if ("piso".equals(command)) {
            floor(doc, parts, result);
        } else if ("teto".equals(command)) {
            ceiling(doc, parts, result);
        } else if ("parede".equals(command)) {
            cursor.wall = wall(doc, parts, result);
        } else if ("vao".equals(command)) {
            opening(cursor.wall, parts, result);
        } else if ("bloco".equals(command)) {
            box(doc, parts, result);
        } else if ("peca".equals(command)) {
            PrefabInstance placed = prefab(doc, parts, catalog, result);
            if (placed != null) {
                cursor.prefab = placed;
                cursor.def = catalog.find(placed.prefabId);
            }
        } else if ("prop".equals(command)) {
            numericProp(cursor.prefab, cursor.def, parts);
        } else if ("texto".equals(command)) {
            textProp(cursor.prefab, cursor.def, parts, line);
        } else if ("patrulha".equals(command)) {
            patrol(cursor.prefab, cursor.def, parts);
        } else if ("inicio".equals(command)) {
            marker(doc, LogicMarker.PLAYER_SPAWN, parts);
        } else if ("saida".equals(command)) {
            marker(doc, LogicMarker.EXIT, parts);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Resgate: quando o validador recusa o mapa, remove/conserta o que
     * quebrou a regra em vez de perder a geração inteira. Devolve o
     * número de consertos; cada um vira um aviso na prévia.
     */
    public static int salvage(MapDocument doc, PrefabCatalog catalog,
                              List<String> warnings) {
        int fixes = 0;
        int enemies = 0;
        int tokens = 0;
        List<PrefabInstance> keep = new ArrayList<>();
        for (PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null) {
                warn(warnings, "resgate: peça desconhecida removida");
                fixes++;
                continue;
            }
            if (p.scale <= 0f) {
                p.scale = 1f;
                fixes++;
            }
            List<String> drop = new ArrayList<>();
            for (java.util.Map.Entry<String, Object> entry
                    : p.properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                boolean text = "controllerId".equals(key)
                        || (PrefabDefinition.BEHAVIOR_NPC_HUMAN
                        .equals(def.behavior) && TEXT_PROPS.contains(key));
                boolean bool = PrefabDefinition.BEHAVIOR_NPC_HUMAN
                        .equals(def.behavior) && "combatant".equals(key);
                if (text && value instanceof String
                        && !"controllerId".equals(key)
                        && ((String) value).length() > textLimit(key)) {
                    value = ((String) value).substring(0, textLimit(key));
                    entry.setValue(value);
                    warn(warnings, "resgate: texto '" + key
                            + "' do NPC foi encurtado");
                    fixes++;
                }
                boolean bad = !def.allowsProperty(key)
                        || (text && (!(value instanceof String)
                        || ((String) value).trim().isEmpty()))
                        || (bool && !(value instanceof Boolean))
                        || (!text && !bool && !(value instanceof Number))
                        || (value instanceof Number && !isFinite(
                        ((Number) value).floatValue()));
                if (bad) drop.add(key);
            }
            for (String key : drop) {
                p.properties.remove(key);
                warn(warnings, "resgate: " + def.name + " perdeu '"
                        + key + "' não permitido");
                fixes++;
            }
            if (isEnemyBehavior(def.behavior)) enemies++;
            if (PrefabDefinition.BEHAVIOR_PICKUP_TOKEN
                    .equals(def.behavior)) tokens++;
            keep.add(p);
        }
        if (keep.size() != doc.prefabs.size()) {
            doc.prefabs.clear();
            doc.prefabs.addAll(keep);
        }
        List<StructureObject> solid = new ArrayList<>();
        for (StructureObject s : doc.structures) {
            if (StructureObject.KIND_BLOCK.equals(s.kind) && (s.half == null
                    || s.half[0] <= 0f || s.half[1] <= 0f
                    || s.half[2] <= 0f)) {
                warn(warnings, "resgate: estrutura sem volume removida");
                fixes++;
                continue;
            }
            fixes += salvageOpenings(s, warnings);
            solid.add(s);
        }
        if (solid.size() != doc.structures.size()) {
            doc.structures.clear();
            doc.structures.addAll(solid);
        }
        fixes += salvageObjective(doc, enemies, tokens, warnings);
        fixes += normalizeDoors(doc, warnings, "resgate: ");
        LogicMarker spawn = doc.firstMarker(LogicMarker.PLAYER_SPAWN);
        if (spawn != null
                && !standsFree(doc, spawn.x, spawn.y, spawn.z)) {
            Result carrier = new Result(doc);
            nudgeFree(doc, spawn, "início", carrier);
            warnings.addAll(carrier.warnings);
            fixes++;
        }
        try {
            RuntimeLevel level = LevelCompiler.compile(doc, catalog);
            fixes += nudgeCompiled(doc, level.colliders(), warnings,
                    "resgate: ");
            fixes += settleEnemies(doc, catalog, level.colliders(), warnings,
                    "resgate: ");
        } catch (RuntimeException ignored) {
            // O validador ainda descreverá qualquer outro erro restante.
        }
        return fixes;
    }

    private static int salvageOpenings(StructureObject s,
                                       List<String> warnings) {
        if (s.openings.isEmpty() || s.half == null) return 0;
        float halfLength = Math.max(s.half[0], s.half[2]);
        float wallHeight = s.half[1] * 2f;
        List<WallOpening> sorted = new ArrayList<>(s.openings);
        sorted.sort((a, b) -> Float.compare(a.offset, b.offset));
        List<WallOpening> accepted = new ArrayList<>();
        float lastEnd = Float.NEGATIVE_INFINITY;
        int fixes = 0;
        for (WallOpening o : sorted) {
            boolean fits = o.width > 0f && o.height > 0f && o.sill >= 0f
                    && Math.abs(o.offset) + o.width / 2f <= halfLength
                    && o.sill + o.height <= wallHeight + 0.001f
                    && o.offset - o.width / 2f >= lastEnd;
            if (fits) {
                accepted.add(o);
                lastEnd = o.offset + o.width / 2f;
            } else {
                warn(warnings, "resgate: vão impossível removido da parede");
                fixes++;
            }
        }
        if (fixes > 0) {
            s.openings.clear();
            s.openings.addAll(accepted);
        }
        return fixes;
    }

    private static int salvageObjective(MapDocument doc, int enemies,
                                        int tokens, List<String> warnings) {
        ObjectiveSpec o = doc.objective;
        int fixes = 0;
        if (ObjectiveSpec.ELIMINATE_ALL.equals(o.type) && enemies == 0) {
            o.type = ObjectiveSpec.REACH_EXIT;
            warn(warnings, "resgate: sem inimigos, objetivo virou "
                    + "chegar à saída");
            fixes++;
        }
        if (ObjectiveSpec.COLLECT.equals(o.type)) {
            if (tokens == 0) {
                o.type = ObjectiveSpec.REACH_EXIT;
                warn(warnings, "resgate: sem fichas no mapa, objetivo "
                        + "virou chegar à saída");
                fixes++;
            } else if (o.target <= 0 || o.target > tokens) {
                o.target = tokens;
                warn(warnings, "resgate: alvo de fichas ajustado para "
                        + tokens);
                fixes++;
            }
        }
        if (ObjectiveSpec.SURVIVE.equals(o.type)) {
            if (o.durationSeconds <= 0f) {
                o.durationSeconds = 60f;
                fixes++;
            }
            if (o.timeLimitSeconds > 0f
                    && o.timeLimitSeconds <= o.durationSeconds) {
                o.timeLimitSeconds = 0f;
                fixes++;
            }
        }
        if (ObjectiveSpec.REACH_EXIT.equals(o.type)
                && doc.firstMarker(LogicMarker.EXIT) == null) {
            Result carrier = new Result(doc);
            autoExit(doc, carrier);
            warnings.addAll(carrier.warnings);
            fixes++;
        }
        return fixes;
    }

    private static boolean isEnemyBehavior(String behavior) {
        return PrefabDefinition.BEHAVIOR_DRONE.equals(behavior)
                || PrefabDefinition.BEHAVIOR_DRONE_DORMANT.equals(behavior)
                || PrefabDefinition.BEHAVIOR_MUTANT.equals(behavior)
                || PrefabDefinition.BEHAVIOR_TURRET.equals(behavior)
                || PrefabDefinition.BEHAVIOR_KAMIKAZE.equals(behavior)
                || PrefabDefinition.BEHAVIOR_BOSS.equals(behavior);
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /** Redes de segurança: spawn, saída e ligação porta↔terminal. */
    private static void finish(MapDocument doc, PrefabCatalog catalog,
                               Result result) {
        if (doc.firstMarker(LogicMarker.PLAYER_SPAWN) == null) {
            LogicMarker spawn = new LogicMarker(Ids.create(),
                    LogicMarker.PLAYER_SPAWN);
            doc.markers.add(spawn);
            warn(result, "resgate: faltou 'inicio'; jogador colocado em 0 0");
        }
        if (ObjectiveSpec.REACH_EXIT.equals(doc.objective.type)
                && doc.firstMarker(LogicMarker.EXIT) == null) {
            autoExit(doc, result);
        }
        // A IA erra coordenada: marcador dentro de parede é empurrado
        // para o espaço livre mais próximo em vez de perder a geração.
        nudgeFree(doc, doc.firstMarker(LogicMarker.PLAYER_SPAWN),
                "início", result);
        nudgeFree(doc, doc.firstMarker(LogicMarker.EXIT), "saída", result);
        // Portas precisam das três meias-medidas; completa o que faltou.
        for (PrefabInstance door : doc.prefabs) {
            boolean gate = "door.gate".equals(door.prefabId);
            if (!gate && !"door.auto".equals(door.prefabId)) continue;
            fillProperty(door, "halfX", gate ? 2.0f : 1.10f);
            fillProperty(door, "halfY", gate ? 1.4f : 1.05f);
            fillProperty(door, "halfZ", gate ? 0.18f : 0.08f);
        }
        // Consertos automáticos entram como "resgate:" para a prévia
        // agrupá-los em "Corrigidos pelo jogo", não em pontos de atenção.
        normalizeDoors(doc, result.warnings, "resgate: ");
        try {
            RuntimeLevel level = LevelCompiler.compile(doc, catalog);
            nudgeCompiled(doc, level.colliders(), result.warnings,
                    "resgate: ");
            settleEnemies(doc, catalog, level.colliders(), result.warnings,
                    "resgate: ");
        } catch (RuntimeException failure) {
            warn(result, "não foi possível conferir todas as colisões: "
                    + (failure.getMessage() == null
                    ? "mapa incompleto" : failure.getMessage()));
        }
    }

    /** Cada portão liga a um terminal DISTINTO, na ordem do roteiro. */
    private static int normalizeDoors(MapDocument doc, List<String> warnings,
                                      String prefix) {
        // Terminais ainda não reivindicados por um portão com ligação válida.
        List<PrefabInstance> unused = new ArrayList<>();
        for (PrefabInstance prefab : doc.prefabs) {
            if ("terminal.wall".equals(prefab.prefabId)) unused.add(prefab);
        }
        for (PrefabInstance door : doc.prefabs) {
            if (!"door.gate".equals(door.prefabId)) continue;
            PrefabInstance target = doc.findInstance(
                    door.stringProperty("controllerId"));
            if (target != null && "terminal.wall".equals(target.prefabId)) {
                unused.remove(target);
            }
        }
        int fixes = 0;
        for (PrefabInstance door : doc.prefabs) {
            boolean gate = "door.gate".equals(door.prefabId);
            if (!gate && !"door.auto".equals(door.prefabId)) continue;
            fillProperty(door, "halfX", gate ? 2.0f : 1.10f);
            fillProperty(door, "halfY", gate ? 1.4f : 1.05f);
            fillProperty(door, "halfZ", gate ? 0.18f : 0.08f);
            if (!gate) continue;
            String controller = door.stringProperty("controllerId");
            PrefabInstance target = doc.findInstance(controller);
            if (target != null && "terminal.wall".equals(target.prefabId)) {
                continue;
            }
            if (!unused.isEmpty()) {
                door.properties.put("controllerId", unused.remove(0).id);
                if (controller != null) {
                    warn(warnings, prefix
                            + "portão religado a um terminal existente");
                }
            } else {
                door.prefabId = "door.auto";
                door.properties.remove("controllerId");
                warn(warnings, prefix + "portão sem terminal livre virou "
                        + "porta automática");
            }
            fixes++;
        }
        return fixes;
    }

    /** Sem 'saida': nasce no ponto mais distante do spawn e é empurrada. */
    private static void autoExit(MapDocument doc, Result result) {
        LogicMarker exit = new LogicMarker(Ids.create(), LogicMarker.EXIT);
        LogicMarker spawn = doc.firstMarker(LogicMarker.PLAYER_SPAWN);
        float fromX = spawn == null ? 0f : spawn.x;
        float fromZ = spawn == null ? 0f : spawn.z;
        float best = -1f;
        for (StructureObject structure : doc.structures) {
            float dx = structure.transform.x - fromX;
            float dz = structure.transform.z - fromZ;
            float distance = dx * dx + dz * dz;
            if (distance > best) {
                best = distance;
                exit.x = structure.transform.x;
                exit.z = structure.transform.z;
            }
        }
        exit.radius = 1.35f;
        doc.markers.add(exit);
        warn(result, "resgate: faltou 'saida'; criada no ponto mais distante");
        nudgeFree(doc, exit, "saída", result);
    }

    /** Mesma checagem do MapValidator: cilindro do jogador × blocos. */
    private static boolean standsFree(MapDocument doc, float x, float y,
                                      float z) {
        float[] pos = {x, y, z};
        float[] bounds = new float[6];
        for (StructureObject s : doc.structures) {
            if (s.half == null
                    || !StructureObject.KIND_BLOCK.equals(s.kind)) continue;
            LegacyLevelLoader.toBounds(s.transform.x, s.transform.y,
                    s.transform.z, s.half[0], s.half[1], s.half[2], bounds);
            if (Collision.overlaps(pos, 0.35f, 1.75f, bounds)) return false;
        }
        return true;
    }

    private static boolean standsFree(float[][] colliders, float x, float y,
                                      float z) {
        float[] pos = {x, y, z};
        for (float[] bounds : colliders) {
            if (Collision.overlaps(pos, 0.35f, 1.75f, bounds)) return false;
        }
        return true;
    }

    private static int nudgeCompiled(MapDocument doc, float[][] colliders,
                                     List<String> warnings, String prefix) {
        int fixes = 0;
        for (LogicMarker marker : doc.markers) {
            if (!LogicMarker.PLAYER_SPAWN.equals(marker.type)
                    && !LogicMarker.EXIT.equals(marker.type)) continue;
            if (standsFree(colliders, marker.x, marker.y, marker.z)) continue;
            String label = LogicMarker.PLAYER_SPAWN.equals(marker.type)
                    ? "início" : "saída";
            if (nudgeFree(colliders, marker)) {
                warn(warnings, prefix + label + " estava dentro de uma peça; "
                        + "movido para lugar livre próximo");
                fixes++;
            } else {
                warn(warnings, prefix + label + " está sem espaço livre "
                        + "por perto; o validador pode recusar");
            }
        }
        return fixes;
    }

    /**
     * A IA escolhe o Y livremente; voador solto no céu ou torreta flutuando
     * são presos à altura do apoio local (chão, laje ou telhado). Mutante
     * tem gravidade e se corrige sozinho.
     */
    private static int settleEnemies(MapDocument doc, PrefabCatalog catalog,
                                     float[][] colliders,
                                     List<String> warnings, String prefix) {
        int fixes = 0;
        for (PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null) continue;
            boolean flyer = PrefabDefinition.BEHAVIOR_DRONE
                    .equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_DRONE_DORMANT
                    .equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_KAMIKAZE
                    .equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_BOSS.equals(def.behavior);
            boolean turret = PrefabDefinition.BEHAVIOR_TURRET
                    .equals(def.behavior);
            if (!flyer && !turret) continue;
            float support = supportBelow(colliders, p.transform.x,
                    p.transform.y, p.transform.z);
            float base = PrefabPlacementTool.defaultY(def);
            float y = p.transform.y;
            float fixed = y;
            if (flyer && (y < support + 0.9f || y > support + 3.4f)) {
                fixed = support + base;
            } else if (turret && Math.abs(y - (support + base)) > 0.9f) {
                fixed = support + base;
            }
            if (fixed != y) {
                p.transform.y = fixed;
                fixes++;
            }
        }
        if (fixes > 0) {
            warn(warnings, prefix + (fixes == 1
                    ? "1 inimigo estava em altura irreal; preso"
                    : fixes + " inimigos estavam em altura irreal; presos")
                    + " ao apoio mais próximo");
        }
        return fixes;
    }

    /** Topo do collider mais alto sob o ponto; sem apoio, chão em 0. */
    private static float supportBelow(float[][] colliders, float x, float y,
                                      float z) {
        float support = 0f;
        for (float[] box : colliders) {
            if (x < box[0] - 0.45f || x > box[3] + 0.45f
                    || z < box[2] - 0.45f || z > box[5] + 0.45f) continue;
            if (box[4] <= y + 0.3f && box[4] > support) {
                support = box[4];
            }
        }
        return support;
    }

    private static boolean nudgeFree(float[][] colliders,
                                     LogicMarker marker) {
        float originX = marker.x;
        float originZ = marker.z;
        for (float radius = 0.6f; radius <= 16f; radius += 0.6f) {
            for (int step = 0; step < 16; step++) {
                double angle = Math.PI * 2.0 * step / 16.0;
                float x = clamp(originX
                        + (float) (Math.cos(angle) * radius), -GRID, GRID);
                float z = clamp(originZ
                        + (float) (Math.sin(angle) * radius), -GRID, GRID);
                if (standsFree(colliders, x, marker.y, z)) {
                    marker.x = x;
                    marker.z = z;
                    return true;
                }
            }
        }
        return false;
    }

    private static void nudgeFree(MapDocument doc, LogicMarker marker,
                                  String label, Result result) {
        if (marker == null
                || standsFree(doc, marker.x, marker.y, marker.z)) return;
        for (float radius = 0.6f; radius <= 16f; radius += 0.6f) {
            for (int step = 0; step < 16; step++) {
                double angle = Math.PI * 2.0 * step / 16.0;
                float x = clamp(marker.x
                        + (float) (Math.cos(angle) * radius), -GRID, GRID);
                float z = clamp(marker.z
                        + (float) (Math.sin(angle) * radius), -GRID, GRID);
                if (standsFree(doc, x, marker.y, z)) {
                    marker.x = x;
                    marker.z = z;
                    warn(result, "resgate: " + label + " estava dentro de "
                            + "uma estrutura; movido para lugar livre "
                            + "próximo");
                    return;
                }
            }
        }
        warn(result, label + " está dentro de estrutura sem espaço livre "
                + "por perto; o validador pode recusar");
    }

    private static void fillProperty(PrefabInstance prefab, String name,
                                     float value) {
        if (prefab.properties.get(name) == null) {
            prefab.properties.put(name, value);
        }
    }

    private static void objective(MapDocument doc, String[] parts) {
        String type = oneOf(parts[1], ObjectiveSpec.REACH_EXIT,
                ObjectiveSpec.COLLECT, ObjectiveSpec.ELIMINATE_ALL,
                ObjectiveSpec.SURVIVE);
        doc.objective.type = type;
        if (ObjectiveSpec.COLLECT.equals(type)) {
            doc.objective.target = (int) clamp(number(parts[2]), 1f, 40f);
            doc.objective.timeLimitSeconds = parts.length > 3
                    ? clamp(number(parts[3]), 30f, 3600f) : 0f;
        } else if (ObjectiveSpec.SURVIVE.equals(type)) {
            doc.objective.durationSeconds =
                    clamp(number(parts[2]), 15f, 1200f);
        } else {
            doc.objective.timeLimitSeconds = parts.length > 2
                    ? clamp(number(parts[2]), 30f, 3600f) : 0f;
        }
        float limit = doc.objective.timeLimitSeconds;
        if (limit > 0f) {
            doc.objective.twoStarSeconds = limit * 0.78f;
            doc.objective.threeStarSeconds = limit * 0.58f;
        }
    }

    /** piso x z hx hz mat r g b [ytopo] — topo do piso no nível pedido. */
    private static void floor(MapDocument doc, String[] parts,
                              Result result) {
        requireRoom(doc, result);
        float top = parts.length > 9 ? height(number(parts[9])) : 0f;
        structure(doc, StructureObject.ROLE_FLOOR, parts[5],
                coord(number(parts[1])), top - 0.15f, coord(number(parts[2])),
                span(number(parts[3])), 0.15f, span(number(parts[4])),
                color(parts, 6));
    }

    /** teto x z hx hz ybase mat r g b — laje pisável por cima. */
    private static void ceiling(MapDocument doc, String[] parts,
                                Result result) {
        requireRoom(doc, result);
        structure(doc, StructureObject.ROLE_CEILING, parts[6],
                coord(number(parts[1])), height(number(parts[5])) + 0.15f,
                coord(number(parts[2])), span(number(parts[3])), 0.15f,
                span(number(parts[4])), color(parts, 7));
    }

    /** bloco x y z hx hy hz mat r g b — y é o CENTRO do bloco. */
    private static void box(MapDocument doc, String[] parts, Result result) {
        requireRoom(doc, result);
        structure(doc, StructureObject.ROLE_BLOCK, parts[7],
                coord(number(parts[1])), height(number(parts[2])),
                coord(number(parts[3])), span(number(parts[4])),
                clamp(number(parts[5]), 0.05f, 15f), span(number(parts[6])),
                color(parts, 8));
    }

    /** parede x1 z1 x2 z2 altura mat r g b [ybase] — diagonal vira poly. */
    private static StructureObject wall(MapDocument doc, String[] parts,
                                        Result result) {
        requireRoom(doc, result);
        float ax = coord(number(parts[1]));
        float az = coord(number(parts[2]));
        float bx = coord(number(parts[3]));
        float bz = coord(number(parts[4]));
        float tall = clamp(number(parts[5]), 0.5f, 12f);
        float base = parts.length > 10 ? height(number(parts[10])) : 0f;
        float[] paint = color(parts, 7);
        String material = parts[6];
        boolean straight = Math.abs(ax - bx) < 0.01f
                || Math.abs(az - bz) < 0.01f;
        if (straight) {
            float hx = Math.abs(bx - ax) / 2f;
            float hz = Math.abs(bz - az) / 2f;
            if (Math.max(hx, hz) < 0.15f) {
                throw new IllegalArgumentException("parede curta demais");
            }
            return structure(doc, StructureObject.ROLE_WALL, material,
                    (ax + bx) / 2f, base + tall / 2f, (az + bz) / 2f,
                    Math.max(hx, 0.15f), tall / 2f, Math.max(hz, 0.15f),
                    paint);
        }
        float length = (float) Math.hypot(bx - ax, bz - az);
        if (length < 0.4f) {
            throw new IllegalArgumentException("parede curta demais");
        }
        float nx = -(bz - az) / length * 0.15f;
        float nz = (bx - ax) / length * 0.15f;
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_POLY);
        value.role = StructureObject.ROLE_WALL;
        value.material = knownMaterial(material);
        value.transform.y = base + tall / 2f;
        value.half = new float[]{0f, tall / 2f, 0f};
        value.polygon = new float[]{ax + nx, az + nz, bx + nx, bz + nz,
                bx - nx, bz - nz, ax - nx, az - nz};
        value.color = paint;
        value.syncPolyBounds();
        doc.structures.add(value);
        return value;
    }

    /** vao porta|janela|portal [offset largura altura peitoril] */
    private static void opening(StructureObject wall, String[] parts,
                                Result result) {
        if (wall == null) {
            throw new IllegalArgumentException("nenhuma parede antes do vão");
        }
        if (StructureObject.KIND_POLY.equals(wall.kind)) {
            throw new IllegalArgumentException(
                    "parede diagonal não aceita vão");
        }
        String kind = parts[1].toLowerCase(Locale.ROOT);
        WallOpening value;
        if ("janela".equals(kind)) {
            value = new WallOpening(Ids.create(), WallOpening.WINDOW);
            value.width = 1.2f;
            value.height = 1.2f;
            value.sill = 0.9f;
        } else if ("portal".equals(kind)) {
            value = new WallOpening(Ids.create(), WallOpening.PORTAL);
            value.width = 1.6f;
            value.height = wall.half[1] * 2f;
        } else if ("porta".equals(kind)) {
            value = new WallOpening(Ids.create(), WallOpening.DOOR);
            value.width = 1.0f;
            value.height = 2.1f;
        } else {
            throw new IllegalArgumentException("tipo de vão desconhecido");
        }
        if (parts.length > 2) value.offset = number(parts[2]);
        if (parts.length > 3) {
            value.width = clamp(number(parts[3]), 0.5f, 6f);
        }
        if (parts.length > 4) {
            value.height = clamp(number(parts[4]), 0.5f,
                    wall.half[1] * 2f);
        }
        if (parts.length > 5) {
            value.sill = clamp(number(parts[5]), 0f, 2.5f);
        }
        // Prende o vão às medidas reais da parede (regras do validador).
        float halfLength = Math.max(wall.half[0], wall.half[2]);
        float wallHeight = wall.half[1] * 2f;
        value.width = Math.min(value.width,
                Math.max(0.5f, halfLength * 2f - 0.2f));
        float limit = halfLength - value.width / 2f - 0.02f;
        if (limit < 0f) {
            throw new IllegalArgumentException("vão maior que a parede");
        }
        value.offset = clamp(value.offset, -limit, limit);
        value.sill = Math.min(value.sill,
                Math.max(0f, wallHeight - value.height));
        value.height = Math.min(value.height, wallHeight - value.sill);
        for (WallOpening existing : wall.openings) {
            if (value.offset - value.width / 2f
                    < existing.offset + existing.width / 2f
                    && value.offset + value.width / 2f
                    > existing.offset - existing.width / 2f) {
                throw new IllegalArgumentException(
                        "vão sobreposto a outro na mesma parede");
            }
        }
        wall.openings.add(value);
    }

    private static PrefabInstance prefab(MapDocument doc, String[] parts,
                                         PrefabCatalog catalog,
                                         Result result) {
        if (doc.prefabs.size() >= MAX_PREFABS) {
            throw new LimitReached("limite de peças atingido");
        }
        String id = parts[1];
        if (catalog.find(id) == null) {
            throw new IllegalArgumentException("peça desconhecida: " + id);
        }
        PrefabInstance value = new PrefabInstance(Ids.create(), id);
        value.transform.x = coord(number(parts[2]));
        value.transform.y = height(number(parts[3]));
        value.transform.z = coord(number(parts[4]));
        if (parts.length > 5) {
            value.transform.yaw = number(parts[5]);
        }
        doc.prefabs.add(value);
        return value;
    }

    private static void numericProp(PrefabInstance prefab,
                                    PrefabDefinition def, String[] parts) {
        if (prefab == null || def == null) {
            throw new IllegalArgumentException("nenhuma peça antes de prop");
        }
        if (!NUMERIC_PROPS.contains(parts[1])
                || !def.allowsProperty(parts[1])) {
            // Mesma regra do validador: a peça diz o que aceita.
            throw new IllegalArgumentException(def.name
                    + " não aceita '" + parts[1] + "'");
        }
        prefab.properties.put(parts[1], number(parts[2]));
    }

    private static void textProp(PrefabInstance prefab,
                                 PrefabDefinition def, String[] parts,
                                 String line) {
        if (prefab == null || def == null) {
            throw new IllegalArgumentException("nenhuma peça antes de texto");
        }
        if ("combate".equalsIgnoreCase(parts[1])) {
            if (!PrefabDefinition.BEHAVIOR_NPC_HUMAN.equals(def.behavior)
                    || !def.allowsProperty("combatant")) {
                throw new IllegalArgumentException(def.name
                        + " não aceita configuração de combate");
            }
            String value = restAfter(line, parts[1])
                    .toLowerCase(Locale.ROOT);
            if ("sim".equals(value) || "true".equals(value)
                    || "yes".equals(value)) {
                prefab.properties.put("combatant", Boolean.TRUE);
            } else if ("nao".equals(value) || "não".equals(value)
                    || "false".equals(value) || "no".equals(value)) {
                prefab.properties.put("combatant", Boolean.FALSE);
            } else {
                throw new IllegalArgumentException(
                        "combate deve ser sim ou nao");
            }
            return;
        }
        if (!TEXT_PROPS.contains(parts[1])
                || !def.allowsProperty(parts[1])) {
            throw new IllegalArgumentException(def.name
                    + " não aceita texto '" + parts[1] + "'");
        }
        String value = clip(restAfter(line, parts[1]),
                textLimit(parts[1]));
        if (value.isEmpty()) {
            throw new IllegalArgumentException("texto vazio");
        }
        prefab.properties.put(parts[1], value);
    }

    private static int textLimit(String name) {
        if ("name".equals(name)) return 48;
        if ("role".equals(name)) return 80;
        if ("background".equals(name)) return 600;
        if (name != null && name.startsWith("combatLine")) return 120;
        return 240; // greeting
    }

    private static String restAfter(String line, String token) {
        int start = line.indexOf(token);
        return start < 0 ? "" : line.substring(start + token.length()).trim();
    }

    private static void patrol(PrefabInstance prefab, PrefabDefinition def,
                               String[] parts) {
        if (prefab == null || def == null) {
            throw new IllegalArgumentException(
                    "nenhuma peça antes de patrulha");
        }
        if (!def.allowsProperty("patrolX")) {
            throw new IllegalArgumentException(def.name
                    + " é fixa e não patrulha");
        }
        prefab.properties.put("patrolX", coord(number(parts[1])));
        prefab.properties.put("patrolZ", coord(number(parts[2])));
    }

    private static void marker(MapDocument doc, String type,
                               String[] parts) {
        LogicMarker value = new LogicMarker(Ids.create(), type);
        value.x = coord(number(parts[1]));
        value.z = coord(number(parts[2]));
        if (parts.length > 3) value.y = height(number(parts[3]));
        if (parts.length > 4) value.yaw = number(parts[4]);
        if (LogicMarker.EXIT.equals(type)) value.radius = 1.35f;
        doc.markers.removeIf(existing -> existing.type.equals(type));
        doc.markers.add(value);
    }

    private static StructureObject structure(MapDocument doc, String role,
                                             String material, float x,
                                             float y, float z, float hx,
                                             float hy, float hz,
                                             float[] paint) {
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        value.role = role;
        value.material = knownMaterial(material);
        value.transform.x = x;
        value.transform.y = y;
        value.transform.z = z;
        value.half = new float[]{hx, hy, hz};
        value.color = paint;
        doc.structures.add(value);
        return value;
    }

    private static void requireRoom(MapDocument doc, Result result) {
        if (doc.structures.size() >= MAX_STRUCTURES) {
            throw new LimitReached("limite de estruturas atingido");
        }
    }

    private static String knownMaterial(String value) {
        String material = value.toLowerCase(Locale.ROOT);
        for (String known : new String[]{"plain", "brick", "wood", "checker",
                "metal", "water", "lava", "asphalt"}) {
            if (known.equals(material)) return known;
        }
        throw new IllegalArgumentException("material desconhecido: " + value);
    }

    private static float[] color(String[] parts, int start) {
        return new float[]{clamp(number(parts[start]), 0f, 1f),
                clamp(number(parts[start + 1]), 0f, 1f),
                clamp(number(parts[start + 2]), 0f, 1f)};
    }

    private static String oneOf(String value, String... allowed) {
        String choice = value.toLowerCase(Locale.ROOT);
        for (String option : allowed) {
            if (option.equals(choice)) return option;
        }
        throw new IllegalArgumentException("valor desconhecido: " + value);
    }

    static float number(String token) {
        try {
            return Float.parseFloat(token.replace(',', '.'));
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("número inválido: " + token);
        }
    }

    private static float coord(float value) {
        return clamp(value, -GRID, GRID);
    }

    private static float height(float value) {
        return clamp(value, -6f, 30f);
    }

    private static float span(float value) {
        return clamp(value, 0.1f, GRID);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String rest(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1).trim();
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void warn(Result result, String message) {
        warn(result.warnings, message);
    }

    static void warn(List<String> warnings, String message) {
        if (warnings.size() < 40) warnings.add(message);
    }
}
