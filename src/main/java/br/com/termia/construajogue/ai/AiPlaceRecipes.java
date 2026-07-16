package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import java.util.Random;

/**
 * Receitas de lugares abertos: pátio, campus/espalhado, praça central,
 * labirinto, câmaras lineares e o prédio isolado que todas usam. Os
 * tijolos moram em AiGeometry.
 */
final class AiPlaceRecipes {

    private AiPlaceRecipes() {
    }

    static void buildCourtyard(MapDocument doc, AiScenarioPlan plan,
                                       AiScenarioProfile profile,
                                       Random random) {
        float half = profile.halfSize();
        float span = Math.min(half * 0.46f, half - 5.2f);
        float room = Math.min(4.1f, half * 0.24f);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                0f, 0.025f, 0f, Math.min(5f, half * 0.3f), 0.025f,
                Math.min(5f, half * 0.3f),
                new float[]{0.48f, 0.46f, 0.42f});
        int count = Math.min(4, Math.max(2, plan.buildingCount));
        // branching desalinha as alas; loop cerca o jardim com diagonais.
        float stagger = "branching".equals(plan.route) ? span * 0.30f : 0f;
        float[] xs = {-span, span, -span, span};
        float[] zs = {-span, -span, span, span};
        for (int i = 0; i < count; i++) {
            float cz = zs[i] + (i % 2 == 0 ? stagger : -stagger);
            addDetachedBuilding(doc, plan, plan.zoneAt(i), xs[i], cz,
                    room, room * (0.78f + random.nextFloat() * 0.12f), i);
        }
        if ("loop".equals(plan.route)) {
            plazaChamfers(doc, plan, Math.min(4.6f, half * 0.28f));
        }
        AiGeometry.prefab(doc, "prop.plant.tall", -1.8f, 0f, 0f);
        AiGeometry.prefab(doc, "prop.plant.tall", 1.8f, 0f, 0f);
    }

    static void buildCampus(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile, Random random,
                                    boolean scattered) {
        float half = profile.halfSize();
        int count = Math.min(6, Math.max(2, plan.buildingCount));
        int rows = (count + 1) / 2;
        float hx = Math.min(4.2f, half * 0.22f);
        float hz = Math.min(3.4f, half * 0.18f);
        for (int i = 0; i < count; i++) {
            int side = (i & 1) == 0 ? -1 : 1;
            int row = i / 2;
            float x = side * half * (scattered
                    ? 0.34f + random.nextFloat() * 0.20f : 0.46f);
            float z = AiGeometry.rowPosition(row, rows, half, 5.2f, 7.5f);
            if (scattered) z += (random.nextFloat() - 0.5f) * 4f;
            addDetachedBuilding(doc, plan, plan.zoneAt(i), x, z,
                    hx * (0.82f + random.nextFloat() * 0.18f),
                    hz * (0.82f + random.nextFloat() * 0.18f), i);
        }
    }

    /** A rota muda a praça: alas cardeais, seis alas ou anel diagonal. */
    static void buildHub(MapDocument doc, AiScenarioPlan plan,
                                 AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        float span = Math.min(half - 5.5f, half * 0.56f);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                0f, 0.025f, 0f, 5.2f, 0.025f, 5.2f,
                new float[]{0.40f, 0.43f, 0.48f});
        AiGeometry.block(doc, StructureObject.ROLE_BLOCK, "metal",
                0f, 0.65f, 0f, 0.8f, 0.65f, 0.8f, AiGeometry.DARK);
        boolean diagonalWings = "loop".equals(plan.route);
        int wings = "branching".equals(plan.route) ? 6 : 4;
        if (!diagonalWings) {
            plazaChamfers(doc, plan, Math.min(6.6f, span - 5.4f));
        }
        for (int i = 0; i < wings; i++) {
            double angle = i * (Math.PI * 2.0 / wings)
                    + (diagonalWings ? Math.PI / 4.0 : 0.0);
            float cx = (float) Math.cos(angle) * span;
            float cz = (float) Math.sin(angle) * span;
            addDetachedBuilding(doc, plan, plan.zoneAt(i), cx, cz,
                    i % 2 == 0 ? 3.4f : 4.4f,
                    i % 2 == 0 ? 4.4f : 3.4f, i);
        }
        AiGeometry.prefab(doc, "pickup.special", 0f, 1.8f, 0f);
    }

    /** Cantos chanfrados da praça em paredes diagonais (KIND_POLY). */
    static void plazaChamfers(MapDocument doc, AiScenarioPlan plan,
                                      float radius) {
        if (radius < 2.4f) return;
        String material = AiGeometry.wallMaterial(plan);
        float[] color = AiGeometry.wallColor(plan);
        float near = radius * 0.42f;
        AiGeometry.diagonalWall(doc, near, radius, radius, near, material, color);
        AiGeometry.diagonalWall(doc, -near, radius, -radius, near, material, color);
        AiGeometry.diagonalWall(doc, near, -radius, radius, -near, material, color);
        AiGeometry.diagonalWall(doc, -near, -radius, -radius, -near, material, color);
    }

    static void buildMaze(MapDocument doc, AiScenarioPlan plan,
                                  AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        int rows = Math.min(7, Math.max(4, plan.roomCount / 2 + 2));
        float length = half - 2.2f;
        for (int i = 0; i < rows; i++) {
            float z = AiGeometry.rowPosition(i, rows, half, 4.5f, 6.8f);
            StructureObject cross = AiGeometry.wall(doc, 0f, 1.5f, z,
                    length, 0.13f, AiGeometry.wallMaterial(plan), AiGeometry.wallColor(plan), true);
            float offset = (i & 1) == 0 ? -length * 0.62f
                    : length * 0.62f;
            cross.openings.add(AiGeometry.opening(offset, 3f, 2.35f));
            if (i + 1 < rows && i % 2 == 0) {
                float branchX = (i % 4 == 0 ? -1f : 1f) * half * 0.28f;
                AiGeometry.wall(doc, branchX, 1.5f,
                        z + (half * 1.4f / rows),
                        0.13f, half * 0.18f, AiGeometry.wallMaterial(plan),
                        AiGeometry.wallColor(plan), false);
            }
        }
    }

    static void buildLinear(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile,
                                    Random random) {
        float half = profile.halfSize();
        int chambers = Math.min(6, Math.max(3, plan.roomCount / 2));
        float length = half - 2.3f;
        for (int i = 0; i < chambers; i++) {
            float z = AiGeometry.rowPosition(i, chambers, half, 5.4f, 7.4f);
            StructureObject cross = AiGeometry.wall(doc, 0f, 1.5f, z,
                    length, 0.15f, AiGeometry.wallMaterial(plan), AiGeometry.wallColor(plan), true);
            float doorX = (i % 3 - 1) * Math.min(3.2f, half * 0.18f);
            cross.openings.add(AiGeometry.opening(doorX, 2.4f, 2.3f));
            AiGeometry.prefab(doc, (i & 1) == 0 ? "obstacle.crate.small"
                    : "obstacle.barrel", -doorX, 0f,
                    z + Math.min(3.3f, half * 0.15f));
        }
        if ("laboratory".equals(plan.setting)
                || "industrial".equals(plan.setting)) {
            AiGeometry.block(doc, StructureObject.ROLE_CEILING, "metal",
                    0f, 3.15f, 0f, half - 0.4f, 0.15f, half - 0.4f, AiGeometry.DARK);
        }
    }

    static void addDetachedBuilding(MapDocument doc,
                                            AiScenarioPlan plan,
                                            AiScenarioPlan.Zone zone,
                                            float cx, float cz,
                                            float hx, float hz, int index) {
        String material = AiGeometry.buildingMaterial(plan, zone);
        float[] color = AiGeometry.buildingColor(plan, zone);
        String floorMaterial = AiGeometry.buildingFloorMaterial(zone);
        float frontZ = cz >= 0f ? cz - hz : cz + hz;
        float backZ = cz >= 0f ? cz + hz : cz - hz;
        StructureObject front = AiGeometry.wall(doc, cx, 1.5f, frontZ,
                hx, 0.14f, material, color, true);
        front.openings.add(AiGeometry.opening(0f, 1.35f, 2.2f));
        AiGeometry.wall(doc, cx, 1.5f, backZ, hx, 0.14f,
                material, color, false);
        AiGeometry.wall(doc, cx - hx, 1.5f, cz, 0.14f, hz,
                material, color, false);
        AiGeometry.wall(doc, cx + hx, 1.5f, cz, 0.14f, hz,
                material, color, false);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, floorMaterial,
                cx, 0.025f, cz, hx - 0.14f, 0.025f, hz - 0.14f,
                new float[]{0.43f, 0.39f, 0.34f});
        if (!"open".equals(plan.roofStyle)) {
            AiGeometry.block(doc, StructureObject.ROLE_CEILING, material,
                    cx, 3.15f, cz, hx, 0.15f, hz, AiGeometry.DARK);
        }
        if (plan.hasFeature("automatic_doors") && (index & 1) == 0) {
            PrefabInstance door = AiGeometry.prefab(doc, "door.auto",
                    cx, 1.05f, frontZ);
            door.properties.put("halfX", 0.64f);
            door.properties.put("halfY", 1.05f);
            door.properties.put("halfZ", 0.08f);
        }
        if (plan.hasFeature("furniture")) {
            AiGeometry.prefab(doc, "house".equals(zone.kind) ? "furniture.table"
                    : "furniture.workbench", cx, 0f, cz);
        }
        if (plan.hasFeature("indoor_lights")) {
            PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.ceiling",
                    cx, 3f, cz);
            lamp.properties.put("lightR", 0.90f);
            lamp.properties.put("lightG", 0.78f);
            lamp.properties.put("lightB", 0.62f);
            lamp.properties.put("lightRadius", 5.8f);
        }
    }

}
