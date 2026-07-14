package br.com.termia.construajogue.game;

import android.content.res.AssetManager;

import br.com.termia.construajogue.engine.Boxes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Nível carregado de texto em assets/levels (formato do PLANO.md seção 9).
 * Comandos: `# comentário`, `ambient a`, `fog r g b dist`,
 * `spawn x y z yawGraus`, `box cx cy cz hx hy hz r g b`,
 * `drone x y z x2 z2` (ativo), `wave x y z x2 z2` (dormente até o alarme),
 * `mutant x y z x2 z2` (inimigo terrestre de ataque próximo),
 * `terminal x y z` (ponto de interação/luz), `door cx cy cz hx hy hz`
 * (portão que desce ao ativar o terminal; colide e bloqueia visão),
 * `exit x z raio` (zona de vitória), `item health|ammo x y z`.
 * O parse acontece uma vez; após perda de contexto só as malhas re-sobem.
 */
public final class Level {

    public static final int ITEM_HEALTH = 0;
    public static final int ITEM_AMMO = 1;

    private final float[][] colliders;
    private final float[] vertexData;
    private final float[] doorVertexData;
    private final int doorIndex;
    private final float[] doorOriginal;
    private final float[] terminal;
    private final float[] exit;
    private final float[][] items;
    private final float[] spawn;
    private final float[][] droneSpawns;
    private final float[][] waveSpawns;
    private final float[][] mutantSpawns;
    private final float ambient;
    private final float[] fogColor;
    private final float fogFar;

    private Level(float[][] colliders, float[] vertexData,
                  float[] doorVertexData, int doorIndex, float[] doorOriginal,
                  float[] terminal, float[] exit, float[][] items,
                  float[] spawn, float[][] droneSpawns, float[][] waveSpawns,
                  float[][] mutantSpawns, float ambient,
                  float[] fogColor, float fogFar) {
        this.colliders = colliders;
        this.vertexData = vertexData;
        this.doorVertexData = doorVertexData;
        this.doorIndex = doorIndex;
        this.doorOriginal = doorOriginal;
        this.terminal = terminal;
        this.exit = exit;
        this.items = items;
        this.spawn = spawn;
        this.droneSpawns = droneSpawns;
        this.waveSpawns = waveSpawns;
        this.mutantSpawns = mutantSpawns;
        this.ambient = ambient;
        this.fogColor = fogColor;
        this.fogFar = fogFar;
    }

    public static Level load(AssetManager assets, String path)
            throws IOException {
        List<float[]> boxes = new ArrayList<>();
        List<float[]> drones = new ArrayList<>();
        List<float[]> waves = new ArrayList<>();
        List<float[]> mutants = new ArrayList<>();
        List<float[]> items = new ArrayList<>();
        float[] door = null;
        float[] terminal = null;
        float[] exit = null;
        float[] spawn = {0f, 0f, 0f, 0f};
        float ambient = 0.35f;
        float[] fogColor = {0.04f, 0.05f, 0.07f};
        float fogFar = 30f;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(path), StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                try {
                    switch (parts[0]) {
                        case "box":
                            boxes.add(new float[]{
                                    f(parts[1]), f(parts[2]), f(parts[3]),
                                    f(parts[4]), f(parts[5]), f(parts[6]),
                                    f(parts[7]), f(parts[8]), f(parts[9])});
                            break;
                        case "drone":
                            drones.add(new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3]), f(parts[4]), f(parts[5])});
                            break;
                        case "wave":
                            waves.add(new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3]), f(parts[4]), f(parts[5])});
                            break;
                        case "mutant":
                            mutants.add(new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3]), f(parts[4]), f(parts[5])});
                            break;
                        case "terminal":
                            terminal = new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3])};
                            break;
                        case "door":
                            door = new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3]), f(parts[4]), f(parts[5]),
                                    f(parts[6])};
                            break;
                        case "exit":
                            exit = new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3])};
                            break;
                        case "item":
                            int type = "health".equals(parts[1])
                                    ? ITEM_HEALTH : ITEM_AMMO;
                            items.add(new float[]{type, f(parts[2]),
                                    f(parts[3]), f(parts[4])});
                            break;
                        case "spawn":
                            spawn = new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3]), f(parts[4])};
                            break;
                        case "ambient":
                            ambient = f(parts[1]);
                            break;
                        case "fog":
                            fogColor = new float[]{f(parts[1]), f(parts[2]),
                                    f(parts[3])};
                            fogFar = f(parts[4]);
                            break;
                        default:
                            throw new IOException("comando desconhecido");
                    }
                } catch (RuntimeException bad) {
                    throw new IOException(path + " linha " + number
                            + ": " + line);
                }
            }
        }
        if (boxes.isEmpty()) {
            throw new IOException(path + ": nenhum box");
        }

        int colliderCount = boxes.size() + (door == null ? 0 : 1);
        float[][] colliders = new float[colliderCount][6];
        float[] vertexData = new float[boxes.size() * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        for (int i = 0; i < boxes.size(); i++) {
            float[] b = boxes.get(i);
            toBounds(b[0], b[1], b[2], b[3], b[4], b[5], colliders[i]);
            cursor = Boxes.emitBounds(vertexData, cursor, colliders[i],
                    b[6], b[7], b[8]);
        }

        // porta: colide (último índice) mas tem malha própria que desce
        int doorIndex = -1;
        float[] doorVertexData = null;
        float[] doorOriginal = null;
        if (door != null) {
            doorIndex = boxes.size();
            toBounds(door[0], door[1], door[2], door[3], door[4], door[5],
                    colliders[doorIndex]);
            doorOriginal = colliders[doorIndex].clone();
            doorVertexData = new float[Boxes.FLOATS_PER_BOX];
            Boxes.emitBounds(doorVertexData, 0, doorOriginal,
                    0.46f, 0.50f, 0.60f);
        }

        return new Level(colliders, vertexData, doorVertexData, doorIndex,
                doorOriginal, terminal, exit,
                items.toArray(new float[0][]), spawn,
                drones.toArray(new float[0][]),
                waves.toArray(new float[0][]),
                mutants.toArray(new float[0][]), ambient, fogColor, fogFar);
    }

    private static void toBounds(float cx, float cy, float cz,
                                 float hx, float hy, float hz, float[] out6) {
        out6[0] = cx - hx;
        out6[1] = cy - hy;
        out6[2] = cz - hz;
        out6[3] = cx + hx;
        out6[4] = cy + hy;
        out6[5] = cz + hz;
    }

    private static float f(String token) {
        return Float.parseFloat(token);
    }

    /** Inclui a porta (mutável pelo GameState durante a abertura). */
    public float[][] colliders() {
        return colliders;
    }

    public float[] vertexData() {
        return vertexData;
    }

    public float[] doorVertexData() {
        return doorVertexData;
    }

    public int doorIndex() {
        return doorIndex;
    }

    /** Bounds originais da porta fechada (para animar/reiniciar). */
    public float[] doorOriginal() {
        return doorOriginal;
    }

    /** Ponto de interação/luz do terminal, ou null. */
    public float[] terminal() {
        return terminal;
    }

    /** {x, z, raio} da zona de vitória, ou null. */
    public float[] exit() {
        return exit;
    }

    /** Cada linha: {tipo, x, y, z}. */
    public float[][] items() {
        return items;
    }

    /** {x, y, z, yaw em graus}. */
    public float[] spawn() {
        return spawn;
    }

    /** Drones ativos desde o início: {x, y, z, x2, z2}. */
    public float[][] droneSpawns() {
        return droneSpawns;
    }

    /** Drones dormentes que acordam no alarme do terminal. */
    public float[][] waveSpawns() {
        return waveSpawns;
    }

    /** Mutantes ativos: {x, y, z, x2, z2}. */
    public float[][] mutantSpawns() {
        return mutantSpawns;
    }

    public float ambient() {
        return ambient;
    }

    public float[] fogColor() {
        return fogColor;
    }

    public float fogFar() {
        return fogFar;
    }

    public int boxCount() {
        return colliders.length;
    }
}
