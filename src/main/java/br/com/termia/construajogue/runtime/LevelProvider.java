package br.com.termia.construajogue.runtime;

import java.io.IOException;

/**
 * Fonte dos níveis de uma partida. O GameRenderer não conhece caminhos:
 * a campanha vem de AssetLevelProvider e o Testar do editor vem de
 * SingleLevelProvider (mapa já compilado).
 */
public interface LevelProvider {

    int count();

    RuntimeLevel load(int index) throws IOException;
}
