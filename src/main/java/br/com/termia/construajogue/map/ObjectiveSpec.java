package br.com.termia.construajogue.map;

/**
 * Regra de vitória autorada no mapa. O runtime mantém o progresso;
 * o documento guarda somente a intenção e metas opcionais de estrelas.
 */
public final class ObjectiveSpec {

    public static final String REACH_EXIT = "reach_exit";
    public static final String ELIMINATE_ALL = "eliminate_all";
    public static final String COLLECT = "collect";
    public static final String SURVIVE = "survive";

    /** Uma das constantes acima. Mapas antigos caem em reach_exit. */
    public String type = REACH_EXIT;
    /** Quantidade de fichas no objetivo collect. */
    public int target;
    /** Duração do objetivo survive. */
    public float durationSeconds;
    /** Zero = sem limite; vale para qualquer tipo de objetivo. */
    public float timeLimitSeconds;
    /** Zero = meta não configurada. Uma conclusão sempre dá 1 estrela. */
    public float twoStarSeconds;
    public float threeStarSeconds;

    public ObjectiveSpec copy() {
        ObjectiveSpec copy = new ObjectiveSpec();
        copy.type = type;
        copy.target = target;
        copy.durationSeconds = durationSeconds;
        copy.timeLimitSeconds = timeLimitSeconds;
        copy.twoStarSeconds = twoStarSeconds;
        copy.threeStarSeconds = threeStarSeconds;
        return copy;
    }
}
