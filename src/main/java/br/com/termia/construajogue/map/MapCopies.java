package br.com.termia.construajogue.map;

import br.com.termia.construajogue.util.Ids;

import java.util.Map;

/** Cópias profundas de elementos editáveis, sempre com IDs novos. */
public final class MapCopies {

    private MapCopies() {
    }

    public static StructureObject structure(StructureObject source,
                                            float dx, float dz) {
        StructureObject copy = new StructureObject(Ids.create(), source.kind);
        copy.role = source.role;
        copy.material = source.material;
        copy.transform = transform(source.transform, dx, dz);
        copy.half = clone(source.half);
        copy.color = clone(source.color);
        copy.color2 = clone(source.color2);
        copy.color3 = clone(source.color3);
        copy.polygon = clone(source.polygon);
        copy.locked = source.locked;
        if (copy.polygon != null) {
            for (int i = 0; i < copy.polygon.length; i += 2) {
                copy.polygon[i] += dx;
                copy.polygon[i + 1] += dz;
            }
            copy.syncPolyBounds();
        }
        for (WallOpening opening : source.openings) {
            copy.openings.add(opening(opening));
        }
        return copy;
    }

    public static PrefabInstance prefab(PrefabInstance source,
                                        float dx, float dz) {
        PrefabInstance copy = new PrefabInstance(Ids.create(), source.prefabId);
        copy.transform = transform(source.transform, dx, dz);
        copy.scale = source.scale;
        copy.locked = source.locked;
        for (Map.Entry<String, Object> entry : source.properties.entrySet()) {
            copy.properties.put(entry.getKey(), entry.getValue());
        }
        if (copy.properties.containsKey("patrolX")) {
            copy.properties.put("patrolX",
                    copy.floatProperty("patrolX", source.transform.x) + dx);
        }
        if (copy.properties.containsKey("patrolZ")) {
            copy.properties.put("patrolZ",
                    copy.floatProperty("patrolZ", source.transform.z) + dz);
        }
        return copy;
    }

    public static LogicMarker marker(LogicMarker source, float dx, float dz) {
        LogicMarker copy = new LogicMarker(Ids.create(), source.type);
        copy.x = source.x + dx;
        copy.y = source.y;
        copy.z = source.z + dz;
        copy.yaw = source.yaw;
        copy.radius = source.radius;
        copy.locked = source.locked;
        return copy;
    }

    public static WallOpening opening(WallOpening source) {
        WallOpening copy = new WallOpening(Ids.create(), source.type);
        copy.offset = source.offset;
        copy.width = source.width;
        copy.height = source.height;
        copy.sill = source.sill;
        copy.locked = source.locked;
        return copy;
    }

    private static Transform transform(Transform source, float dx, float dz) {
        Transform copy = new Transform();
        copy.x = source.x + dx;
        copy.y = source.y;
        copy.z = source.z + dz;
        copy.yaw = source.yaw;
        return copy;
    }

    private static float[] clone(float[] source) {
        return source == null ? null : source.clone();
    }
}
