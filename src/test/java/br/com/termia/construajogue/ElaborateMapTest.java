package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/** Contrato da vitrine: impede que o mapa grande perca recursos sem aviso. */
public final class ElaborateMapTest {

    private static int checks;

    public static void main(String[] args) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(
                "src/main/assets/maps/exemplos/complexo-omega.json")),
                StandardCharsets.UTF_8);
        MapDocument doc = MapJson.read(json);
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }

        check(ObjectiveSpec.COLLECT.equals(doc.objective.type)
                        && doc.objective.target == 9,
                "objetivo usa os nove núcleos");
        check(doc.structures.size() >= 34, "mapa tem muitos setores");
        check(doc.prefabs.size() >= 90, "mapa usa extensamente o catálogo");
        check(!MapValidator.hasError(MapValidator.validate(doc, catalog)),
                "mapa elaborado valida");

        Set<String> materials = new HashSet<>();
        boolean diagonalOpening = false;
        boolean locked = false;
        int openings = 0;
        for (StructureObject structure : doc.structures) {
            materials.add(structure.material);
            openings += structure.openings.size();
            diagonalOpening |= WallGeometry.diagonal(structure)
                    && !structure.openings.isEmpty();
            locked |= structure.locked;
        }
        check(materials.contains("plain") && materials.contains("brick")
                        && materials.contains("wood")
                        && materials.contains("checker")
                        && materials.contains("metal")
                        && materials.contains("water")
                        && materials.contains("lava"),
                "todos os materiais aparecem");
        check(openings >= 20 && diagonalOpening,
                "vãos retos e diagonais aparecem");
        check(locked, "travas persistentes aparecem");

        Set<String> enemyBehaviors = new HashSet<>();
        Set<String> pickupBehaviors = new HashSet<>();
        Set<String> prefabIds = new HashSet<>();
        int terminals = 0;
        int doors = 0;
        int lights = 0;
        int elevatedItems = 0;
        for (PrefabInstance prefab : doc.prefabs) {
            prefabIds.add(prefab.prefabId);
            PrefabDefinition def = catalog.find(prefab.prefabId);
            if (def == null) continue;
            if (def.behavior.startsWith("drone")
                    || PrefabDefinition.BEHAVIOR_MUTANT.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_TURRET.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_KAMIKAZE.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_BOSS.equals(def.behavior)) {
                enemyBehaviors.add(def.behavior);
            }
            if (def.behavior.startsWith("pickup")) {
                pickupBehaviors.add(def.behavior);
                if (prefab.transform.y > 3f) elevatedItems++;
            }
            if (PrefabDefinition.BEHAVIOR_TERMINAL.equals(def.behavior)) {
                terminals++;
            }
            if (PrefabDefinition.BEHAVIOR_DOOR.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_AUTO_DOOR
                    .equals(def.behavior)) {
                doors++;
            }
            if (prefab.prefabId.startsWith("prop.lamp.")) lights++;
        }
        check(enemyBehaviors.size() == 6,
                "todos os comportamentos inimigos aparecem");
        check(pickupBehaviors.size() == 4,
                "todos os tipos de item aparecem");
        boolean allStaticPrefabs = true;
        for (PrefabDefinition def : catalog.all()) {
            if (PrefabDefinition.BEHAVIOR_STATIC.equals(def.behavior)) {
                allStaticPrefabs &= prefabIds.contains(def.id);
            }
        }
        check(allStaticPrefabs, "todo o catálogo estático aparece");
        check(terminals == 3 && doors == 7,
                "sequência de terminais e sete portas");
        check(lights >= 13, "iluminação distribuída");
        check(elevatedItems >= 2, "exploração vertical é obrigatória");

        RuntimeLevel level = LevelCompiler.compile(doc, catalog);
        check(level.doors().length == 7 && level.terminals().length == 3,
                "lógica sobrevive à compilação");
        check(level.hazards().length == 2 && level.lights().length >= 13,
                "hazards e luzes chegam ao runtime");
        check(level.extraEnemySpawns().length >= 7,
                "novos inimigos chegam ao runtime");
        System.out.println("OK ElaborateMapTest: " + checks
                + " verificações");
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
