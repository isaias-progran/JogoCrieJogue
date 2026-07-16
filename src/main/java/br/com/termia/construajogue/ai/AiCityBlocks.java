package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import java.util.Random;

/**
 * Arquétipos de quarteirão urbano, no espírito da Cidade Aurora: loja,
 * garagem de portão largo, sobrado com escada externa até o telhado e
 * mercado de dois portais. O rodízio evita a cidade de caixas iguais.
 */
final class AiCityBlocks {

    private AiCityBlocks() {
    }

    /** Rodízio determinístico por índice; caixas viram bairros variados. */
    static void addCityBlock(MapDocument doc, AiScenarioPlan plan,
                             float cx, float cz, float hx, float hz,
                             int side, int index, Random random,
                             float half) {
        int archetype = index % 4;
        boolean stairsFit = cz - hz - 3.6f > -(half - 1.2f);
        if (archetype == 2 && stairsFit && hx >= 2.6f && hz >= 2.4f) {
            addTwoStory(doc, plan, cx, cz, hx, hz, side, index, random);
        } else if (archetype == 1) {
            addGarage(doc, plan, cx, cz, hx, hz, side, index, random);
        } else if (archetype == 3) {
            addMarket(doc, plan, cx, cz, hx, hz, side, index, random);
        } else {
            AiCityRecipes.addRoom(doc, cx, cz, hx, hz, side, plan,
                    index, random);
        }
    }

    /** Sobrado: térreo normal, escada externa ao fundo e sala no telhado. */
    private static void addTwoStory(MapDocument doc, AiScenarioPlan plan,
                                    float cx, float cz, float hx, float hz,
                                    int side, int index, Random random) {
        AiCityRecipes.addRoom(doc, cx, cz, hx, hz, side, plan,
                index, random);
        String material = AiGeometry.wallMaterial(plan);
        float[] color = AiGeometry.wallColor(plan);

        PrefabInstance stairs = AiGeometry.prefab(doc, "stairs.floor",
                cx, 0f, cz - hz - 1.8f);
        stairs.transform.yaw = 0f;

        // Sala recuada sobre a laje, deixando passarela no lado da escada.
        float hxU = hx - 0.6f;
        float hzU = hz - 0.9f;
        float czU = cz + 0.45f;
        StructureObject front = AiGeometry.wall(doc, cx, 4.8f,
                czU - hzU, hxU, 0.15f, material, color, false);
        front.openings.add(AiGeometry.opening(0f, 1.2f, 2.1f));
        AiGeometry.wall(doc, cx, 4.8f, czU + hzU, hxU, 0.15f,
                material, color, false);
        StructureObject left = AiGeometry.wall(doc, cx - hxU, 4.8f, czU,
                0.15f, hzU, material, color, false);
        left.openings.add(AiGeometry.windowOpening(0f, 1.2f));
        StructureObject right = AiGeometry.wall(doc, cx + hxU, 4.8f, czU,
                0.15f, hzU, material, color, false);
        right.openings.add(AiGeometry.windowOpening(0f, 1.2f));
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, material,
                cx, 6.45f, czU, hxU + 0.15f, 0.15f, hzU + 0.15f,
                AiGeometry.DARK);
        if (plan.hasFeature("indoor_lights")) {
            PrefabInstance lamp = AiGeometry.prefab(doc,
                    "prop.lamp.ceiling", cx, 6.3f, czU);
            lamp.properties.put("lightR", 0.85f);
            lamp.properties.put("lightG", 0.78f);
            lamp.properties.put("lightB", 0.60f);
            lamp.properties.put("lightRadius", 5f);
        }
        AiGeometry.prefab(doc, "pickup.ammo", cx, 3.45f, czU);
    }

    /** Garagem: portão largo sem porta, caixotes e barril lá dentro. */
    private static void addGarage(MapDocument doc, AiScenarioPlan plan,
                                  float cx, float cz, float hx, float hz,
                                  int side, int index, Random random) {
        String material = AiGeometry.wallMaterial(plan);
        float[] color = AiGeometry.wallColor(plan);
        float frontX = cx - side * hx;
        StructureObject front = AiGeometry.wall(doc, frontX, 1.5f, cz,
                0.15f, hz, material, color, true);
        front.openings.add(AiGeometry.opening(0f,
                Math.min(2.8f, hz * 1.2f), 2.45f));
        AiGeometry.wall(doc, cx + side * hx, 1.5f, cz, 0.15f, hz,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                material, color, false);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "metal",
                cx, 0.025f, cz, hx - 0.15f, 0.025f, hz - 0.15f,
                new float[]{0.30f, 0.33f, 0.36f});
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "plain",
                cx, 3.15f, cz, hx, 0.15f, hz, AiGeometry.DARK);
        AiGeometry.prefab(doc, "obstacle.crate.large",
                cx + side * hx * 0.35f, 0f, cz - hz * 0.3f);
        AiGeometry.prefab(doc, "obstacle.barrel",
                cx - side * hx * 0.3f, 0f, cz + hz * 0.35f);
    }

    /** Mercado: dois portais no lugar da porta e corredores de estantes. */
    private static void addMarket(MapDocument doc, AiScenarioPlan plan,
                                  float cx, float cz, float hx, float hz,
                                  int side, int index, Random random) {
        String material = AiGeometry.wallMaterial(plan);
        float[] color = AiGeometry.wallColor(plan);
        float frontX = cx - side * hx;
        StructureObject front = AiGeometry.wall(doc, frontX, 1.5f, cz,
                0.15f, hz, material, color, true);
        float portal = Math.min(1.6f, hz * 0.55f);
        front.openings.add(AiGeometry.opening(-hz * 0.45f, portal, 2.3f));
        front.openings.add(AiGeometry.opening(hz * 0.45f, portal, 2.3f));
        AiGeometry.wall(doc, cx + side * hx, 1.5f, cz, 0.15f, hz,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                material, color, false);
        AiGeometry.wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                material, color, false);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                cx, 0.025f, cz, hx - 0.15f, 0.025f, hz - 0.15f,
                new float[]{0.46f, 0.48f, 0.50f});
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "plain",
                cx, 3.15f, cz, hx, 0.15f, hz, AiGeometry.DARK);
        float ox = Math.min(1.8f, hx * 0.45f);
        AiGeometry.prefab(doc, "furniture.shelf", cx - ox, 0f, cz);
        AiGeometry.prefab(doc, "furniture.shelf", cx + ox, 0f, cz);
        AiGeometry.prefab(doc, "furniture.workbench", cx, 0f,
                cz + hz * 0.55f);
        if (plan.hasFeature("indoor_lights") && (index & 1) == 0) {
            PrefabInstance lamp = AiGeometry.prefab(doc,
                    "prop.lamp.ceiling", cx, 3f, cz);
            lamp.properties.put("lightR", 0.80f);
            lamp.properties.put("lightG", 0.82f);
            lamp.properties.put("lightB", 0.70f);
            lamp.properties.put("lightRadius", 6f);
        }
    }
}
