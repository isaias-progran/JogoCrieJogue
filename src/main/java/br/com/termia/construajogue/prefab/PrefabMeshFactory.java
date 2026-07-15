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
                        box(0, 0.72f, -0.20f, 0.22f, 0.26f, 0.02f, WOOD)};
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
