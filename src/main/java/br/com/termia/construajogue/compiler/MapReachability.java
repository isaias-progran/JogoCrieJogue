package br.com.termia.construajogue.compiler;

import br.com.termia.construajogue.runtime.RuntimeDoor;
import br.com.termia.construajogue.runtime.RuntimeLevel;

/**
 * Verifica uma rota horizontal simples entre início e saída. É deliberadamente
 * conservador: rampas e rotas entre andares ficam para o jogo, portanto uma
 * falha aqui só pode produzir aviso, nunca bloquear um mapa válido.
 */
final class MapReachability {

    private static final float CELL = 0.6f;
    private static final float HEIGHT = 1.75f;
    private static final float STEP_HEIGHT = 0.35f;
    private static final float WORLD_LIMIT = 50f;

    private MapReachability() {
    }

    static boolean hasSimpleRoute(RuntimeLevel level) {
        float[] spawn = level.spawn();
        float[] exit = level.exit();
        if (spawn == null || exit == null) return false;
        float exitX = exit[0];
        float exitY = exit.length >= 4 ? exit[1]
                : level.exitElevationKnown() ? level.exitElevation() : 0f;
        float exitZ = exit.length >= 4 ? exit[2] : exit[1];
        float exitRadius = exit.length >= 4 ? exit[3] : exit[2];
        if (Math.abs(spawn[1] - exitY) > STEP_HEIGHT + 0.05f) {
            return true; // um analisador 2D não deve julgar rampas/escadas
        }

        float[][] boxes = level.colliders();
        boolean[] doors = new boolean[boxes.length];
        for (RuntimeDoor door : level.doors()) {
            if (door.colliderIndex >= 0 && door.colliderIndex < doors.length) {
                doors[door.colliderIndex] = true; // portas podem abrir
            }
        }

        float lowX = Math.min(spawn[0], exitX);
        float highX = Math.max(spawn[0], exitX);
        float lowZ = Math.min(spawn[2], exitZ);
        float highZ = Math.max(spawn[2], exitZ);
        for (int i = 0; i < boxes.length; i++) {
            if (doors[i]) continue;
            float[] box = boxes[i];
            // Só caixas que possam servir de piso ou obstáculo neste andar.
            if (box[1] >= spawn[1] + HEIGHT
                    || box[4] < spawn[1] - STEP_HEIGHT - 0.1f) continue;
            lowX = Math.min(lowX, box[0]);
            highX = Math.max(highX, box[3]);
            lowZ = Math.min(lowZ, box[2]);
            highZ = Math.max(highZ, box[5]);
        }
        lowX = Math.max(-WORLD_LIMIT, lowX - CELL);
        highX = Math.min(WORLD_LIMIT, highX + CELL);
        lowZ = Math.max(-WORLD_LIMIT, lowZ - CELL);
        highZ = Math.min(WORLD_LIMIT, highZ + CELL);

        // Ancora a grade exatamente no spawn para não perder portas estreitas.
        int left = (int) Math.ceil((spawn[0] - lowX) / CELL);
        int front = (int) Math.ceil((spawn[2] - lowZ) / CELL);
        float originX = spawn[0] - left * CELL;
        float originZ = spawn[2] - front * CELL;
        int width = (int) Math.ceil((highX - originX) / CELL) + 1;
        int depth = (int) Math.ceil((highZ - originZ) / CELL) + 1;
        if (width <= 0 || depth <= 0 || width * depth > 40000) return true;

        int startX = left;
        int startZ = front;
        byte[] state = rasterize(boxes, doors, spawn[1], originX, originZ,
                width, depth);
        int[] queue = new int[state.length];
        int head = 0;
        int tail = 0;
        int start = startZ * width + startX;
        // Célula inicial estranha (spawn colado em parede, apoio fora da
        // faixa) é inconclusiva: um rasterizador de 0,6 m não deve acusar
        // um mapa jogável — só avisa quando anda a partir de célula boa.
        if ((state[start] & 3) != 1) return true;
        state[start] |= 4;
        queue[tail++] = start;
        float targetRadius = Math.max(exitRadius, CELL * 0.75f);
        float targetRadius2 = targetRadius * targetRadius;
        final int[] dx = {1, -1, 0, 0};
        final int[] dz = {0, 0, 1, -1};
        while (head < tail) {
            int index = queue[head++];
            int gx = index % width;
            int gz = index / width;
            float x = originX + gx * CELL;
            float z = originZ + gz * CELL;
            float tx = x - exitX;
            float tz = z - exitZ;
            if (tx * tx + tz * tz <= targetRadius2) return true;
            for (int direction = 0; direction < 4; direction++) {
                int nx = gx + dx[direction];
                int nz = gz + dz[direction];
                if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                    continue;
                }
                int next = nz * width + nx;
                if ((state[next] & 4) != 0 || (state[next] & 3) != 1) {
                    continue;
                }
                state[next] |= 4;
                queue[tail++] = next;
            }
        }
        return false;
    }

    /** Bit 1 = apoio; bit 2 = obstáculo; bit 4 = visitado pela busca. */
    private static byte[] rasterize(float[][] boxes, boolean[] doors,
                                    float y, float originX, float originZ,
                                    int width, int depth) {
        byte[] cells = new byte[width * depth];
        for (int i = 0; i < boxes.length; i++) {
            if (doors[i]) continue;
            float[] box = boxes[i];
            if (box[4] >= y - STEP_HEIGHT - 0.05f
                    && box[4] <= y + STEP_HEIGHT + 0.01f) {
                mark(cells, width, depth, originX, originZ,
                        box[0], box[3], box[2], box[5], (byte) 1);
            }
            if (y < box[4] && y + HEIGHT > box[1]
                    && box[4] - y > STEP_HEIGHT + 0.001f) {
                // Sem inflar pelo raio do jogador: com células de 0,6 m a
                // inflação fecharia vãos legais de ~1 m e geraria alarme
                // falso; passagem justa passa batida, o que é aceitável num
                // teste que só pode avisar.
                mark(cells, width, depth, originX, originZ,
                        box[0], box[3], box[2], box[5], (byte) 2);
            }
        }
        return cells;
    }

    private static void mark(byte[] cells, int width, int depth,
                             float originX, float originZ,
                             float minX, float maxX, float minZ, float maxZ,
                             byte bit) {
        int firstX = Math.max(0,
                (int) Math.ceil((minX - originX) / CELL));
        int lastX = Math.min(width - 1,
                (int) Math.floor((maxX - originX) / CELL));
        int firstZ = Math.max(0,
                (int) Math.ceil((minZ - originZ) / CELL));
        int lastZ = Math.min(depth - 1,
                (int) Math.floor((maxZ - originZ) / CELL));
        for (int z = firstZ; z <= lastZ; z++) {
            int row = z * width;
            for (int x = firstX; x <= lastX; x++) {
                cells[row + x] |= bit;
            }
        }
    }
}
