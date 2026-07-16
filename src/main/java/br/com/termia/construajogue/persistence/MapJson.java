package br.com.termia.construajogue.persistence;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.Transform;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Converte MapDocument ↔ JSON schema atual (docs/FORMATO-MAPA.md). */
public final class MapJson {

    private MapJson() {
    }

    // ---- escrita ----

    public static String write(MapDocument doc) {
        Map<String, Object> root = new TreeMap<>();
        root.put("schema", MapDocument.SCHEMA);
        root.put("id", doc.id);
        root.put("name", doc.name);
        if (doc.objective != null && !isDefaultObjective(doc.objective)) {
            Map<String, Object> objective = new TreeMap<>();
            objective.put("type", doc.objective.type);
            if (doc.objective.target != 0) {
                objective.put("target", doc.objective.target);
            }
            if (doc.objective.durationSeconds != 0f) {
                objective.put("durationSeconds", doc.objective.durationSeconds);
            }
            if (doc.objective.timeLimitSeconds != 0f) {
                objective.put("timeLimitSeconds",
                        doc.objective.timeLimitSeconds);
            }
            if (doc.objective.twoStarSeconds != 0f) {
                objective.put("twoStarSeconds", doc.objective.twoStarSeconds);
            }
            if (doc.objective.threeStarSeconds != 0f) {
                objective.put("threeStarSeconds",
                        doc.objective.threeStarSeconds);
            }
            root.put("objective", objective);
        }
        Map<String, Object> env = new TreeMap<>();
        env.put("ambient", doc.ambient);
        env.put("fog", floats(doc.fogColor));
        env.put("fogFar", doc.fogFar);
        if (!"none".equals(doc.sky)) {
            env.put("sky", doc.sky);
        }
        if (!"auto".equals(doc.soundscape)) {
            env.put("soundscape", doc.soundscape);
        }
        root.put("environment", env);

        List<Object> structures = new ArrayList<>();
        for (StructureObject s : doc.structures) {
            Map<String, Object> item = new TreeMap<>();
            item.put("id", s.id);
            item.put("kind", s.kind);
            if (s.role != null) {
                item.put("role", s.role);
            }
            if (s.material != null && !"plain".equals(s.material)) {
                item.put("material", s.material);
            }
            if (s.locked) {
                item.put("locked", true);
            }
            item.put("transform", transform(s.transform));
            if (s.half != null) {
                item.put("half", floats(s.half));
            }
            if (s.color != null) {
                item.put("color", floats(s.color));
            }
            if (s.color2 != null) {
                item.put("color2", floats(s.color2));
            }
            if (s.color3 != null) {
                item.put("color3", floats(s.color3));
            }
            if (s.polygon != null) {
                item.put("polygon", floats(s.polygon));
            }
            if (!s.openings.isEmpty()) {
                List<Object> openings = new ArrayList<>();
                for (WallOpening o : s.openings) {
                    Map<String, Object> cut = new TreeMap<>();
                    cut.put("id", o.id);
                    cut.put("type", o.type);
                    cut.put("offset", o.offset);
                    cut.put("width", o.width);
                    cut.put("height", o.height);
                    if (o.sill != 0f) {
                        cut.put("sill", o.sill);
                    }
                    if (o.locked) {
                        cut.put("locked", true);
                    }
                    openings.add(cut);
                }
                item.put("openings", openings);
            }
            structures.add(item);
        }
        root.put("structures", structures);

        List<Object> prefabs = new ArrayList<>();
        for (PrefabInstance p : doc.prefabs) {
            Map<String, Object> item = new TreeMap<>();
            item.put("id", p.id);
            item.put("prefabId", p.prefabId);
            item.put("transform", transform(p.transform));
            if (p.scale != 1f) {
                item.put("scale", p.scale);
            }
            if (p.locked) {
                item.put("locked", true);
            }
            if (!p.properties.isEmpty()) {
                item.put("properties", new TreeMap<>(p.properties));
            }
            prefabs.add(item);
        }
        root.put("prefabs", prefabs);

        List<Object> markers = new ArrayList<>();
        for (LogicMarker m : doc.markers) {
            Map<String, Object> item = new TreeMap<>();
            item.put("id", m.id);
            item.put("type", m.type);
            item.put("x", m.x);
            item.put("y", m.y);
            item.put("z", m.z);
            if (m.yaw != 0f) {
                item.put("yaw", m.yaw);
            }
            if (m.radius != 0f) {
                item.put("radius", m.radius);
            }
            if (m.locked) {
                item.put("locked", true);
            }
            markers.add(item);
        }
        root.put("markers", markers);
        return Json.write(root);
    }

    private static Map<String, Object> transform(Transform t) {
        Map<String, Object> map = new TreeMap<>();
        map.put("x", t.x);
        map.put("y", t.y);
        map.put("z", t.z);
        if (t.yaw != 0f) {
            map.put("yaw", t.yaw);
        }
        return map;
    }

    private static List<Object> floats(float[] values) {
        List<Object> list = new ArrayList<>();
        for (float value : values) {
            list.add(value);
        }
        return list;
    }

    private static boolean isDefaultObjective(ObjectiveSpec objective) {
        return ObjectiveSpec.REACH_EXIT.equals(objective.type)
                && objective.target == 0
                && objective.durationSeconds == 0f
                && objective.timeLimitSeconds == 0f
                && objective.twoStarSeconds == 0f
                && objective.threeStarSeconds == 0f;
    }

    // ---- leitura ----

    public static MapDocument read(String text) {
        Map<?, ?> root = MapMigration.toCurrent(Json.parse(text));
        int schema = intOf(root, "schema", -1);
        if (schema != MapDocument.SCHEMA) {
            throw new IllegalArgumentException(
                    "schema " + schema + " não suportado");
        }
        MapDocument doc = new MapDocument();
        doc.id = stringOf(root, "id", null);
        doc.name = stringOf(root, "name", "");
        Object objective = root.get("objective");
        if (objective instanceof Map) {
            Map<?, ?> o = (Map<?, ?>) objective;
            doc.objective.type = stringOf(o, "type",
                    ObjectiveSpec.REACH_EXIT);
            doc.objective.target = intOf(o, "target", 0);
            doc.objective.durationSeconds = floatOf(o,
                    "durationSeconds", 0f);
            doc.objective.timeLimitSeconds = floatOf(o,
                    "timeLimitSeconds", 0f);
            doc.objective.twoStarSeconds = floatOf(o,
                    "twoStarSeconds", 0f);
            doc.objective.threeStarSeconds = floatOf(o,
                    "threeStarSeconds", 0f);
        }
        Object env = root.get("environment");
        if (env instanceof Map) {
            Map<?, ?> e = (Map<?, ?>) env;
            doc.ambient = floatOf(e, "ambient", doc.ambient);
            doc.fogFar = floatOf(e, "fogFar", doc.fogFar);
            doc.sky = stringOf(e, "sky", "none");
            doc.soundscape = stringOf(e, "soundscape", "auto");
            float[] fog = floatsOf(e.get("fog"), 3);
            if (fog != null) {
                doc.fogColor = fog;
            }
        }
        for (Object item : listOf(root.get("structures"))) {
            Map<?, ?> s = asMap(item, "structure");
            StructureObject structure = new StructureObject(
                    stringOf(s, "id", null), stringOf(s, "kind", ""));
            structure.role = stringOf(s, "role", null);
            structure.material = stringOf(s, "material", "plain");
            structure.locked = boolOf(s, "locked", false);
            structure.transform = transformOf(s.get("transform"));
            structure.half = floatsOf(s.get("half"), 3);
            structure.color = floatsOf(s.get("color"), 3);
            structure.color2 = floatsOf(s.get("color2"), 3);
            structure.color3 = floatsOf(s.get("color3"), 3);
            Object polygon = s.get("polygon");
            if (polygon instanceof List) {
                List<?> list = (List<?>) polygon;
                if (list.size() < 6 || (list.size() & 1) != 0) {
                    throw new IllegalArgumentException(
                            "polygon precisa de pares X,Z (3+ pontos)");
                }
                structure.polygon = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    structure.polygon[i] =
                            ((Json.Num) list.get(i)).floatValue();
                }
                structure.syncPolyBounds();
            }
            for (Object cut : listOf(s.get("openings"))) {
                Map<?, ?> c = asMap(cut, "opening");
                WallOpening opening = new WallOpening(
                        stringOf(c, "id", null), stringOf(c, "type", ""));
                opening.offset = floatOf(c, "offset", 0f);
                opening.width = floatOf(c, "width", 0f);
                opening.height = floatOf(c, "height", 0f);
                opening.sill = floatOf(c, "sill", 0f);
                opening.locked = boolOf(c, "locked", false);
                structure.openings.add(opening);
            }
            doc.structures.add(structure);
        }
        for (Object item : listOf(root.get("prefabs"))) {
            Map<?, ?> p = asMap(item, "prefab");
            PrefabInstance instance = new PrefabInstance(
                    stringOf(p, "id", null), stringOf(p, "prefabId", ""));
            instance.transform = transformOf(p.get("transform"));
            instance.scale = floatOf(p, "scale", 1f);
            instance.locked = boolOf(p, "locked", false);
            Object props = p.get("properties");
            if (props instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) props).entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Json.Num) {
                        value = ((Json.Num) value).floatValue();
                    }
                    instance.properties.put(entry.getKey().toString(), value);
                }
            }
            doc.prefabs.add(instance);
        }
        for (Object item : listOf(root.get("markers"))) {
            Map<?, ?> m = asMap(item, "marker");
            LogicMarker marker = new LogicMarker(
                    stringOf(m, "id", null), stringOf(m, "type", ""));
            marker.x = floatOf(m, "x", 0f);
            marker.y = floatOf(m, "y", 0f);
            marker.z = floatOf(m, "z", 0f);
            marker.yaw = floatOf(m, "yaw", 0f);
            marker.radius = floatOf(m, "radius", 0f);
            marker.locked = boolOf(m, "locked", false);
            doc.markers.add(marker);
        }
        return doc;
    }

    private static Transform transformOf(Object value) {
        Transform t = new Transform();
        if (value instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) value;
            t.x = floatOf(m, "x", 0f);
            t.y = floatOf(m, "y", 0f);
            t.z = floatOf(m, "z", 0f);
            t.yaw = floatOf(m, "yaw", 0f);
        }
        return t;
    }

    private static List<?> listOf(Object value) {
        return value instanceof List ? (List<?>) value : new ArrayList<>();
    }

    private static Map<?, ?> asMap(Object value, String what) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(what + ": objeto esperado");
        }
        return (Map<?, ?>) value;
    }

    private static float[] floatsOf(Object value, int count) {
        if (!(value instanceof List)) {
            return null;
        }
        List<?> list = (List<?>) value;
        if (list.size() != count) {
            throw new IllegalArgumentException(
                    "lista de " + count + " números esperada");
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = ((Json.Num) list.get(i)).floatValue();
        }
        return out;
    }

    private static float floatOf(Map<?, ?> map, String key, float fallback) {
        Object value = map.get(key);
        return value instanceof Json.Num
                ? ((Json.Num) value).floatValue() : fallback;
    }

    private static int intOf(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Json.Num
                ? ((Json.Num) value).intValue() : fallback;
    }

    private static String stringOf(Map<?, ?> map, String key,
                                   String fallback) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static boolean boolOf(Map<?, ?> map, String key,
                                  boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
