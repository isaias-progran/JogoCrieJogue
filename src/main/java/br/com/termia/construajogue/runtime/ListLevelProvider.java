package br.com.termia.construajogue.runtime;

/** Campanha montada em memória a partir de vários mapas do usuário. */
public final class ListLevelProvider implements LevelProvider {

    private final RuntimeLevel[] levels;

    public ListLevelProvider(RuntimeLevel[] levels) {
        if (levels == null || levels.length == 0) {
            throw new IllegalArgumentException("campanha vazia");
        }
        this.levels = levels.clone();
    }

    @Override public int count() { return levels.length; }

    @Override
    public RuntimeLevel load(int index) {
        if (index < 0 || index >= levels.length) {
            throw new IndexOutOfBoundsException("mapa " + index);
        }
        return levels[index];
    }
}
