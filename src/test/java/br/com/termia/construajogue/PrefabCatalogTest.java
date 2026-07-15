package br.com.termia.construajogue;

import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;

import java.io.FileInputStream;
import java.io.IOException;

public final class PrefabCatalogTest {

    private static final String PATH =
            "src/main/assets/prefabs/catalog.json";

    public static void main(String[] args) throws IOException {
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(PATH)) {
            catalog = PrefabCatalog.load(input);
        }
        Check.that(catalog.size() >= 7, "catálogo com as peças da fase 1");

        String[][] expected = {
                {"enemy.drone", PrefabDefinition.BEHAVIOR_DRONE},
                {"enemy.drone.wave",
                        PrefabDefinition.BEHAVIOR_DRONE_DORMANT},
                {"enemy.mutant", PrefabDefinition.BEHAVIOR_MUTANT},
                {"pickup.health", PrefabDefinition.BEHAVIOR_PICKUP_HEALTH},
                {"pickup.ammo", PrefabDefinition.BEHAVIOR_PICKUP_AMMO},
                {"terminal.wall", PrefabDefinition.BEHAVIOR_TERMINAL},
                {"door.gate", PrefabDefinition.BEHAVIOR_DOOR},
        };
        for (String[] pair : expected) {
            PrefabDefinition def = catalog.find(pair[0]);
            Check.that(def != null, "existe " + pair[0]);
            Check.equal(def.behavior, pair[1],
                    "comportamento de " + pair[0]);
            Check.that(!def.name.isEmpty() && !def.category.isEmpty(),
                    pair[0] + " tem nome e categoria");
        }
        Check.that(catalog.find("enemy.drone").allowsProperty("patrolX"),
                "drone permite patrolX");
        Check.that(!catalog.find("pickup.health").allowsProperty("patrolX"),
                "kit não permite patrolX");
        Check.that(catalog.find("door.gate").allowsProperty("controllerId"),
                "porta permite controllerId");
        Check.that(catalog.find("nao.existe") == null, "id inexistente");

        // toda peça estática do catálogo tem malha, collider e pegada
        int statics = 0;
        for (PrefabDefinition def : catalog.all()) {
            if (!PrefabDefinition.BEHAVIOR_STATIC.equals(def.behavior)) {
                continue;
            }
            statics++;
            Check.that(PrefabMeshFactory.parts(def.id) != null,
                    def.id + " tem malha");
            Check.that(PrefabMeshFactory.colliders(def.id) != null,
                    def.id + " tem colliders");
            Check.that(PrefabMeshFactory.footprint(def.id) != null,
                    def.id + " tem pegada");
        }
        Check.that(statics >= 11, "móveis/obstáculos/objetos no catálogo");

        Check.fails(() -> PrefabCatalog.parse(
                "{\"prefabs\": [{\"id\": \"x\", \"name\": \"X\", "
                        + "\"category\": \"c\", \"behavior\": \"b\"}, "
                        + "{\"id\": \"x\", \"name\": \"X\", "
                        + "\"category\": \"c\", \"behavior\": \"b\"}]}"),
                "id duplicado");
        Check.fails(() -> PrefabCatalog.parse("{\"prefabs\": [{}]}"),
                "campos obrigatórios");
        Check.done("PrefabCatalogTest");
    }
}
