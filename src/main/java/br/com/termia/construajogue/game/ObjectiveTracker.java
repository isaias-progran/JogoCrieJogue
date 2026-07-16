package br.com.termia.construajogue.game;

import br.com.termia.construajogue.map.ObjectiveSpec;

/** Estado pequeno e isolado do objetivo; GameState apenas envia eventos. */
public final class ObjectiveTracker {
    private final ObjectiveSpec spec;
    private final int totalEnemies;
    private float elapsed;
    private int enemies;
    private int tokens;
    private boolean complete;
    private boolean failed;

    public ObjectiveTracker(ObjectiveSpec value, int totalEnemies) {
        spec = value == null ? new ObjectiveSpec() : value.copy();
        this.totalEnemies = totalEnemies;
    }

    public void reset() {
        elapsed = 0f;
        enemies = 0;
        tokens = 0;
        complete = false;
        failed = false;
    }

    public void tick(float dt) {
        if (complete || failed) return;
        elapsed += dt;
        if (spec.timeLimitSeconds > 0f
                && elapsed >= spec.timeLimitSeconds) {
            failed = true;
            return;
        }
        if (ObjectiveSpec.SURVIVE.equals(spec.type)
                && elapsed >= spec.durationSeconds) {
            complete = true;
        }
    }

    public void enemyEliminated() {
        if (complete || failed) return;
        enemies++;
        if (ObjectiveSpec.ELIMINATE_ALL.equals(spec.type)
                && enemies >= totalEnemies) {
            complete = true;
        }
    }

    public void tokenCollected() {
        if (complete || failed) return;
        tokens++;
        if (ObjectiveSpec.COLLECT.equals(spec.type)
                && tokens >= spec.target) {
            complete = true;
        }
    }

    public void enteredExit() {
        if (!complete && !failed
                && ObjectiveSpec.REACH_EXIT.equals(spec.type)) {
            complete = true;
        }
    }

    public boolean complete() { return complete; }
    public boolean failed() { return failed; }
    public float elapsed() { return elapsed; }
    public int enemies() { return enemies; }
    public int tokens() { return tokens; }

    public String hudText() {
        String text;
        if (ObjectiveSpec.ELIMINATE_ALL.equals(spec.type)) {
            text = "ELIMINE OS INIMIGOS  " + enemies + "/" + totalEnemies;
        } else if (ObjectiveSpec.COLLECT.equals(spec.type)) {
            text = "COLETE AS FICHAS  " + tokens + "/" + spec.target;
        } else if (ObjectiveSpec.SURVIVE.equals(spec.type)) {
            int left = Math.max(0,
                    (int) Math.ceil(spec.durationSeconds - elapsed));
            text = "SOBREVIVA  " + left + "s";
        } else {
            text = "CHEGUE À SAÍDA";
        }
        if (spec.timeLimitSeconds > 0f) {
            int left = Math.max(0,
                    (int) Math.ceil(spec.timeLimitSeconds - elapsed));
            text += "  ·  " + left + "s";
        }
        return text;
    }
}
