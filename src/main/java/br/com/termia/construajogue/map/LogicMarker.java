package br.com.termia.construajogue.map;

/** Marcador invisível de lógica: início, saída ou ponto de patrulha. */
public final class LogicMarker {

    public static final String PLAYER_SPAWN = "player_spawn";
    public static final String EXIT = "exit";
    public static final String PATROL_POINT = "patrol_point";

    public String id;
    public String type;
    public float x;
    public float y;
    public float z;
    /** Direção inicial do jogador (player_spawn). */
    public float yaw;
    /** Raio da zona de vitória (exit). */
    public float radius;
    /** Trava persistente do editor. */
    public boolean locked;

    public LogicMarker() {
    }

    public LogicMarker(String id, String type) {
        this.id = id;
        this.type = type;
    }
}
