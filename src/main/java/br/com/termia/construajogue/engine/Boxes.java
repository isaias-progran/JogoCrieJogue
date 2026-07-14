package br.com.termia.construajogue.engine;

/**
 * Emissão de caixas como triângulos intercalados pos+normal+cor (o formato
 * do Mesh). Usado pelo Level (cenário) e pelas malhas de drone/arma/fagulha.
 * Winding CCW visto de fora — obrigatório com back-face culling ativo.
 */
public final class Boxes {

    /** Floats emitidos por caixa: 6 faces × 2 triângulos × 3 vértices × 9. */
    public static final int FLOATS_PER_BOX = 6 * 6 * 9;

    /** {nx,ny,nz, 4 cantos (0=min 1=max)} em ordem CCW visto de fora. */
    private static final int[][] FACES = {
            {1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
            {-1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
            {0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
            {0, -1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
            {0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
            {0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
    };
    private static final int[] TRI = {0, 1, 2, 0, 2, 3};

    private Boxes() {
    }

    /** bounds = {minX,minY,minZ,maxX,maxY,maxZ}; devolve o novo cursor. */
    public static int emitBounds(float[] out, int cursor, float[] bounds,
                                 float r, float g, float b) {
        for (int[] face : FACES) {
            for (int t : TRI) {
                int corner = 3 + t * 3;
                out[cursor++] = face[corner] == 0 ? bounds[0] : bounds[3];
                out[cursor++] = face[corner + 1] == 0 ? bounds[1] : bounds[4];
                out[cursor++] = face[corner + 2] == 0 ? bounds[2] : bounds[5];
                out[cursor++] = face[0];
                out[cursor++] = face[1];
                out[cursor++] = face[2];
                out[cursor++] = r;
                out[cursor++] = g;
                out[cursor++] = b;
            }
        }
        return cursor;
    }

    /** Caixa por centro + meia-extensão (para malhas locais). */
    public static int emitCentered(float[] out, int cursor,
                                   float cx, float cy, float cz,
                                   float hx, float hy, float hz,
                                   float r, float g, float b) {
        float[] bounds = {cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz};
        return emitBounds(out, cursor, bounds, r, g, b);
    }
}
