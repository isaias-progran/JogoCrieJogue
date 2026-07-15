package br.com.termia.construajogue.prefab;

/**
 * Peças estáticas procedurais (móveis, obstáculos, luminárias) em
 * caixas low-poly. Origem na BASE da peça (y=0 no chão). `parts` são as
 * caixas visuais {cx,cy,cz,hx,hy,hz,r,g,b}; `colliders` são as caixas
 * de colisão {cx,cy,cz,hx,hy,hz} — normalmente uma só, mais simples que
 * o visual (ex.: mesa colide como um bloco, sem passar por baixo).
 * Cores > 1 fazem a cúpula das luminárias parecer acesa (o shader
 * multiplica; não há luz dinâmica de verdade).
 */
public final class PrefabMeshFactory {

    private static final float[] WOOD = {0.55f, 0.40f, 0.25f};
    private static final float[] WOOD_DARK = {0.42f, 0.30f, 0.18f};
    private static final float[] METAL = {0.55f, 0.58f, 0.62f};
    private static final float[] GRAFITE = {0.24f, 0.26f, 0.30f};
    private static final float[] CLOTH = {0.85f, 0.85f, 0.88f};
    private static final float[] GLOW = {1.9f, 1.75f, 1.25f};
    private static final float[] CERAMIC = {0.90f, 0.92f, 0.95f};
    private static final float[] TERRACOTA = {0.65f, 0.35f, 0.22f};
    private static final float[] LEAF = {0.25f, 0.50f, 0.28f};
    private static final float[] LEAF_LIGHT = {0.35f, 0.62f, 0.33f};

    // Convenção: a FRENTE da peça (porta, torneira, assento) aponta
    // para -Z em yaw 0; a planta desenha a seta de frente nesse lado.

    private PrefabMeshFactory() {
    }

    /** Caixas visuais da peça, ou null se o id não é estático. */
    public static float[][] parts(String id) {
        switch (id) {
            case "furniture.table":
                return new float[][]{
                        box(0, 0.72f, 0, 0.70f, 0.025f, 0.40f, WOOD),
                        box(-0.64f, 0.35f, -0.34f, 0.03f, 0.35f, 0.03f,
                                WOOD_DARK),
                        box(0.64f, 0.35f, -0.34f, 0.03f, 0.35f, 0.03f,
                                WOOD_DARK),
                        box(-0.64f, 0.35f, 0.34f, 0.03f, 0.35f, 0.03f,
                                WOOD_DARK),
                        box(0.64f, 0.35f, 0.34f, 0.03f, 0.35f, 0.03f,
                                WOOD_DARK)};
            case "furniture.chair":
                return new float[][]{
                        box(0, 0.44f, 0, 0.22f, 0.02f, 0.22f, WOOD),
                        box(-0.18f, 0.21f, -0.18f, 0.025f, 0.21f, 0.025f,
                                WOOD_DARK),
                        box(0.18f, 0.21f, -0.18f, 0.025f, 0.21f, 0.025f,
                                WOOD_DARK),
                        box(-0.18f, 0.21f, 0.18f, 0.025f, 0.21f, 0.025f,
                                WOOD_DARK),
                        box(0.18f, 0.21f, 0.18f, 0.025f, 0.21f, 0.025f,
                                WOOD_DARK),
                        box(0, 0.72f, 0.20f, 0.22f, 0.26f, 0.02f, WOOD)};
            case "furniture.shelf":
                return new float[][]{
                        box(-0.43f, 0.90f, 0, 0.02f, 0.90f, 0.15f, WOOD),
                        box(0.43f, 0.90f, 0, 0.02f, 0.90f, 0.15f, WOOD),
                        box(0, 0.90f, 0.135f, 0.45f, 0.90f, 0.015f,
                                WOOD_DARK),
                        box(0, 0.05f, 0, 0.41f, 0.02f, 0.14f, WOOD),
                        box(0, 0.62f, 0, 0.41f, 0.02f, 0.14f, WOOD),
                        box(0, 1.19f, 0, 0.41f, 0.02f, 0.14f, WOOD),
                        box(0, 1.76f, 0, 0.41f, 0.02f, 0.14f, WOOD)};
            case "furniture.cabinet":
                return new float[][]{
                        box(0, 0.95f, 0, 0.50f, 0.95f, 0.275f, WOOD),
                        box(-0.08f, 0.95f, -0.29f, 0.015f, 0.06f, 0.015f,
                                METAL),
                        box(0.08f, 0.95f, -0.29f, 0.015f, 0.06f, 0.015f,
                                METAL)};
            case "furniture.bed":
                return new float[][]{
                        box(0, 0.18f, 0, 0.475f, 0.18f, 1.0f, WOOD_DARK),
                        box(0, 0.42f, 0, 0.45f, 0.06f, 0.97f, CLOTH),
                        box(0, 0.52f, -0.75f, 0.30f, 0.05f, 0.18f,
                                new float[]{0.75f, 0.78f, 0.85f})};
            case "furniture.workbench":
                return new float[][]{
                        box(0, 0.88f, 0, 0.90f, 0.03f, 0.30f, METAL),
                        box(0, 0.42f, 0, 0.85f, 0.42f, 0.28f, GRAFITE),
                        box(0, 0.62f, -0.29f, 0.80f, 0.02f, 0.01f,
                                new float[]{0.9f, 0.6f, 0.2f})};
            case "obstacle.crate.small":
                return new float[][]{
                        box(0, 0.25f, 0, 0.25f, 0.25f, 0.25f, WOOD)};
            case "obstacle.crate.large":
                return new float[][]{
                        box(0, 0.45f, 0, 0.45f, 0.45f, 0.45f, WOOD_DARK)};
            case "obstacle.barrel":
                return new float[][]{
                        box(0, 0.45f, 0, 0.28f, 0.45f, 0.28f,
                                new float[]{0.55f, 0.22f, 0.18f}),
                        box(0, 0.20f, 0, 0.295f, 0.02f, 0.295f, METAL),
                        box(0, 0.70f, 0, 0.295f, 0.02f, 0.295f, METAL)};
            case "prop.lamp.floor":
                return new float[][]{
                        box(0, 0.025f, 0, 0.14f, 0.025f, 0.14f, GRAFITE),
                        box(0, 0.76f, 0, 0.02f, 0.72f, 0.02f, GRAFITE),
                        box(0, 1.56f, 0, 0.16f, 0.12f, 0.16f, GLOW)};
            case "prop.lamp.ceiling":
                // pendura da origem para baixo: colocar na altura do teto
                return new float[][]{
                        box(0, -0.20f, 0, 0.012f, 0.20f, 0.012f, GRAFITE),
                        box(0, -0.48f, 0, 0.09f, 0.08f, 0.09f, GLOW)};
            case "furniture.sink.kitchen":
                return new float[][]{
                        box(0, 0.44f, 0, 0.60f, 0.44f, 0.30f, WOOD_DARK),
                        box(0, 0.90f, 0, 0.62f, 0.02f, 0.31f, METAL),
                        box(0, 0.87f, -0.02f, 0.28f, 0.035f, 0.20f,
                                GRAFITE),
                        box(0, 1.02f, 0.24f, 0.015f, 0.10f, 0.015f,
                                METAL),
                        box(0, 1.11f, 0.16f, 0.015f, 0.015f, 0.09f,
                                METAL)};
            case "furniture.sink.bath":
                return new float[][]{
                        box(0, 0.35f, 0.04f, 0.08f, 0.35f, 0.08f, CERAMIC),
                        box(0, 0.76f, 0, 0.26f, 0.06f, 0.21f, CERAMIC),
                        box(0, 0.86f, 0.17f, 0.015f, 0.08f, 0.015f,
                                METAL)};
            case "furniture.toilet":
                return new float[][]{
                        box(0, 0.20f, 0.02f, 0.16f, 0.20f, 0.20f, CERAMIC),
                        box(0, 0.42f, -0.01f, 0.18f, 0.025f, 0.21f,
                                CERAMIC),
                        box(0, 0.58f, 0.24f, 0.18f, 0.22f, 0.06f,
                                CERAMIC)};
            case "furniture.wardrobe":
                return new float[][]{
                        box(0, 1.0f, 0, 0.60f, 1.0f, 0.30f, WOOD),
                        box(0, 1.0f, -0.305f, 0.007f, 0.92f, 0.007f,
                                WOOD_DARK),
                        box(-0.06f, 1.0f, -0.31f, 0.014f, 0.07f, 0.012f,
                                METAL),
                        box(0.06f, 1.0f, -0.31f, 0.014f, 0.07f, 0.012f,
                                METAL)};
            case "prop.plant.small":
                return new float[][]{
                        box(0, 0.12f, 0, 0.10f, 0.12f, 0.10f, TERRACOTA),
                        box(0, 0.38f, 0, 0.16f, 0.16f, 0.16f, LEAF)};
            case "prop.plant.tall":
                return new float[][]{
                        box(0, 0.15f, 0, 0.13f, 0.15f, 0.13f, TERRACOTA),
                        box(0, 0.52f, 0, 0.03f, 0.24f, 0.03f, WOOD_DARK),
                        box(0, 1.02f, 0, 0.22f, 0.30f, 0.22f, LEAF),
                        box(0, 1.40f, 0, 0.14f, 0.13f, 0.14f, LEAF_LIGHT)};
            default:
                return null;
        }
    }

    /** Caixas de colisão; vazio = atravessável (lâmpada de teto). */
    public static float[][] colliders(String id) {
        switch (id) {
            case "furniture.table":
                return new float[][]{{0, 0.37f, 0, 0.70f, 0.37f, 0.40f}};
            case "furniture.chair":
                return new float[][]{{0, 0.49f, 0, 0.24f, 0.49f, 0.24f}};
            case "furniture.shelf":
                return new float[][]{{0, 0.90f, 0, 0.45f, 0.90f, 0.15f}};
            case "furniture.cabinet":
                return new float[][]{{0, 0.95f, 0, 0.50f, 0.95f, 0.275f}};
            case "furniture.bed":
                return new float[][]{{0, 0.24f, 0, 0.475f, 0.24f, 1.0f}};
            case "furniture.workbench":
                return new float[][]{{0, 0.45f, 0, 0.90f, 0.45f, 0.30f}};
            case "obstacle.crate.small":
                return new float[][]{{0, 0.25f, 0, 0.25f, 0.25f, 0.25f}};
            case "obstacle.crate.large":
                return new float[][]{{0, 0.45f, 0, 0.45f, 0.45f, 0.45f}};
            case "obstacle.barrel":
                return new float[][]{{0, 0.45f, 0, 0.295f, 0.45f, 0.295f}};
            case "prop.lamp.floor":
                return new float[][]{{0, 0.80f, 0, 0.14f, 0.80f, 0.14f}};
            case "prop.lamp.ceiling":
                return new float[][]{};
            case "furniture.sink.kitchen":
                return new float[][]{{0, 0.46f, 0, 0.62f, 0.46f, 0.31f}};
            case "furniture.sink.bath":
                return new float[][]{{0, 0.42f, 0, 0.26f, 0.42f, 0.21f}};
            case "furniture.toilet":
                return new float[][]{{0, 0.40f, 0, 0.18f, 0.40f, 0.30f}};
            case "furniture.wardrobe":
                return new float[][]{{0, 1.0f, 0, 0.60f, 1.0f, 0.30f}};
            case "prop.plant.small":
                return new float[][]{{0, 0.25f, 0, 0.12f, 0.25f, 0.12f}};
            case "prop.plant.tall":
                return new float[][]{{0, 0.50f, 0, 0.15f, 0.50f, 0.15f}};
            default:
                return null;
        }
    }

    /** Meia-pegada {hx, hz} no plano (ícone da planta). */
    public static float[] footprint(String id) {
        float[][] all = parts(id);
        if (all == null) {
            return null;
        }
        float hx = 0f;
        float hz = 0f;
        for (float[] p : all) {
            hx = Math.max(hx, Math.abs(p[0]) + p[3]);
            hz = Math.max(hz, Math.abs(p[2]) + p[5]);
        }
        return new float[]{hx, hz};
    }

    private static float[] box(float cx, float cy, float cz,
                               float hx, float hy, float hz,
                               float[] color) {
        return new float[]{cx, cy, cz, hx, hy, hz,
                color[0], color[1], color[2]};
    }
}
