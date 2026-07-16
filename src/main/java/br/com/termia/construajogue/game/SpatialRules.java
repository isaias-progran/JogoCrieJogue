package br.com.termia.construajogue.game;

/** Regras espaciais pequenas e testáveis, compartilhadas pelo GameState. */
public final class SpatialRules {

    private SpatialRules() {
    }

    /**
     * Formato legado: {x,z,raio}. Formato vertical: {x,y,z,raio}.
     * O limite de Y impede concluir uma saída através de outro pavimento.
     */
    public static boolean insideExit(float[] exit, float x, float y, float z) {
        if (exit == null) return false;
        if (exit.length >= 4) {
            float horizontal = (float) Math.hypot(exit[0] - x, exit[2] - z);
            return horizontal < exit[3] && Math.abs(exit[1] - y) < 1.25f;
        }
        return exit.length >= 3
                && Math.hypot(exit[0] - x, exit[1] - z) < exit[2];
    }

    /** Acrescenta Y conhecido a um array legado de três valores. */
    public static boolean insideExit(float[] exit, float knownY,
                                     boolean yIsKnown,
                                     float x, float y, float z) {
        if (exit != null && exit.length == 3 && yIsKnown) {
            return Math.hypot(exit[0] - x, exit[1] - z) < exit[2]
                    && Math.abs(knownY - y) < 1.25f;
        }
        return insideExit(exit, x, y, z);
    }

    public static float distance(float ax, float ay, float az,
                                 float bx, float by, float bz) {
        float dx = ax - bx;
        float dy = ay - by;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
