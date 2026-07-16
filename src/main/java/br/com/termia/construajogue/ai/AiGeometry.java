package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.util.Ids;

/**
 * Tijolos do construtor de cenários: estruturas, vãos, peças e paletas
 * de material/cor. Todas as receitas de topologia moram no
 * AiScenarioBuilder; aqui não há decisão de planta.
 */
final class AiGeometry {

    private AiGeometry() {
    }

    static final float[] CONCRETE = {0.46f, 0.49f, 0.54f};
    static final float[] DARK = {0.23f, 0.26f, 0.31f};
    static final float[] LIGHT = {0.68f, 0.70f, 0.73f};

    static String groundMaterial(AiScenarioPlan plan) {
        if (isUnderground(plan)) return "metal";
        if ("city".equals(plan.setting)) return "asphalt";
        if ("laboratory".equals(plan.setting)) return "checker";
        if ("industrial".equals(plan.setting)
                || "tunnel".equals(plan.setting)) return "metal";
        return "plain";
    }

    static float[] groundColor(AiScenarioPlan plan) {
        if (isUnderground(plan)) {
            return new float[]{0.20f, 0.23f, 0.25f};
        }
        if ("city".equals(plan.setting)) {
            return new float[]{0.20f, 0.22f, 0.25f};
        }
        if ("laboratory".equals(plan.setting)) {
            return new float[]{0.42f, 0.46f, 0.52f};
        }
        if ("tunnel".equals(plan.setting)) {
            return new float[]{0.20f, 0.23f, 0.25f};
        }
        return new float[]{0.31f, 0.34f, 0.38f};
    }

    static String wallMaterial(AiScenarioPlan plan) {
        if (isUnderground(plan)) return "metal";
        if ("industrial".equals(plan.setting)
                || "laboratory".equals(plan.setting)
                || "tunnel".equals(plan.setting)) return "metal";
        if ("city".equals(plan.setting)
                || "ruins".equals(plan.setting)) return "brick";
        return "plain";
    }

    static float[] wallColor(AiScenarioPlan plan) {
        if (isUnderground(plan)) {
            return new float[]{0.32f, 0.35f, 0.38f};
        }
        if ("laboratory".equals(plan.setting)) return LIGHT;
        if ("fortress".equals(plan.setting)) {
            return new float[]{0.36f, 0.38f, 0.42f};
        }
        if ("ruins".equals(plan.setting)) {
            return new float[]{0.45f, 0.38f, 0.31f};
        }
        if ("tunnel".equals(plan.setting)) {
            return new float[]{0.32f, 0.35f, 0.38f};
        }
        return CONCRETE;
    }

    static String buildingMaterial(AiScenarioPlan plan,
                                           AiScenarioPlan.Zone zone) {
        if ("laboratory".equals(zone.kind) || "warehouse".equals(zone.kind)
                || "station".equals(zone.kind)) return "metal";
        if ("house".equals(zone.kind) || "apartment".equals(zone.kind)
                || "shop".equals(zone.kind) || "ruins".equals(zone.kind)) {
            return "brick";
        }
        return wallMaterial(plan);
    }

    static float[] buildingColor(AiScenarioPlan plan,
                                         AiScenarioPlan.Zone zone) {
        if ("house".equals(zone.kind) || "shop".equals(zone.kind)) {
            return new float[]{0.64f, 0.52f, 0.40f};
        }
        if ("laboratory".equals(zone.kind)) return LIGHT;
        if ("warehouse".equals(zone.kind) || "station".equals(zone.kind)) {
            return new float[]{0.39f, 0.43f, 0.48f};
        }
        return wallColor(plan);
    }

    static String buildingFloorMaterial(AiScenarioPlan.Zone zone) {
        if ("house".equals(zone.kind) || "apartment".equals(zone.kind)
                || "shop".equals(zone.kind)) return "wood";
        if ("laboratory".equals(zone.kind)) return "checker";
        return "metal";
    }

    static void hazard(MapDocument doc, String material,
                               float x, float z) {
        block(doc, StructureObject.ROLE_FLOOR, material,
                x, -0.08f, z, 2.4f, 0.08f, 2.4f,
                "lava".equals(material)
                        ? new float[]{0.72f, 0.18f, 0.04f}
                        : new float[]{0.10f, 0.38f, 0.66f});
    }

    static float rowPosition(int index, int count, float half,
                                     float frontMargin, float backMargin) {
        float first = -half + frontMargin;
        float last = half - backMargin;
        return count <= 1 ? 0f
                : first + (last - first) * index / (count - 1f);
    }

    static StructureObject wall(MapDocument doc, float x, float y,
                                        float z, float hx, float hz,
                                        String material, float[] color,
                                        boolean roadFacing) {
        return block(doc, StructureObject.ROLE_WALL, material,
                x, y, z, hx, 1.5f, hz, color);
    }

    static StructureObject block(MapDocument doc, String role,
                                         String material, float x, float y,
                                         float z, float hx, float hy,
                                         float hz, float[] color) {
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        value.role = role;
        value.material = material;
        value.transform.x = x;
        value.transform.y = y;
        value.transform.z = z;
        value.half = new float[]{hx, hy, hz};
        value.color = color.clone();
        doc.structures.add(value);
        return value;
    }

    static WallOpening opening(float offset, float width,
                                       float height) {
        WallOpening opening = new WallOpening(Ids.create(), WallOpening.DOOR);
        opening.offset = offset;
        opening.width = width;
        opening.height = height;
        return opening;
    }

    static WallOpening windowOpening(float offset, float width) {
        WallOpening opening = new WallOpening(Ids.create(),
                WallOpening.WINDOW);
        opening.offset = offset;
        opening.width = width;
        opening.height = 1.05f;
        opening.sill = 1.05f;
        return opening;
    }

    static StructureObject diagonalWall(MapDocument doc,
                                                float ax, float az,
                                                float bx, float bz,
                                                String material,
                                                float[] color) {
        float length = (float) Math.hypot(bx - ax, bz - az);
        float nx = -(bz - az) / length * 0.15f;
        float nz = (bx - ax) / length * 0.15f;
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_POLY);
        value.role = StructureObject.ROLE_WALL;
        value.material = material;
        value.transform.y = 1.5f;
        value.half = new float[]{0f, 1.5f, 0f};
        value.polygon = new float[]{ax + nx, az + nz, bx + nx, bz + nz,
                bx - nx, bz - nz, ax - nx, az - nz};
        value.color = color.clone();
        value.syncPolyBounds();
        doc.structures.add(value);
        return value;
    }

    static PrefabInstance prefab(MapDocument doc, String prefabId,
                                         float x, float y, float z) {
        PrefabInstance value = new PrefabInstance(Ids.create(), prefabId);
        value.transform.x = x;
        value.transform.y = y;
        value.transform.z = z;
        doc.prefabs.add(value);
        return value;
    }

    static boolean isUnderground(AiScenarioPlan plan) {
        return "tunnel".equals(plan.setting)
                || "underground".equals(plan.layout);
    }
}
