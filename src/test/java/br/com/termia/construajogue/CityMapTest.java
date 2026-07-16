package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.editor.tools.StoryLevels;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Contrato da cidade de exemplo, incluindo rua, postes e dois andares. */
public final class CityMapTest {

    private static int checks;

    public static void main(String[] args) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(
                "src/main/assets/maps/exemplos/cidade-aurora.json")),
                StandardCharsets.UTF_8);
        check(!Files.exists(Paths.get(
                        "src/main/assets/maps/exemplos/casa.json"))
                        && !Files.exists(Paths.get(
                        "src/main/assets/maps/exemplos/patio.json"))
                        && !Files.exists(Paths.get(
                        "src/main/assets/maps/exemplos/fortaleza.json")),
                "exemplos pequenos não voltam ao pacote");
        MapDocument doc = MapJson.read(json);
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }

        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        check(issues.isEmpty(), "cidade valida sem avisos de desempenho");
        check("exemplo-cidade-aurora".equals(doc.id)
                        && "dusk".equals(doc.sky),
                "identidade e entardecer persistem");
        check(ObjectiveSpec.COLLECT.equals(doc.objective.type)
                        && doc.objective.target == 12,
                "missão exige as doze células");
        check(doc.structures.size() >= 75 && doc.structures.size() <= 80,
                "cidade ampliada permanece dentro do orçamento");

        int asphalt = 0;
        int crossings = 0;
        for (StructureObject structure : doc.structures) {
            if ("asphalt".equals(structure.material)) asphalt++;
            if (structure.id.startsWith("zebra-")) crossings++;
        }
        check(asphalt == 7, "avenidas e anel usam sete placas de asfalto");
        check(crossings == 16, "quatro travessias têm faixas visíveis");

        int streetLights = 0;
        int tokens = 0;
        int elevatedTokens = 0;
        int stairs = 0;
        int enemies = 0;
        int humans = 0;
        for (PrefabInstance prefab : doc.prefabs) {
            if ("prop.lamp.street".equals(prefab.prefabId)) {
                streetLights++;
                check(near(prefab.floatProperty("lightOffsetY", 0f), 3.35f),
                        "foco do poste fica no alto");
            }
            if ("pickup.token".equals(prefab.prefabId)) {
                tokens++;
                if (prefab.transform.y > 3.3f) elevatedTokens++;
            }
            if ("stairs.floor".equals(prefab.prefabId)) stairs++;
            if ("npc.human".equals(prefab.prefabId)) {
                humans++;
                check("Lia".equals(prefab.stringProperty("name"))
                                && prefab.stringProperty("greeting")
                                .contains("bora")
                                && prefab.stringProperty("background")
                                .contains("sem cerimônia"),
                        "Lia tem identidade e fala local descontraída");
            }
            PrefabDefinition definition = catalog.find(prefab.prefabId);
            if (definition != null && isEnemy(definition.behavior)) enemies++;
        }
        check(streetLights == 32, "trinta e dois postes cobrem as avenidas");
        check(tokens == 12 && elevatedTokens == 1,
                "todas as células contam e uma exige subir");
        check(stairs == 1, "prefeitura tem acesso ao andar superior");
        check(enemies == 17, "ameaças distribuídas sem exceder o limite");
        check(humans == 1, "cidade contém uma pessoa amigável");

        List<Float> stories = StoryLevels.discover(doc);
        check(stories.size() == 3 && contains(stories, 0f)
                        && contains(stories, 3.3f)
                        && contains(stories, 6.6f),
                "editor separa térreo, primeiro andar e cobertura");

        RuntimeLevel level = LevelCompiler.compile(doc, catalog);
        check(level.terminals().length == 2 && level.doors().length == 7,
                "sequência elétrica controla os dois portões");
        check(level.npcs().length == 1
                        && "Lia".equals(level.npcs()[0].name),
                "pessoa amigável chega ao runtime");
        check(level.hazards().length == 1
                        && level.hazards()[0][0] == RuntimeLevel.HAZARD_WATER,
                "alagamento chega ao runtime");
        check(level.lights().length == 37,
                "postes e luzes internas chegam ao renderer");
        int elevatedStreetLights = 0;
        for (float[] light : level.lights()) {
            if (near(light[1], 3.35f)) elevatedStreetLights++;
        }
        check(elevatedStreetLights == 32,
                "compilador aplica a altura luminosa dos postes");
        check(level.vertexData().length > 100_000,
                "cidade gera geometria 3D substancial");
        check(level.boxCount() < 220,
                "colliders permanecem seguros para aparelhos modestos");

        System.out.println("OK CityMapTest: " + checks
                + " verificações");
    }

    private static boolean isEnemy(String behavior) {
        return PrefabDefinition.BEHAVIOR_DRONE.equals(behavior)
                || PrefabDefinition.BEHAVIOR_DRONE_DORMANT.equals(behavior)
                || PrefabDefinition.BEHAVIOR_MUTANT.equals(behavior)
                || PrefabDefinition.BEHAVIOR_TURRET.equals(behavior)
                || PrefabDefinition.BEHAVIOR_KAMIKAZE.equals(behavior)
                || PrefabDefinition.BEHAVIOR_BOSS.equals(behavior);
    }

    private static boolean contains(List<Float> values, float wanted) {
        for (float value : values) {
            if (near(value, wanted)) return true;
        }
        return false;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
