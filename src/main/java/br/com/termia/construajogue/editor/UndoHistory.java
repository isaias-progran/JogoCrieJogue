package br.com.termia.construajogue.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Histórico global por snapshots do documento em JSON (como o Scene do
 * editor3d). Barato: mapas da Fase 2 têm poucos KB. `push` recebe o
 * estado ANTES da mudança; undo/redo devolvem o JSON a restaurar.
 */
public final class UndoHistory {

    private static final int LIMIT = 50;

    private final List<String> past = new ArrayList<>();
    private final List<String> future = new ArrayList<>();

    public void push(String snapshotBefore) {
        past.add(snapshotBefore);
        if (past.size() > LIMIT) {
            past.remove(0);
        }
        future.clear();
    }

    /** @param current estado atual (vai para o refazer); null se vazio. */
    public String undo(String current) {
        if (past.isEmpty()) {
            return null;
        }
        future.add(current);
        return past.remove(past.size() - 1);
    }

    public String redo(String current) {
        if (future.isEmpty()) {
            return null;
        }
        past.add(current);
        return future.remove(future.size() - 1);
    }

    public boolean canUndo() {
        return !past.isEmpty();
    }

    public boolean canRedo() {
        return !future.isEmpty();
    }
}
