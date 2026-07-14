package br.com.termia.construajogue.engine;

/**
 * Interseção raio×AABB pelo método das lajes — a rotina hitBox do shader
 * do dicom3d portada para Java. Usada pelo hitscan da arma e, na Fase 3,
 * pela linha de visão dos drones. Sem alocações.
 */
public final class Raycast {

    public static final float MISS = Float.MAX_VALUE;
    private static final float EPSILON = 1e-8f;

    private Raycast() {
    }

    /**
     * Distância t até a entrada do raio na caixa {minX..maxZ},
     * ou MISS se não acerta (origem dentro da caixa devolve 0).
     */
    public static float hitBox(float ox, float oy, float oz,
                               float dx, float dy, float dz, float[] box) {
        float t0 = 0f;
        float t1 = MISS;
        for (int axis = 0; axis < 3; axis++) {
            float origin = axis == 0 ? ox : axis == 1 ? oy : oz;
            float dir = axis == 0 ? dx : axis == 1 ? dy : dz;
            float min = box[axis];
            float max = box[axis + 3];
            if (Math.abs(dir) < EPSILON) {
                if (origin < min || origin > max) {
                    return MISS;
                }
                continue;
            }
            float inv = 1f / dir;
            float near = (min - origin) * inv;
            float far = (max - origin) * inv;
            if (near > far) {
                float swap = near;
                near = far;
                far = swap;
            }
            if (near > t0) {
                t0 = near;
            }
            if (far < t1) {
                t1 = far;
            }
            if (t0 > t1) {
                return MISS;
            }
        }
        return t0;
    }

    /** Menor t contra todas as caixas do cenário (MISS se nada). */
    public static float hitBoxes(float ox, float oy, float oz,
                                 float dx, float dy, float dz,
                                 float[][] boxes) {
        float best = MISS;
        for (float[] box : boxes) {
            float t = hitBox(ox, oy, oz, dx, dy, dz, box);
            if (t < best) {
                best = t;
            }
        }
        return best;
    }
}
