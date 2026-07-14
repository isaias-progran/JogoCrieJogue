package br.com.termia.construajogue.runtime;

/** Partida de um mapa só, já compilado (Testar do editor e biblioteca). */
public final class SingleLevelProvider implements LevelProvider {

    private final RuntimeLevel level;

    public SingleLevelProvider(RuntimeLevel level) {
        this.level = level;
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public RuntimeLevel load(int index) {
        return level;
    }
}
