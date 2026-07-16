package br.com.termia.construajogue.runtime;

import br.com.termia.construajogue.map.ObjectiveSpec;

/**
 * Nível pronto para jogar: imutável (exceto o collider da porta, que o
 * GameState anima), produzido pelo LegacyLevelLoader (.txt) ou pelo
 * LevelCompiler (MapDocument JSON). Puro Java — os VBOs sobem depois,
 * na thread GL.
 */
public final class RuntimeLevel {

    public static final int ITEM_HEALTH = 0;
    public static final int ITEM_AMMO = 1;
    public static final int ITEM_TOKEN = 2;
    public static final int ITEM_SPECIAL = 3;
    public static final int ITEM_WEAPON_SMG = 4;
    public static final int ITEM_WEAPON_SHOTGUN = 5;
    public static final int ITEM_WEAPON_RIFLE = 6;

    public static final int HAZARD_WATER = 1;
    public static final int HAZARD_LAVA = 2;

    public static final int ENEMY_TURRET = 2;
    public static final int ENEMY_KAMIKAZE = 3;
    public static final int ENEMY_BOSS = 4;

    public static final int SKY_NONE = 0;
    public static final int SKY_DAY = 1;
    public static final int SKY_DUSK = 2;
    public static final int SKY_NIGHT = 3;

    public static final int SOUNDSCAPE_OUTDOOR = 0;
    public static final int SOUNDSCAPE_TUNNEL = 1;
    public static final int SOUNDSCAPE_INDUSTRIAL = 2;

    private final float[][] colliders;
    private final float[] vertexData;
    private final float[] doorVertexData;
    private final int doorIndex;
    private final float[] doorOriginal;
    private final float[] terminal;
    private final float[] exit;
    /** Compilador JSON conhece Y mesmo quando mantém o array legado em Y=0. */
    private float exitElevation;
    private boolean exitElevationKnown;
    private final float[][] items;
    private final float[] spawn;
    private final float[][] droneSpawns;
    private final float[][] waveSpawns;
    private final float[][] mutantSpawns;
    private final float ambient;
    private final float[] fogColor;
    private final float fogFar;
    private int skyMode = SKY_NONE;
    private int soundscapeMode = SOUNDSCAPE_OUTDOOR;
    private RuntimeTerminal[] terminals = new RuntimeTerminal[0];
    private RuntimeDoor[] doors = new RuntimeDoor[0];
    /** Novos inimigos: {tipo, x, y, z, x2, z2}. */
    private float[][] extraEnemySpawns = new float[0][];
    /** {tipo,minX,minY,minZ,maxX,maxY,maxZ}; aceita o formato legado 2D. */
    private float[][] hazards = new float[0][];
    /** {x,y,z,r,g,b,raio}. */
    private float[][] lights = new float[0][];
    /** Pessoas amigáveis; não recebem comandos de baixo nível da rede. */
    private RuntimeNpc[] npcs = new RuntimeNpc[0];
    private String mapId;
    private String mapName = "";
    private ObjectiveSpec objective = new ObjectiveSpec();

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
        if (terminal != null) {
            terminals = new RuntimeTerminal[]{new RuntimeTerminal(
                    "legacy-terminal", terminal[0], terminal[1], terminal[2],
                    0)};
        }
        if (doorOriginal != null) {
            doors = new RuntimeDoor[]{new RuntimeDoor("legacy-door",
                    terminal == null ? null : "legacy-terminal", doorIndex,
                    doorOriginal, doorVertexData, 0f,
                    -(doorOriginal[4] - doorOriginal[1]), 0f, false)};
        }
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

    /** {x,z,raio} legado ou {x,y,z,raio} vertical, ou null. */
    public float[] exit() {
        return exit;
    }

    public float exitElevation() {
        return exitElevation;
    }

    public boolean exitElevationKnown() {
        return exitElevationKnown;
    }

    /** Chamado pelo compilador JSON; níveis TXT antigos continuam 2D. */
    public void setExitElevation(float y) {
        exitElevation = y;
        exitElevationKnown = true;
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

    public int soundscapeMode() {
        return soundscapeMode;
    }

    /** Chamado apenas pelo compilador, logo após construir. */
    public void setSoundscapeMode(int mode) {
        soundscapeMode = mode;
    }

    public int boxCount() {
        return colliders.length;
    }

    public RuntimeTerminal[] terminals() {
        return terminals;
    }

    public RuntimeDoor[] doors() {
        return doors;
    }

    public void setInteractive(RuntimeTerminal[] terminals,
                               RuntimeDoor[] doors) {
        this.terminals = terminals == null
                ? new RuntimeTerminal[0] : terminals;
        this.doors = doors == null ? new RuntimeDoor[0] : doors;
    }

    public float[][] extraEnemySpawns() {
        return extraEnemySpawns;
    }

    public void setExtraEnemySpawns(float[][] spawns) {
        extraEnemySpawns = spawns == null ? new float[0][] : spawns;
    }

    public float[][] hazards() {
        return hazards;
    }

    public void setHazards(float[][] value) {
        hazards = value == null ? new float[0][] : value;
    }

    public float[][] lights() {
        return lights;
    }

    public void setLights(float[][] value) {
        lights = value == null ? new float[0][] : value;
    }

    public RuntimeNpc[] npcs() {
        return npcs;
    }

    public void setNpcs(RuntimeNpc[] value) {
        npcs = value == null ? new RuntimeNpc[0] : value;
    }

    public void setMetadata(String id, String name, ObjectiveSpec value) {
        mapId = id;
        mapName = name == null ? "" : name;
        objective = value == null ? new ObjectiveSpec() : value.copy();
    }

    public String mapId() {
        return mapId;
    }

    public String mapName() {
        return mapName;
    }

    public ObjectiveSpec objective() {
        return objective;
    }

    /** Multiplicador de velocidade do jogador na posição atual. */
    public float speedMultiplierAt(float x, float z) {
        for (float[] h : hazards) {
            if (h[0] == HAZARD_WATER && horizontalContains(h, x, z)) {
                return 0.48f;
            }
        }
        return 1f;
    }

    public float speedMultiplierAt(float x, float y, float z) {
        for (float[] h : hazards) {
            if (h[0] == HAZARD_WATER && horizontalContains(h, x, z)
                    && verticalContains(h, y)) return 0.48f;
        }
        return 1f;
    }

    public boolean inHazard(int type, float x, float z) {
        for (float[] h : hazards) {
            if (h[0] == type && horizontalContains(h, x, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean inHazard(int type, float x, float y, float z) {
        for (float[] h : hazards) {
            if (h[0] == type && horizontalContains(h, x, z)
                    && verticalContains(h, y)) return true;
        }
        return false;
    }

    private static boolean horizontalContains(float[] h, float x, float z) {
        if (h.length >= 7) {
            return x >= h[1] && x <= h[4] && z >= h[3] && z <= h[6];
        }
        return h.length >= 5 && x >= h[1] && x <= h[3]
                && z >= h[2] && z <= h[4];
    }

    private static boolean verticalContains(float[] h, float y) {
        // Arrays antigos não guardavam Y e continuam globais por definição.
        return h.length < 7 || (y >= h[2] - 0.20f && y <= h[5] + 0.45f);
    }
}
