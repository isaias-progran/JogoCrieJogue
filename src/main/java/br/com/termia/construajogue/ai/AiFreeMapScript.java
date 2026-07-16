package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;
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
            "name", "role", "greeting", "background");

    private AiFreeMapScript() {
    }

    public static final class Result {
        public final MapDocument document;
        public final List<String> warnings = new ArrayList<>();

        Result(MapDocument document) {
            this.document = document;
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
        StructureObject lastWall = null;
        PrefabInstance lastPrefab = null;
        String[] lines = script.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")
                    || line.startsWith("```")) continue;
            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase(Locale.ROOT);
            try {
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
                    lastWall = wall(doc, parts, result);
                } else if ("vao".equals(command)) {
                    opening(lastWall, parts, result);
                } else if ("bloco".equals(command)) {
                    box(doc, parts, result);
                } else if ("peca".equals(command)) {
                    PrefabInstance placed =
                            prefab(doc, parts, catalog, result);
                    if (placed != null) lastPrefab = placed;
                } else if ("prop".equals(command)) {
                    numericProp(lastPrefab, parts, result);
                } else if ("texto".equals(command)) {
                    textProp(lastPrefab, parts, line, result);
                } else if ("patrulha".equals(command)) {
                    patrol(lastPrefab, parts, result);
                } else if ("inicio".equals(command)) {
                    marker(doc, LogicMarker.PLAYER_SPAWN, parts);
                } else if ("saida".equals(command)) {
                    marker(doc, LogicMarker.EXIT, parts);
                } else {
                    warn(result, "linha " + (i + 1)
                            + ": comando desconhecido '" + command + "'");
                }
            } catch (RuntimeException bad) {
                warn(result, "linha " + (i + 1) + " ignorada ("
                        + command + "): " + bad.getMessage());
            }
        }
        finish(doc, result);
        return result;
    }

    /** Redes de segurança: spawn, saída e ligação porta↔terminal. */
    private static void finish(MapDocument doc, Result result) {
        if (doc.firstMarker(LogicMarker.PLAYER_SPAWN) == null) {
            LogicMarker spawn = new LogicMarker(Ids.create(),
                    LogicMarker.PLAYER_SPAWN);
            doc.markers.add(spawn);
            warn(result, "faltou 'inicio'; jogador colocado em 0 0");
        }
        if (ObjectiveSpec.REACH_EXIT.equals(doc.objective.type)
                && doc.firstMarker(LogicMarker.EXIT) == null) {
            LogicMarker exit = new LogicMarker(Ids.create(),
                    LogicMarker.EXIT);
            LogicMarker spawn = doc.firstMarker(LogicMarker.PLAYER_SPAWN);
            float best = -1f;
            for (StructureObject structure : doc.structures) {
                float dx = structure.transform.x - spawn.x;
                float dz = structure.transform.z - spawn.z;
                float distance = dx * dx + dz * dz;
                if (distance > best) {
                    best = distance;
                    exit.x = structure.transform.x;
                    exit.z = structure.transform.z;
                }
            }
            exit.radius = 1.35f;
            doc.markers.add(exit);
            warn(result, "faltou 'saida'; criada no ponto mais distante");
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
        // Convenção do editor: portão sem controlador liga ao terminal livre.
        List<String> linked = new ArrayList<>();
        for (PrefabInstance door : doc.prefabs) {
            if (!"door.gate".equals(door.prefabId)
                    || door.properties.get("controllerId") != null) continue;
            for (PrefabInstance terminal : doc.prefabs) {
                if ("terminal.wall".equals(terminal.prefabId)
                        && !linked.contains(terminal.id)) {
                    door.properties.put("controllerId", terminal.id);
                    linked.add(terminal.id);
                    break;
                }
            }
        }
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
                    warn(result, label + " estava dentro de uma estrutura; "
                            + "movido para lugar livre próximo");
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
            throw new IllegalArgumentException("limite de peças atingido");
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

    private static void numericProp(PrefabInstance prefab, String[] parts,
                                    Result result) {
        if (prefab == null) {
            throw new IllegalArgumentException("nenhuma peça antes de prop");
        }
        if (!NUMERIC_PROPS.contains(parts[1])) {
            throw new IllegalArgumentException(
                    "propriedade desconhecida: " + parts[1]);
        }
        prefab.properties.put(parts[1], number(parts[2]));
    }

    private static void textProp(PrefabInstance prefab, String[] parts,
                                 String line, Result result) {
        if (prefab == null) {
            throw new IllegalArgumentException("nenhuma peça antes de texto");
        }
        if (!TEXT_PROPS.contains(parts[1])) {
            throw new IllegalArgumentException(
                    "texto desconhecido: " + parts[1]);
        }
        int start = line.indexOf(parts[1]) + parts[1].length();
        String value = clip(line.substring(start).trim(),
                "background".equals(parts[1]) ? 600 : 240);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("texto vazio");
        }
        prefab.properties.put(parts[1], value);
    }

    private static void patrol(PrefabInstance prefab, String[] parts,
                               Result result) {
        if (prefab == null) {
            throw new IllegalArgumentException(
                    "nenhuma peça antes de patrulha");
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
            throw new IllegalArgumentException(
                    "limite de estruturas atingido");
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

    private static float number(String token) {
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
        if (result.warnings.size() < 40) result.warnings.add(message);
    }
}
