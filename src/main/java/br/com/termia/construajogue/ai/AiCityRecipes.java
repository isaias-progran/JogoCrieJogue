package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import java.util.Random;

/**
 * Receitas urbanas e temáticas do gerador: cidade (avenida, avenidas
 * gêmeas, cruz com quadras), indústria, laboratório, fortaleza e ruínas.
 * Os tijolos moram em AiGeometry; o dispatch por layout continua no
 * AiScenarioBuilder.
 */
final class AiCityRecipes {

    private AiCityRecipes() {
    }

    static void buildThemedStreet(MapDocument doc,
                                          AiScenarioPlan plan,
                                          AiScenarioProfile profile,
                                          Random random, String theme) {
        if ("industrial".equals(theme)) {
            AiThemeRecipes.buildIndustrial(doc, plan, profile, random);
        } else if ("laboratory".equals(theme)) {
            AiThemeRecipes.buildLaboratory(doc, plan, profile, random);
        } else if ("fortress".equals(theme)) {
            AiThemeRecipes.buildFortress(doc, plan, profile, random);
        } else if ("ruins".equals(theme)) {
            AiThemeRecipes.buildRuins(doc, plan, profile, random);
        } else {
            buildCity(doc, plan, profile, random);
        }
    }

    /** A rota escolhe a malha viária: reta, avenidas gêmeas ou cruzamentos. */
    static void buildCity(MapDocument doc, AiScenarioPlan plan,
                                  AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        float roomX = "huge".equals(profile.sectorSize()) ? 7.2f
                : "large".equals(profile.sectorSize()) ? 6.5f
                : "medium".equals(profile.sectorSize()) ? 5.2f : 4.0f;
        float roomZ = "huge".equals(profile.sectorSize()) ? 3.65f
                : "large".equals(profile.sectorSize()) ? 3.2f : 2.6f;
        if ("loop".equals(plan.route) && half >= 24f) {
            buildTwinAvenues(doc, plan, profile, random,
                    half, roomX, roomZ);
        } else if ("maze".equals(plan.route) && half >= 20f) {
            buildCrossQuarters(doc, plan, profile, random,
                    half, roomX, roomZ);
        } else {
            buildAvenue(doc, plan, profile, random, half, roomX, roomZ,
                    "branching".equals(plan.route));
        }
        if ("city".equals(plan.setting)) {
            roadCenterLine(doc, half);
            if ("huge".equals(profile.sectorSize())) {
                skylineVolumes(doc, half, random);
            }
        }
    }

    /**
     * maze na cidade: avenida em cruz com quadras nos quatro quadrantes,
     * o desenho da Cidade Aurora — referência de complexidade do app.
     */
    static void buildCrossQuarters(MapDocument doc,
                                           AiScenarioPlan plan,
                                           AiScenarioProfile profile,
                                           Random random, float half,
                                           float roomX, float roomZ) {
        int buildings = Math.min(profile.rows() * 2,
                Math.max(4, plan.buildingCount));
        float roadHalf = 3.2f;
        for (int index = 0; index < buildings; index++) {
            int quadrant = index % 4;
            int ring = index / 4;
            float qx = quadrant % 2 == 0 ? -1f : 1f;
            float qz = quadrant < 2 ? -1f : 1f;
            float hx = roomX * (0.78f + random.nextFloat() * 0.12f);
            float hz = roomZ * (0.88f + random.nextFloat() * 0.18f);
            float cx = qx * (roadHalf + hx + 0.4f);
            float cz = qz * (roadHalf + hz + 0.8f
                    + ring * (hz * 2f + 2.4f));
            if (Math.abs(cz) + hz > half - 1.2f) continue;
            addRoom(doc, cx, cz, hx, hz, (int) -qx, plan, index, random);
        }
        if ("city".equals(plan.setting)) {
            crosswalks(doc, roadHalf);
        }
    }

    /** Faixas de pedestres nas bocas do cruzamento, como na Aurora. */
    static void crosswalks(MapDocument doc, float roadHalf) {
        float[] white = {0.88f, 0.88f, 0.86f};
        for (int approach = 0; approach < 2; approach++) {
            float z = (approach == 0 ? -1f : 1f) * (roadHalf + 1.2f);
            for (int stripe = -1; stripe <= 1; stripe++) {
                AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "plain",
                        stripe * 1.1f, 0.02f, z, 0.38f, 0.02f, 0.55f,
                        white);
            }
        }
    }

    /** Volumes simples de skyline nos cantos: cidade grande tem horizonte. */
    static void skylineVolumes(MapDocument doc, float half,
                                       Random random) {
        float edge = half - 3.4f;
        for (int corner = 0; corner < 4; corner++) {
            float sx = corner % 2 == 0 ? -edge : edge;
            float sz = corner < 2 ? -edge : edge;
            float height = 6.5f + random.nextFloat() * 3.5f;
            AiGeometry.block(doc, StructureObject.ROLE_BLOCK, "metal",
                    sx, height / 2f, sz, 2.4f, height / 2f, 2.4f,
                    new float[]{0.16f, 0.19f, 0.24f});
        }
    }

    static void buildAvenue(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile, Random random,
                                    float half, float roomX, float roomZ,
                                    boolean crossings) {
        int buildings = Math.min(profile.rows() * 2,
                Math.max(1, plan.buildingCount));
        int rows = (buildings + 1) / 2;
        float roadHalf = 3.2f;
        float crossA = -Math.min(half * 0.26f, 7f);
        float crossB = Math.min(half * 0.20f, 5f);
        for (int row = 0; row < rows; row++) {
            float z = AiGeometry.rowPosition(row, rows, half, 6.2f, 7.2f);
            if (row > 0 && row + 1 < rows) {
                z += (random.nextFloat() - 0.5f) * 1.4f;
            }
            if (crossings) {
                // Transversais do branching viram ruas de verdade: o
                // quarteirão sai da faixa em vez de bloquear o cruzamento.
                float clear = roomZ + 1.6f;
                if (Math.abs(z - crossA) < clear) {
                    z = crossA + (z >= crossA ? clear : -clear);
                }
                if (Math.abs(z - crossB) < clear) {
                    z = crossB + (z >= crossB ? clear : -clear);
                }
            }
            float leftX = roomX * (0.90f + random.nextFloat() * 0.10f);
            float rightX = roomX * (0.90f + random.nextFloat() * 0.10f);
            float leftZ = roomZ * (0.88f + random.nextFloat() * 0.18f);
            float rightZ = roomZ * (0.88f + random.nextFloat() * 0.18f);
            int leftIndex = row * 2;
            if (leftIndex < buildings) {
                addRoom(doc, -(roadHalf + leftX), z, leftX, leftZ,
                        -1, plan, leftIndex, random);
            }
            if (leftIndex + 1 < buildings) {
                addRoom(doc, roadHalf + rightX, z, rightX, rightZ,
                        1, plan, leftIndex + 1, random);
            }
        }
    }

    /** loop: duas avenidas paralelas com quadra central — dá para circular. */
    static void buildTwinAvenues(MapDocument doc, AiScenarioPlan plan,
                                         AiScenarioProfile profile,
                                         Random random, float half,
                                         float roomX, float roomZ) {
        int buildings = Math.min(profile.rows() * 3,
                Math.max(3, plan.buildingCount));
        int rows = (buildings + 2) / 3;
        float roadHalf = 3.2f;
        float span = Math.min(half * 0.45f, roomX * 2f + roadHalf * 2.4f);
        for (int row = 0; row < rows; row++) {
            float z = AiGeometry.rowPosition(row, rows, half, 6.2f, 7.2f);
            if (row > 0 && row + 1 < rows) {
                z += (random.nextFloat() - 0.5f) * 1.4f;
            }
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                if (index >= buildings) break;
                float hx = roomX * (0.72f + random.nextFloat() * 0.10f);
                float hz = roomZ * (0.88f + random.nextFloat() * 0.18f);
                float cx = col == 0 ? -(span + roadHalf + hx)
                        : col == 2 ? span + roadHalf + hx : 0f;
                int side = col == 0 ? -1
                        : col == 2 ? 1 : (row % 2 == 0 ? -1 : 1);
                if (col == 1) {
                    hx = Math.min(hx, span - roadHalf - 0.6f);
                    if (hx < 1.6f) continue;
                }
                addRoom(doc, cx, z, hx, hz, side, plan, index, random);
            }
        }
    }

    /** Faixa central tracejada da avenida, como na Cidade Aurora. */
    static void roadCenterLine(MapDocument doc, float half) {
        float reach = half - 6.5f;
        int dashes = Math.max(4, (int) (reach / 4f));
        for (int i = 0; i < dashes; i++) {
            float z = -reach + (2f * reach) * i / (dashes - 1f);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "plain",
                    0f, 0.02f, z, 0.12f, 0.02f, 1.1f,
                    new float[]{0.92f, 0.90f, 0.78f});
        }
    }





    /** side -1 = sala à esquerda; +1 = à direita da via. */
    static void addRoom(MapDocument doc, float cx, float cz,
                                float hx, float hz, int side,
                                AiScenarioPlan plan, int index,
                                Random random) {
        String material = AiGeometry.wallMaterial(plan);
        float[] color = AiGeometry.wallColor(plan);
        float frontX = cx - side * hx;
        float backX = cx + side * hx;
        StructureObject front = AiGeometry.wall(doc, frontX, 1.5f, cz,
                0.15f, hz, material, color, true);
        front.openings.add(AiGeometry.opening(0f, 1.25f, 2.15f));
        AiGeometry.wall(doc, backX, 1.5f, cz, 0.15f, hz,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                material, color, false);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR,
                "laboratory".equals(plan.setting) ? "checker" : "wood",
                cx, 0.025f, cz, hx - 0.15f, 0.025f, hz - 0.15f,
                new float[]{0.42f, 0.36f, 0.30f});
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "plain",
                cx, 3.15f, cz, hx, 0.15f, hz, AiGeometry.DARK);

        if (plan.hasFeature("automatic_doors") && (index & 1) == 0) {
            PrefabInstance door = AiGeometry.prefab(doc, "door.auto", frontX,
                    1.05f, cz);
            door.properties.put("halfX", 0.08f);
            door.properties.put("halfY", 1.05f);
            door.properties.put("halfZ", 0.60f);
        }
        if ((index & 1) == 0) {
            PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.ceiling",
                    cx, 3f, cz);
            lamp.properties.put("lightR", 0.82f
                    + random.nextFloat() * 0.18f);
            lamp.properties.put("lightG", 0.72f);
            lamp.properties.put("lightB", 0.52f);
            lamp.properties.put("lightRadius", 5.5f);
        } else {
            AiGeometry.prefab(doc, "obstacle.crate.large", cx, 0f, cz);
        }
        if (plan.hasFeature("furniture")) {
            roomPurpose(doc, plan.zoneAt(index).kind, cx, cz, hx, hz);
        }
    }

    /** Mobília leve pela finalidade da zone: a loja não parece a casa. */
    static void roomPurpose(MapDocument doc, String kind,
                                    float cx, float cz,
                                    float hx, float hz) {
        float ox = Math.min(1.9f, hx * 0.52f);
        float oz = Math.min(1.7f, hz * 0.52f);
        if ("shop".equals(kind)) {
            AiGeometry.prefab(doc, "furniture.shelf", cx - ox, 0f, cz - oz);
            AiGeometry.prefab(doc, "furniture.table", cx + ox, 0f, cz + oz);
        } else if ("laboratory".equals(kind) || "warehouse".equals(kind)
                || "station".equals(kind)) {
            AiGeometry.prefab(doc, "furniture.workbench", cx - ox, 0f, cz - oz);
            AiGeometry.prefab(doc, "obstacle.barrel", cx + ox, 0f, cz + oz);
        } else if ("park".equals(kind) || "plaza".equals(kind)
                || "courtyard".equals(kind)) {
            AiGeometry.prefab(doc, "prop.plant.tall", cx - ox, 0f, cz - oz);
            AiGeometry.prefab(doc, "prop.plant.small", cx + ox, 0f, cz + oz);
        } else {
            AiGeometry.prefab(doc, "furniture.table", cx - ox, 0f, cz - oz);
            AiGeometry.prefab(doc, "furniture.chair", cx + ox, 0f, cz + oz);
        }
    }


}
