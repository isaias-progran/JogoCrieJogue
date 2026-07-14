package br.com.termia.construajogue.prefab;

import java.util.ArrayList;
import java.util.List;

/**
 * Definição de uma peça pronta do catálogo. O mapa referencia só o `id`;
 * aparência, collider e comportamento moram aqui (ou no código que
 * interpreta `behavior`), nunca duplicados no documento.
 */
public final class PrefabDefinition {

    /** Comportamentos que o LevelCompiler entende na Fase 1. */
    public static final String BEHAVIOR_DRONE = "drone";
    public static final String BEHAVIOR_DRONE_DORMANT = "drone_dormant";
    public static final String BEHAVIOR_MUTANT = "mutant";
    public static final String BEHAVIOR_PICKUP_HEALTH = "pickup_health";
    public static final String BEHAVIOR_PICKUP_AMMO = "pickup_ammo";
    public static final String BEHAVIOR_TERMINAL = "terminal";
    public static final String BEHAVIOR_DOOR = "door";

    public String id;
    public String name;
    public String category;
    public String behavior;
    /** Nomes de propriedades editáveis permitidas na instância. */
    public final List<String> properties = new ArrayList<>();

    public boolean allowsProperty(String property) {
        return properties.contains(property);
    }
}
