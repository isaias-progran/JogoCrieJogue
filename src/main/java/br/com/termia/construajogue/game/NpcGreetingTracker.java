package br.com.termia.construajogue.game;

import java.util.Arrays;

/** Garante no máximo uma saudação automática por NPC em cada tentativa. */
public final class NpcGreetingTracker {

    private final boolean[] greeted;

    public NpcGreetingTracker(int count) {
        greeted = new boolean[Math.max(0, count)];
    }

    public boolean firstApproach(int index) {
        if (index < 0 || index >= greeted.length || greeted[index]) {
            return false;
        }
        greeted[index] = true;
        return true;
    }

    public void reset() {
        Arrays.fill(greeted, false);
    }
}
