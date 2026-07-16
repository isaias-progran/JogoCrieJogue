package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import java.util.Random;

/**
 * Receitas focais: a casa/prédio de 1-3 pavimentos com escada, janelas,
 * mobília e luzes, e o túnel coberto. Também é a dona do dimensionamento
 * da casa (houseHalfX/Z, effectiveFloors), que o dispatch consulta.
 */
final class AiFocalRecipes {

    private AiFocalRecipes() {
    }

    /** Casa/prédio com 1–3 pavimentos, laje vazada e circulação vertical. */
    static void buildFocalBuilding(MapDocument doc,
                                           AiScenarioPlan plan,
                                           AiScenarioProfile profile,
                                           AiScenarioPlan.Zone zone) {
        float hx = houseHalfX(plan, profile);
        float hz = houseHalfZ(plan, profile);
        int floors = effectiveFloors(plan);
        String material = AiGeometry.buildingMaterial(plan, zone);
        float[] color = AiGeometry.buildingColor(plan, zone);
        String floorMaterial = AiGeometry.buildingFloorMaterial(zone);
        float[] floorColor = "wood".equals(floorMaterial)
                ? new float[]{0.48f, 0.35f, 0.24f}
                : "checker".equals(floorMaterial)
                ? new float[]{0.48f, 0.52f, 0.58f}
                : new float[]{0.31f, 0.35f, 0.40f};
        String accessId = plan.hasFeature("ramps")
                && !plan.hasFeature("stairs") ? "ramp.floor" : "stairs.floor";
        float accessLength = "ramp.floor".equals(accessId) ? 6f : 3.6f;

        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, floorMaterial,
                0f, 0.025f, 0f, hx - 0.18f, 0.025f, hz - 0.18f,
                floorColor);
        for (int level = 0; level < floors; level++) {
            float baseY = level * 3.3f;
            if (level > 0) {
                upperFloor(doc, floorMaterial, floorColor, baseY,
                        hx, hz, accessLength);
            }
            addStoryShell(doc, plan, material, color, baseY, hx, hz,
                    level);
            addStoryPartitions(doc, plan, material, color, baseY, hx, hz,
                    level);
            if (level + 1 < floors) {
                PrefabInstance access = AiGeometry.prefab(doc, accessId,
                        0f, baseY, 1f);
                access.transform.yaw = 180f;
            }
            if (plan.hasFeature("furniture")) {
                furnishStory(doc, zone.kind, level, baseY, hx, hz);
            }
            if (plan.hasFeature("indoor_lights")) {
                indoorLights(doc, level, baseY, hx, hz);
            }
        }
        addBuildingRoof(doc, plan, material, color, floorMaterial,
                floorColor, floors, hx, hz, accessId, accessLength);
    }

    static void upperFloor(MapDocument doc, String material,
                                   float[] color, float baseY,
                                   float hx, float hz, float accessLength) {
        float openingHalfX = 0.78f;
        float sideHalfX = (hx - openingHalfX) * 0.5f;
        float sideX = (hx + openingHalfX) * 0.5f;
        float slabY = baseY - 0.15f;
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, material,
                -sideX, slabY, 0f, sideHalfX, 0.15f, hz - 0.18f, color);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, material,
                sideX, slabY, 0f, sideHalfX, 0.15f, hz - 0.18f, color);
        float highEdge = 1f - accessLength * 0.5f;
        float rearHalfZ = (highEdge + hz - 0.18f) * 0.5f;
        float rearZ = -hz + 0.18f + rearHalfZ;
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, material,
                0f, slabY, rearZ, openingHalfX, 0.15f, rearHalfZ, color);
    }

    static void furnishStory(MapDocument doc, String kind,
                                     int level, float baseY,
                                     float hx, float hz) {
        float x = Math.min(3.1f, hx * 0.56f);
        float z = Math.min(3.2f, hz * 0.48f);
        if ("laboratory".equals(kind) || "warehouse".equals(kind)
                || "station".equals(kind)) {
            AiGeometry.prefab(doc, "furniture.workbench", -x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.shelf", x, baseY, -z);
            AiGeometry.prefab(doc, level == 0 ? "obstacle.barrel"
                    : "furniture.cabinet", x, baseY, z);
        } else if ("shop".equals(kind)) {
            AiGeometry.prefab(doc, "furniture.shelf", -x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.shelf", x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.table", 0f, baseY, z * 0.5f);
            AiGeometry.prefab(doc, "furniture.cabinet", -x, baseY, z);
        } else if ("park".equals(kind) || "plaza".equals(kind)
                || "courtyard".equals(kind)) {
            AiGeometry.prefab(doc, "prop.plant.tall", -x, baseY, -z);
            AiGeometry.prefab(doc, "prop.plant.tall", x, baseY, z);
            AiGeometry.prefab(doc, "furniture.chair", -x * 0.5f, baseY, z);
            AiGeometry.prefab(doc, "furniture.chair", x * 0.5f, baseY, -z);
        } else if (("apartment".equals(kind) || "tower".equals(kind))
                && level == 0) {
            PrefabInstance sofa = AiGeometry.prefab(doc, "furniture.sofa",
                    -x, baseY, -z);
            sofa.transform.yaw = 90f;
            AiGeometry.prefab(doc, "prop.tv", -x, baseY, z);
            AiGeometry.prefab(doc, "furniture.table", x, baseY, -z);
            AiGeometry.prefab(doc, "prop.plant.small", x, baseY, z);
        } else if (("apartment".equals(kind) || "tower".equals(kind))
                && level == 1) {
            AiGeometry.prefab(doc, "furniture.bed", -x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.wardrobe", x, baseY, -z);
            AiGeometry.prefab(doc, "prop.mirror.round", x, baseY, z);
            AiGeometry.prefab(doc, "furniture.sink.bath", -x, baseY, z);
        } else if (level == 0) {
            PrefabInstance sofa = AiGeometry.prefab(doc, "furniture.sofa",
                    -x, baseY, -z);
            sofa.transform.yaw = 90f;
            AiGeometry.prefab(doc, "furniture.table", x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.sink.kitchen", -x, baseY, z);
            AiGeometry.prefab(doc, "prop.plant.tall", x, baseY, z);
        } else if (level == 1) {
            AiGeometry.prefab(doc, "furniture.bed", -x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.wardrobe", x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.toilet", x, baseY, z);
            AiGeometry.prefab(doc, "furniture.sink.bath", -x, baseY, z);
        } else {
            AiGeometry.prefab(doc, "furniture.workbench", -x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.shelf", x, baseY, -z);
            AiGeometry.prefab(doc, "furniture.chair", -x, baseY, z);
            AiGeometry.prefab(doc, "prop.plant.small", x, baseY, z);
        }
    }

    static void indoorLights(MapDocument doc, int level,
                                     float baseY, float hx, float hz) {
        float x = Math.min(2.6f, hx * 0.42f);
        for (int side = -1; side <= 1; side += 2) {
            PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.ceiling",
                    side * x, baseY + 3f, -hz * 0.12f);
            lamp.properties.put("lightR", level == 0 ? 1f : 0.78f);
            lamp.properties.put("lightG", level == 0 ? 0.78f : 0.86f);
            lamp.properties.put("lightB", level == 0 ? 0.56f : 1f);
            lamp.properties.put("lightRadius", 6f);
        }
    }

    static int effectiveFloors(AiScenarioPlan plan) {
        int floors = Math.max(plan.floors, plan.primaryZone().floors);
        if (plan.hasFeature("second_floor")) floors = Math.max(2, floors);
        return Math.min(3, Math.max(1, floors));
    }

    static float houseHalfX(AiScenarioPlan plan,
                                    AiScenarioProfile profile) {
        float base = "huge".equals(profile.sectorSize()) ? 9f
                : "large".equals(profile.sectorSize()) ? 7.8f
                : "medium".equals(profile.sectorSize()) ? 6.5f : 5.3f;
        if ("large".equals(plan.primaryZone().size)) base += 0.8f;
        if ("small".equals(plan.primaryZone().size)) base -= 0.5f;
        return Math.min(profile.halfSize() - 3.2f, base);
    }

    static float houseHalfZ(AiScenarioPlan plan,
                                    AiScenarioProfile profile) {
        float base = "huge".equals(profile.sectorSize()) ? 10.5f
                : "large".equals(profile.sectorSize()) ? 9f
                : "medium".equals(profile.sectorSize()) ? 7.8f : 6.3f;
        if ("large".equals(plan.primaryZone().size)) base += 0.8f;
        if ("small".equals(plan.primaryZone().size)) base -= 0.4f;
        return Math.min(profile.halfSize() - 3.5f, base);
    }

    /** Teto único cobre toda a área jogável, inclusive entradas e salas. */
    static void buildTunnel(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile,
                                    Random random) {
        float half = profile.halfSize();
        float corridor = Math.min(5.2f, half * 0.42f);
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "metal",
                0f, 3.15f, 0f, half, 0.15f, half,
                new float[]{0.25f, 0.28f, 0.30f});

        StructureObject left = AiGeometry.wall(doc, -corridor, 1.5f, 0f,
                0.15f, half - 1f, "metal", AiGeometry.wallColor(plan), false);
        StructureObject right = AiGeometry.wall(doc, corridor, 1.5f, 0f,
                0.15f, half - 1f, "metal", AiGeometry.wallColor(plan), false);
        for (int i = 0; i < profile.rows(); i++) {
            float offset = AiGeometry.rowPosition(i, profile.rows(), half, 5.5f, 6.5f);
            if ((i & 1) == 0) left.openings.add(
                    AiGeometry.opening(offset, 2.2f, 2.25f));
            else right.openings.add(AiGeometry.opening(offset, 2.2f, 2.25f));
        }

        int bulkheads = Math.max(1, profile.rows() - 1);
        for (int i = 0; i < bulkheads; i++) {
            float z = AiGeometry.rowPosition(i, bulkheads, half, 9f, 10f);
            StructureObject rib = AiGeometry.wall(doc, 0f, 1.5f, z,
                    corridor, 0.16f, "metal", AiGeometry.DARK, true);
            rib.openings.add(AiGeometry.opening(0f, 3.1f, 2.4f));
        }

        int lights = profile.rows() * 2 + 2;
        for (int i = 0; i < lights; i++) {
            float z = -half + 3.2f + (half * 2f - 6.4f)
                    * i / Math.max(1f, lights - 1f);
            PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.ceiling",
                    (i & 1) == 0 ? -1.65f : 1.65f, 2.92f, z);
            lamp.properties.put("lightR", 0.62f);
            lamp.properties.put("lightG", 0.78f
                    + random.nextFloat() * 0.12f);
            lamp.properties.put("lightB", 0.90f);
            lamp.properties.put("lightRadius", 6.4f);
        }
        for (int i = 0; i < profile.rows(); i++) {
            float z = AiGeometry.rowPosition(i, profile.rows(), half, 6f, 7f);
            AiGeometry.prefab(doc, (i & 1) == 0 ? "obstacle.crate.small"
                            : "obstacle.crate.large",
                    (i & 1) == 0 ? -corridor - 2f : corridor + 2f,
                    0f, z);
        }
    }

    static void addStoryShell(MapDocument doc, AiScenarioPlan plan,
                                      String material, float[] color,
                                      float baseY, float hx, float hz,
                                      int level) {
        float y = baseY + 1.5f;
        StructureObject front = AiGeometry.wall(doc, 0f, y, hz, hx, 0.15f,
                material, color, false);
        StructureObject back = AiGeometry.wall(doc, 0f, y, -hz, hx, 0.15f,
                material, color, false);
        StructureObject left = AiGeometry.wall(doc, -hx, y, 0f, 0.15f, hz,
                material, color, false);
        StructureObject right = AiGeometry.wall(doc, hx, y, 0f, 0.15f, hz,
                material, color, false);
        if (level == 0) {
            front.openings.add(AiGeometry.opening(0f, 1.5f, 2.2f));
            if (plan.hasFeature("automatic_doors")) {
                PrefabInstance door = AiGeometry.prefab(doc, "door.auto",
                        0f, baseY + 1.05f, hz);
                door.properties.put("halfX", 0.72f);
                door.properties.put("halfY", 1.05f);
                door.properties.put("halfZ", 0.08f);
            }
        }
        if (plan.hasFeature("windows")) {
            float frontOffset = hx * 0.56f;
            front.openings.add(AiGeometry.windowOpening(-frontOffset, 1.2f));
            front.openings.add(AiGeometry.windowOpening(frontOffset, 1.2f));
            float backOffset = hx * 0.43f;
            back.openings.add(AiGeometry.windowOpening(-backOffset, 1.35f));
            back.openings.add(AiGeometry.windowOpening(backOffset, 1.35f));
            float sideOffset = hz * 0.42f;
            left.openings.add(AiGeometry.windowOpening(-sideOffset, 1.25f));
            left.openings.add(AiGeometry.windowOpening(sideOffset, 1.25f));
            right.openings.add(AiGeometry.windowOpening(-sideOffset, 1.25f));
            right.openings.add(AiGeometry.windowOpening(sideOffset, 1.25f));
        }
    }

    static void addStoryPartitions(MapDocument doc,
                                           AiScenarioPlan plan,
                                           String material, float[] color,
                                           float baseY, float hx, float hz,
                                           int level) {
        String pattern = plan.roomPattern;
        if ("mixed".equals(pattern)) {
            pattern = (level & 1) == 0 ? "corridor_rooms" : "central_hall";
        }
        float y = baseY + 1.5f;
        if ("corridor_rooms".equals(pattern)) {
            for (int side = -1; side <= 1; side += 2) {
                StructureObject partition = AiGeometry.wall(doc, side * 1.6f, y, 0f,
                        0.12f, hz - 0.32f, material, color, false);
                float offset = hz * 0.38f;
                partition.openings.add(AiGeometry.opening(-offset, 1.15f, 2.15f));
                partition.openings.add(AiGeometry.opening(offset, 1.15f, 2.15f));
            }
        } else if ("central_hall".equals(pattern)) {
            float offset = Math.min(hz * 0.34f, 3f);
            for (int side = -1; side <= 1; side += 2) {
                StructureObject partition = AiGeometry.wall(doc, 0f, y, side * offset,
                        hx - 0.32f, 0.12f, material, color, true);
                partition.openings.add(AiGeometry.opening(0f, 1.55f, 2.15f));
            }
        } else if ("split_rooms".equals(pattern)) {
            StructureObject partition = AiGeometry.wall(doc, 0f, y, -hz * 0.28f,
                    hx - 0.32f, 0.12f, material, color, true);
            partition.openings.add(AiGeometry.opening(hx * 0.34f, 1.35f, 2.15f));
        } else if (!"open_plan".equals(pattern)) {
            StructureObject partition = AiGeometry.wall(doc, -2f, y, 0f,
                    0.12f, hz - 0.32f, material, color, false);
            partition.openings.add(AiGeometry.opening(0f, 1.3f, 2.15f));
        }
    }

    static void addBuildingRoof(MapDocument doc,
                                        AiScenarioPlan plan,
                                        String wallMaterial, float[] wallColor,
                                        String floorMaterial,
                                        float[] floorColor, int floors,
                                        float hx, float hz, String accessId,
                                        float accessLength) {
        float topBase = (floors - 1) * 3.3f;
        float roofY = topBase + 3.15f;
        if (plan.hasFeature("rooftop")) {
            upperFloor(doc, floorMaterial, floorColor, floors * 3.3f,
                    hx, hz, accessLength);
            PrefabInstance access = AiGeometry.prefab(doc, accessId,
                    0f, topBase, 1f);
            access.transform.yaw = 180f;
            float parapetY = floors * 3.3f + 0.38f;
            AiGeometry.block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    0f, parapetY, hz, hx, 0.38f, 0.12f, wallColor);
            AiGeometry.block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    0f, parapetY, -hz, hx, 0.38f, 0.12f, wallColor);
            AiGeometry.block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    -hx, parapetY, 0f, 0.12f, 0.38f, hz, wallColor);
            AiGeometry.block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    hx, parapetY, 0f, 0.12f, 0.38f, hz, wallColor);
            AiGeometry.prefab(doc, "pickup.special", hx * 0.45f,
                    floors * 3.3f + 0.5f, -hz * 0.45f);
        } else if ("partial".equals(plan.roofStyle)) {
            AiGeometry.block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    -hx * 0.55f, roofY, 0f, hx * 0.45f, 0.15f, hz,
                    wallColor);
            AiGeometry.block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    hx * 0.55f, roofY, 0f, hx * 0.45f, 0.15f, hz,
                    wallColor);
        } else if (!"open".equals(plan.roofStyle)) {
            AiGeometry.block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    0f, roofY, 0f, hx, 0.15f, hz, wallColor);
        }
    }

}
