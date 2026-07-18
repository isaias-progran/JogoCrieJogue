package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Raycast;
import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.util.Arrays;

/**
 * Linha de visada inimigo↔aliado calculada no máximo UMA vez por par
 * dentro do mesmo quadro: a escolha de alvo do inimigo e a mira do aliado
 * consultam o mesmo resultado. O cache NUNCA atravessa quadros — segurar
 * a visada entre quadros mudaria a mira; o GameState o esvazia no início
 * de cada um via beginFrame.
 */
public final class AllySight {

    private static final byte CLEAR = 1;
    private static final byte BLOCKED = 2;

    private byte[] cells = new byte[0];
    private int allyCount = 1;

    /** Esvazia o cache; chamar uma única vez no início de cada quadro. */
    public void beginFrame(int enemies, int allies) {
        allyCount = Math.max(1, allies);
        int size = Math.max(1, enemies) * allyCount;
        if (cells.length < size) {
            cells = new byte[size];
        } else {
            Arrays.fill(cells, (byte) 0);
        }
    }

    /** true quando nenhum collider bloqueia a reta inimigo↔olho do aliado. */
    public boolean clear(int enemyIndex, int allyIndex, Enemy enemy,
                         RuntimeNpc ally, float[][] colliders) {
        int cell = enemyIndex * allyCount + allyIndex;
        byte cached = cells[cell];
        if (cached == 0) {
            cached = compute(enemy, ally, colliders) ? CLEAR : BLOCKED;
            cells[cell] = cached;
        }
        return cached == CLEAR;
    }

    /** Mesma reta e mesma margem das duas rotas antigas. */
    private static boolean compute(Enemy enemy, RuntimeNpc ally,
                                   float[][] colliders) {
        float dx = ally.x - enemy.x();
        float dy = ally.y + NpcCompanion.EYE_HEIGHT - enemy.y();
        float dz = ally.z - enemy.z();
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.01f) return true;
        float wall = Raycast.hitBoxes(enemy.x(), enemy.y(), enemy.z(),
                dx / distance, dy / distance, dz / distance, colliders);
        return wall >= distance - 0.1f;
    }
}
