package br.com.termia.construajogue.persistence;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Playlist única "Minha campanha", na ordem escolhida pelo usuário. */
public final class CampaignStore {

    private static final String KEY = "map_ids";
    private final SharedPreferences preferences;

    public CampaignStore(Context context) {
        preferences = context.getSharedPreferences("user_campaign", 0);
    }

    public List<String> get() {
        String raw = preferences.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (String id : raw.split("\\n")) {
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    public void set(List<String> ids) {
        StringBuilder raw = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.indexOf('\n') >= 0) continue;
            if (raw.length() > 0) raw.append('\n');
            raw.append(id);
        }
        preferences.edit().putString(KEY, raw.toString()).apply();
    }
}
