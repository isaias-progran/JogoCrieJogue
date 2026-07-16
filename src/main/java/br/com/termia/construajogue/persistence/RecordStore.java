package br.com.termia.construajogue.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import br.com.termia.construajogue.game.GameResult;

/** Melhores tempo, precisão e estrelas, indexados pelo UUID do mapa. */
public final class RecordStore {
    public static final class Record {
        public final float bestSeconds;
        public final float bestAccuracy;
        public final int stars;
        public final int completions;

        Record(float bestSeconds, float bestAccuracy, int stars,
               int completions) {
            this.bestSeconds = bestSeconds;
            this.bestAccuracy = bestAccuracy;
            this.stars = stars;
            this.completions = completions;
        }

        public boolean exists() { return completions > 0; }
    }

    private final SharedPreferences prefs;

    public RecordStore(Context context) {
        prefs = context.getSharedPreferences("map_records",
                Context.MODE_PRIVATE);
    }

    public Record get(String mapId) {
        if (mapId == null) return new Record(0f, 0f, 0, 0);
        String p = mapId + ".";
        return new Record(prefs.getFloat(p + "time", 0f),
                prefs.getFloat(p + "accuracy", 0f),
                prefs.getInt(p + "stars", 0),
                prefs.getInt(p + "completions", 0));
    }

    public void record(GameResult result) {
        if (result == null || result.mapId == null) return;
        Record old = get(result.mapId);
        float bestTime = old.bestSeconds == 0f
                ? result.seconds : Math.min(old.bestSeconds, result.seconds);
        float bestAccuracy = Math.max(old.bestAccuracy, result.accuracy());
        String p = result.mapId + ".";
        prefs.edit()
                .putFloat(p + "time", bestTime)
                .putFloat(p + "accuracy", bestAccuracy)
                .putInt(p + "stars", Math.max(old.stars, result.stars))
                .putInt(p + "completions", old.completions + 1)
                .apply();
    }
}
