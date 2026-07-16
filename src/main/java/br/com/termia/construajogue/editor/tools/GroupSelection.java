package br.com.termia.construajogue.editor.tools;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapCopies;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado e operações da seleção retangular do editor. Mantê-la fora da
 * View evita misturar regras de documento com desenho/toque.
 */
public final class GroupSelection {

    private final List<StructureObject> structures = new ArrayList<>();
    private final List<PrefabInstance> prefabs = new ArrayList<>();
    private final List<LogicMarker> markers = new ArrayList<>();

    public void clear() {
        structures.clear();
        prefabs.clear();
        markers.clear();
    }

    public boolean isEmpty() {
        return structures.isEmpty() && prefabs.isEmpty() && markers.isEmpty();
    }

    public int size() {
        return structures.size() + prefabs.size() + markers.size();
    }

    public void select(MapDocument doc, float x0, float z0,
                       float x1, float z1) {
        select(doc, x0, z0, x1, z1, Float.NaN);
    }

    /** Seleciona só objetos do andar ativo; NaN mantém o modo legado. */
    public void select(MapDocument doc, float x0, float z0,
                       float x1, float z1, float activeBaseY) {
        clear();
        float minX = Math.min(x0, x1);
        float minZ = Math.min(z0, z1);
        float maxX = Math.max(x0, x1);
        float maxZ = Math.max(z0, z1);
        List<Float> levels = Float.isNaN(activeBaseY)
                ? null : StoryLevels.discover(doc);
        for (StructureObject s : doc.structures) {
            if (!Float.isNaN(activeBaseY)
                    && !StoryLevels.belongs(s, activeBaseY, levels)) continue;
            if (intersects(minX, minZ, maxX, maxZ,
                    s.transform.x - s.half[0],
                    s.transform.z - s.half[2],
                    s.transform.x + s.half[0],
                    s.transform.z + s.half[2])) {
                structures.add(s);
            }
        }
        for (PrefabInstance p : doc.prefabs) {
            if (!Float.isNaN(activeBaseY)
                    && !StoryLevels.belongs(p, activeBaseY, levels)) continue;
            if (inside(p.transform.x, p.transform.z,
                    minX, minZ, maxX, maxZ)) {
                prefabs.add(p);
            }
        }
        for (LogicMarker m : doc.markers) {
            if (!Float.isNaN(activeBaseY)
                    && !StoryLevels.belongs(m, activeBaseY)) continue;
            if (inside(m.x, m.z, minX, minZ, maxX, maxZ)) {
                markers.add(m);
            }
        }
    }

    private static boolean inside(float x, float z, float minX, float minZ,
                                  float maxX, float maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static boolean intersects(float aMinX, float aMinZ,
                                      float aMaxX, float aMaxZ,
                                      float bMinX, float bMinZ,
                                      float bMaxX, float bMaxZ) {
        return aMaxX >= bMinX && aMinX <= bMaxX
                && aMaxZ >= bMinZ && aMinZ <= bMaxZ;
    }

    public boolean contains(StructureObject value) {
        return structures.contains(value);
    }

    public boolean contains(PrefabInstance value) {
        return prefabs.contains(value);
    }

    public boolean contains(LogicMarker value) {
        return markers.contains(value);
    }

    public boolean containsPoint(float x, float z) {
        float[] b = bounds();
        return b != null && inside(x, z, b[0], b[1], b[2], b[3]);
    }

    public boolean anyLocked() {
        for (StructureObject s : structures) if (s.locked) return true;
        for (PrefabInstance p : prefabs) if (p.locked) return true;
        for (LogicMarker m : markers) if (m.locked) return true;
        return false;
    }

    public boolean allLocked() {
        if (isEmpty()) return false;
        for (StructureObject s : structures) if (!s.locked) return false;
        for (PrefabInstance p : prefabs) if (!p.locked) return false;
        for (LogicMarker m : markers) if (!m.locked) return false;
        return true;
    }

    public void toggleLocks() {
        boolean value = !allLocked();
        for (StructureObject s : structures) s.locked = value;
        for (PrefabInstance p : prefabs) p.locked = value;
        for (LogicMarker m : markers) m.locked = value;
    }

    public void translate(float dx, float dz) {
        for (StructureObject s : structures) {
            s.transform.x += dx;
            s.transform.z += dz;
            if (s.polygon != null) {
                for (int i = 0; i < s.polygon.length; i += 2) {
                    s.polygon[i] += dx;
                    s.polygon[i + 1] += dz;
                }
            }
        }
        for (PrefabInstance p : prefabs) {
            p.transform.x += dx;
            p.transform.z += dz;
            if (p.properties.containsKey("patrolX")) {
                p.properties.put("patrolX",
                        p.floatProperty("patrolX", p.transform.x) + dx);
            }
            if (p.properties.containsKey("patrolZ")) {
                p.properties.put("patrolZ",
                        p.floatProperty("patrolZ", p.transform.z) + dz);
            }
        }
        for (LogicMarker m : markers) {
            m.x += dx;
            m.z += dz;
        }
    }

    /** Duplica estruturas/peças; início e saída continuam únicos. */
    public int duplicateInto(MapDocument doc, float dx, float dz) {
        List<StructureObject> newStructures = new ArrayList<>();
        List<PrefabInstance> newPrefabs = new ArrayList<>();
        Map<String, String> newIds = new HashMap<>();
        for (StructureObject s : structures) {
            StructureObject copy = MapCopies.structure(s, dx, dz);
            doc.structures.add(copy);
            newStructures.add(copy);
        }
        for (PrefabInstance p : prefabs) {
            PrefabInstance copy = MapCopies.prefab(p, dx, dz);
            doc.prefabs.add(copy);
            newPrefabs.add(copy);
            newIds.put(p.id, copy.id);
        }
        for (PrefabInstance copy : newPrefabs) {
            Object controller = copy.properties.get("controllerId");
            String replacement = controller == null ? null
                    : newIds.get(String.valueOf(controller));
            if (replacement != null) {
                copy.properties.put("controllerId", replacement);
            }
        }
        clear();
        structures.addAll(newStructures);
        prefabs.addAll(newPrefabs);
        return size();
    }

    public void deleteFrom(MapDocument doc) {
        doc.structures.removeAll(structures);
        doc.prefabs.removeAll(prefabs);
        doc.markers.removeAll(markers);
        clear();
    }

    /** minX,minZ,maxX,maxZ, ou null se vazia. */
    public float[] bounds() {
        if (isEmpty()) return null;
        float minX = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (StructureObject s : structures) {
            minX = Math.min(minX, s.transform.x - s.half[0]);
            minZ = Math.min(minZ, s.transform.z - s.half[2]);
            maxX = Math.max(maxX, s.transform.x + s.half[0]);
            maxZ = Math.max(maxZ, s.transform.z + s.half[2]);
        }
        for (PrefabInstance p : prefabs) {
            minX = Math.min(minX, p.transform.x - 0.4f);
            minZ = Math.min(minZ, p.transform.z - 0.4f);
            maxX = Math.max(maxX, p.transform.x + 0.4f);
            maxZ = Math.max(maxZ, p.transform.z + 0.4f);
        }
        for (LogicMarker m : markers) {
            float r = Math.max(0.35f, m.radius);
            minX = Math.min(minX, m.x - r);
            minZ = Math.min(minZ, m.z - r);
            maxX = Math.max(maxX, m.x + r);
            maxZ = Math.max(maxZ, m.z + r);
        }
        return new float[]{minX, minZ, maxX, maxZ};
    }
}
