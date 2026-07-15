package br.com.termia.construajogue.runtime;

/**
 * Nível pronto para jogar: imutável (exceto o collider da porta, que o
 * GameState anima), produzido pelo LegacyLevelLoader (.txt) ou pelo
 * LevelCompiler (MapDocument JSON). Puro Java — os VBOs sobem depois,
 * na thread GL.
 */
public final class RuntimeLevel {

    public static final int ITEM_HEALTH = 0;
    public static final int ITEM_AMMO = 1;

    public static final int SKY_NONE = 0;
    public static final int SKY_DAY = 1;
    public static final int SKY_DUSK = 2;
    public static final int SKY_NIGHT = 3;

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
    private int skyMode = SKY_NONE;

    public RuntimeLevel(float[][] colliders, float[] vertexData,
                        float[] doorVertexData, int doorIndex,
                        float[] doorOriginal, float[] terminal, float[] exit,
                        float[][] items, float[] spawn,
                        float[][] droneSpawns, float[][] waveSpawns,
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

    /** Uma das constantes SKY_*; NONE = só a cor da neblina. */
    public int skyMode() {
        return skyMode;
    }

    /** Chamado apenas pelo compilador, logo após construir. */
    public void setSkyMode(int mode) {
        skyMode = mode;
    }

    public int boxCount() {
        return colliders.length;
    }
}
