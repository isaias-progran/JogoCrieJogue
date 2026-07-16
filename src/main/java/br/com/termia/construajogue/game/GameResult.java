package br.com.termia.construajogue.game;

import br.com.termia.construajogue.map.ObjectiveSpec;

/** Resultado imutável entregue da thread GL para persistência/UI. */
public final class GameResult {
    public final String mapId;
    public final String mapName;
    public final float seconds;
    public final int shots;
    public final int hits;
    public final int kills;
    public final int health;
    public final int stars;

    public GameResult(String mapId, String mapName, float seconds,
                      int shots, int hits, int kills, int health,
                      ObjectiveSpec spec) {
        this.mapId = mapId;
        this.mapName = mapName;
        this.seconds = seconds;
        this.shots = shots;
        this.hits = hits;
        this.kills = kills;
        this.health = health;
        stars = starsFor(seconds, health, spec);
    }

    public float accuracy() {
        return shots == 0 ? 1f : (float) hits / shots;
    }

    /**
     * No survive o tempo decorrido é sempre durationSeconds (metas de
     * tempo virariam constantes); a vida restante é o que mede a partida.
     */
    public static final int SURVIVE_TWO_STAR_HEALTH = 40;
    public static final int SURVIVE_THREE_STAR_HEALTH = 80;

    private static int starsFor(float seconds, int health,
                                ObjectiveSpec value) {
        ObjectiveSpec spec = value == null ? new ObjectiveSpec() : value;
        if (ObjectiveSpec.SURVIVE.equals(spec.type)) {
            if (health >= SURVIVE_THREE_STAR_HEALTH) return 3;
            if (health >= SURVIVE_TWO_STAR_HEALTH) return 2;
            return 1;
        }
        int stars = 1;
        if (spec.twoStarSeconds > 0f && seconds <= spec.twoStarSeconds) {
            stars = 2;
        }
        if (spec.threeStarSeconds > 0f && seconds <= spec.threeStarSeconds) {
            stars = 3;
        }
        return stars;
    }
}
