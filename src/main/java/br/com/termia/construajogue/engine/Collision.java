package br.com.termia.construajogue.engine;

/**
 * Colisão do jogador (cilindro aproximado por AABB, pos = pés) contra as
 * caixas do cenário. Movimento resolvido eixo a eixo (PLANO.md seção 7):
 * primeiro X, depois Z (com degrau pequeno), por fim Y (gravidade).
 * Caixa = float[6]: {minX, minY, minZ, maxX, maxY, maxZ}.
 * Sem alocações: tudo opera sobre arrays já existentes.
 */
public final class Collision {

    private Collision() {
    }

    /** Comparações estritas: pés exatamente sobre o topo NÃO colidem. */
    public static boolean overlaps(float[] pos, float radius, float height,
                                   float[] box) {
        return pos[0] - radius < box[3] && pos[0] + radius > box[0]
                && pos[1] < box[4] && pos[1] + height > box[1]
                && pos[2] - radius < box[5] && pos[2] + radius > box[2];
    }

    private static boolean overlapsAny(float[] pos, float radius, float height,
                                       float[][] boxes) {
        for (float[] box : boxes) {
            if (overlaps(pos, radius, height, box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Move no eixo horizontal (axis 0 = X, axis 2 = Z) e resolve colisões.
     * Obstáculo com topo até `step` acima dos pés vira degrau: sobe e segue,
     * desde que a nova altura não esbarre em mais nada.
     */
    public static void moveHorizontal(float[] pos, int axis, float delta,
                                      float radius, float height, float step,
                                      float[][] boxes) {
        if (delta == 0f) {
            return;
        }
        pos[axis] += delta;
        for (float[] box : boxes) {
            if (!overlaps(pos, radius, height, box)) {
                continue;
            }
            float rise = box[4] - pos[1];
            if (rise > 0f && rise <= step) {
                float oldY = pos[1];
                pos[1] = box[4];
                if (!overlapsAny(pos, radius, height, boxes)) {
                    continue; // subiu o degrau
                }
                pos[1] = oldY;
            }
            if (delta > 0f) {
                pos[axis] = box[axis] - radius;
            } else {
                pos[axis] = box[axis + 3] + radius;
            }
        }
    }

    /**
     * Move no eixo vertical. Devolve 1 se pousou no chão, -1 se bateu a
     * cabeça, 0 se não colidiu.
     */
    public static int moveVertical(float[] pos, float delta, float radius,
                                   float height, float[][] boxes) {
        pos[1] += delta;
        int hit = 0;
        for (float[] box : boxes) {
            if (!overlaps(pos, radius, height, box)) {
                continue;
            }
            if (delta <= 0f) {
                pos[1] = box[4];
                hit = 1;
            } else {
                pos[1] = box[1] - height;
                hit = -1;
            }
        }
        return hit;
    }
}
