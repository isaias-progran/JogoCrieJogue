package br.com.termia.construajogue;

import br.com.termia.construajogue.game.Enemy;
import br.com.termia.construajogue.game.Kamikaze;
import br.com.termia.construajogue.game.Mutant;

/** Ataques de contato não podem atravessar um vão vertical entre andares. */
public final class VerticalEnemyTest {
    private static int checks;

    public static void main(String[] args) {
        float[] spawn = {0f, 0.85f, 0f, 0f, 0f};
        Mutant lower = new Mutant(spawn);
        boolean hit = false;
        for (int i = 0; i < 120; i++) {
            hit |= lower.update(0.02f, i * 0.02f, new float[0][],
                    0f, 4.9f, 0f, true) == Enemy.EV_ATTACK_HIT;
        }
        check(!hit, "mutante não golpeia jogador no andar superior");

        Mutant nearby = new Mutant(spawn);
        hit = false;
        for (int i = 0; i < 120; i++) {
            hit |= nearby.update(0.02f, i * 0.02f, new float[0][],
                    0f, 1.6f, 0f, true) == Enemy.EV_ATTACK_HIT;
        }
        check(hit, "mutante continua golpeando no mesmo andar");

        Kamikaze upperSafe = new Kamikaze(
                new float[]{0f, 1.8f, 0f, 0f, 0f});
        upperSafe.wake();
        check(upperSafe.update(0.02f, 0f, new float[0][],
                        0f, 4.9f, 0f, true) != Enemy.EV_ATTACK_HIT,
                "kamikaze não explode através do pavimento");

        System.out.println("OK VerticalEnemyTest: " + checks
                + " verificações");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
