package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import java.util.Random;

/**
 * Receitas dos temas industriais e de guerra: galpões, laboratório,
 * fortaleza e ruínas. Os tijolos moram em AiGeometry.
 */
final class AiThemeRecipes {

    private AiThemeRecipes() {
    }

    static void buildIndustrial(MapDocument doc, AiScenarioPlan plan,
                                        AiScenarioProfile profile,
                                        Random random) {
        int bays = Math.max(2, profile.rows() - 1);
        float half = profile.halfSize();
        float hx = "huge".equals(profile.sectorSize()) ? 8.2f
                : "large".equals(profile.sectorSize()) ? 7.0f
                : "medium".equals(profile.sectorSize()) ? 5.4f : 4.1f;
        float hz = "compact".equals(profile.sectorSize()) ? 3.0f : 4.2f;
        // loop abre dois corredores com espinha central; branching deixa
        // uma travessa livre no meio do galpão.
        boolean spine = "loop".equals(plan.route) && half >= 24f;
        boolean crossGap = "branching".equals(plan.route);
        float spineHx = Math.min(2.6f, hx * 0.4f);
        for (int i = 0; i < bays; i++) {
            float z = AiGeometry.rowPosition(i, bays, half, 7f, 8f);
            if (crossGap && Math.abs(z) < hz + 1.6f) {
                z = z >= 0f ? hz + 1.6f : -(hz + 1.6f);
            }
            addIndustrialBay(doc, -(3.8f + hx), z, hx, hz, -1,
                    i * 2, random);
            addIndustrialBay(doc, 3.8f + hx, z, hx, hz, 1,
                    i * 2 + 1, random);
            if (spine && i + 1 < bays) {
                addIndustrialBay(doc, 0f, z + hz + 1.4f, spineHx, hz * 0.7f,
                        (i & 1) == 0 ? -1 : 1, bays * 2 + i, random);
            }
        }
        // Obstáculos centrais alternados mudam o ritmo sem fechar a rota
        // (a espinha do loop já ocupa o centro; não empilhar caixote nela).
        int crates = spine ? 0 : profile.rows();
        for (int i = 0; i < crates; i++) {
            float z = AiGeometry.rowPosition(i, crates, half, 5f, 7f);
            PrefabInstance crate = AiGeometry.prefab(doc,
                    (i & 1) == 0 ? "obstacle.crate.large"
                            : "obstacle.crate.small",
                    (i & 1) == 0 ? -2.35f : 2.35f, 0f, z);
            crate.transform.yaw = (i & 1) * 90f;
        }
    }

    static void buildLaboratory(MapDocument doc, AiScenarioPlan plan,
                                        AiScenarioProfile profile,
                                        Random random) {
        AiCityRecipes.buildCity(doc, plan, profile, random);
        int checkpoints = Math.max(1, profile.rows() - 1);
        float half = profile.halfSize();
        for (int i = 0; i < checkpoints; i++) {
            float z = AiGeometry.rowPosition(i, checkpoints, half, 10f, 11f);
            StructureObject partition = AiGeometry.wall(doc, 0f, 1.5f, z,
                    3.25f, 0.12f, "metal", AiGeometry.LIGHT, true);
            partition.openings.add(AiGeometry.opening(0f, 2.35f, 2.25f));
            if (plan.hasFeature("automatic_doors")) {
                PrefabInstance door = AiGeometry.prefab(doc, "door.auto", 0f, 1.05f, z);
                door.properties.put("halfX", 1.10f);
                door.properties.put("halfY", 1.05f);
                door.properties.put("halfZ", 0.08f);
            }
        }
    }

    static void buildFortress(MapDocument doc, AiScenarioPlan plan,
                                      AiScenarioProfile profile,
                                      Random random) {
        float half = profile.halfSize();
        float innerX = Math.min(half - 4.5f, half * 0.46f);
        float length = half - 3.2f;
        StructureObject left = AiGeometry.wall(doc, -innerX, 1.5f, 0f,
                0.18f, length, "plain", AiGeometry.wallColor(plan), false);
        StructureObject right = AiGeometry.wall(doc, innerX, 1.5f, 0f,
                0.18f, length, "plain", AiGeometry.wallColor(plan), false);
        int sideDoors = Math.max(2, profile.rows() - 1);
        for (int i = 0; i < sideDoors; i++) {
            float offset = AiGeometry.rowPosition(i, sideDoors, half, 7f, 8f);
            left.openings.add(AiGeometry.opening(offset, 2.1f, 2.25f));
            right.openings.add(AiGeometry.opening(offset, 2.1f, 2.25f));
        }
        for (int side = -1; side <= 1; side += 2) {
            float z = side * half * 0.43f;
            StructureObject cross = AiGeometry.wall(doc, 0f, 1.5f, z,
                    innerX, 0.18f, "plain", AiGeometry.wallColor(plan), true);
            if ("loop".equals(plan.route)) {
                // Duas portas afastadas: dá para rondar a muralha em anel.
                cross.openings.add(AiGeometry.opening(-innerX * 0.55f, 2.2f, 2.3f));
                cross.openings.add(AiGeometry.opening(innerX * 0.55f, 2.2f, 2.3f));
            } else {
                cross.openings.add(AiGeometry.opening(0f, 3f, 2.4f));
            }
        }
        if ("branching".equals(plan.route)) {
            // Bastiões chanfrados nos cantos, em paredes diagonais reais.
            float bx = innerX * 0.94f;
            float bz = half * 0.55f;
            for (int ix = -1; ix <= 1; ix += 2) {
                for (int iz = -1; iz <= 1; iz += 2) {
                    AiGeometry.diagonalWall(doc, ix * bx, iz * bz,
                            ix * bx * 0.45f, iz * Math.min(half - 1.6f,
                                    bz * 1.28f),
                            "plain", AiGeometry.wallColor(plan));
                }
            }
        }
        for (int ix = -1; ix <= 1; ix += 2) {
            for (int iz = -1; iz <= 1; iz += 2) {
                float x = ix * (innerX - 2.1f);
                float z = iz * half * 0.62f;
                AiGeometry.block(doc, StructureObject.ROLE_BLOCK, "plain",
                        x, 1.8f, z, 1.8f, 1.8f, 1.8f,
                        new float[]{0.31f, 0.33f, 0.36f});
                AiGeometry.block(doc, StructureObject.ROLE_CEILING, "plain",
                        x, 3.75f, z, 2.05f, 0.15f, 2.05f, AiGeometry.DARK);
            }
        }
    }

    static void buildRuins(MapDocument doc, AiScenarioPlan plan,
                                   AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        int pieces = profile.rows() * 4 + 2;
        boolean ring = "loop".equals(plan.route);
        for (int i = 0; i < pieces; i++) {
            float x;
            float z;
            if (ring) {
                // Destroços em elipse: a ruína circunda um vazio central.
                double angle = i * (Math.PI * 2.0 / pieces);
                float radius = half * (0.48f + random.nextFloat() * 0.14f);
                x = (float) Math.cos(angle) * radius;
                z = (float) Math.sin(angle) * radius * 0.82f;
            } else {
                int side = (i & 1) == 0 ? -1 : 1;
                z = -half + 4.5f + (half * 2f - 9f)
                        * i / Math.max(1f, pieces - 1f);
                z += (random.nextFloat() - 0.5f) * 1.8f;
                x = side * (4.5f + random.nextFloat()
                        * Math.max(1f, half * 0.34f));
            }
            if (i % 3 == 0) {
                AiGeometry.wall(doc, x, 1.5f, z, 2.2f, 0.15f,
                        "brick", AiGeometry.wallColor(plan), false);
            } else {
                AiGeometry.wall(doc, x, 1.5f, z, 0.15f, 1.7f,
                        "brick", AiGeometry.wallColor(plan), false);
            }
            if (i % 4 == 1) {
                AiGeometry.block(doc, StructureObject.ROLE_CEILING, "plain",
                        x, 3.15f, z, 1.4f, 0.15f, 1.2f, AiGeometry.DARK);
            }
        }
    }

    static void addIndustrialBay(MapDocument doc, float cx, float cz,
                                         float hx, float hz, int side,
                                         int index, Random random) {
        float backX = cx + side * hx;
        AiGeometry.wall(doc, backX, 1.5f, cz, 0.15f, hz,
                "metal", AiGeometry.CONCRETE, false);
        AiGeometry.wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                "metal", AiGeometry.CONCRETE, false);
        AiGeometry.wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                "metal", AiGeometry.CONCRETE, false);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "metal",
                cx, 0.025f, cz, hx, 0.025f, hz,
                new float[]{0.30f, 0.33f, 0.36f});
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "metal",
                cx, 3.15f, cz, hx, 0.15f, hz, AiGeometry.DARK);
        AiGeometry.prefab(doc, (index & 1) == 0 ? "obstacle.crate.large"
                        : "obstacle.crate.small",
                cx, 0f, cz + (random.nextFloat() - 0.5f) * hz);
        PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.ceiling",
                cx, 3f, cz);
        lamp.properties.put("lightR", 0.72f);
        lamp.properties.put("lightG", 0.80f);
        lamp.properties.put("lightB", 0.86f);
        lamp.properties.put("lightRadius", 6.2f);
    }

}
