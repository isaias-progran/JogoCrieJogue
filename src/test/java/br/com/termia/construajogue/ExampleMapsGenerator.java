package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Gera os mapas de exemplo embarcados (assets/maps/exemplos/). IDs
 * determinísticos para diffs estáveis; valida e compila cada mapa
 * antes de gravar — exemplo inválido quebra o build.
 */
public final class ExampleMapsGenerator {

    private static final String DIR = "src/main/assets/maps/exemplos/";

    private ExampleMapsGenerator() {
    }

    public static void main(String[] args) throws IOException {
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }
        removeSmallExamples();
        write(catalog, complexoOmega(), "complexo-omega.json");
        write(catalog, cidadeAurora(), "cidade-aurora.json");
        System.out.println("exemplos OK em " + DIR);
    }

    /** Exemplos curtos antigos não entram mais no APK nem voltam ao gerar. */
    private static void removeSmallExamples() throws IOException {
        Files.deleteIfExists(Paths.get(DIR + "casa.json"));
        Files.deleteIfExists(Paths.get(DIR + "patio.json"));
        Files.deleteIfExists(Paths.get(DIR + "fortaleza.json"));
    }

    private static void write(PrefabCatalog catalog, MapDocument doc,
                              String file) throws IOException {
        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        if (MapValidator.hasError(issues)) {
            throw new IllegalStateException(file + ": " + issues);
        }
        LevelCompiler.compile(doc, catalog); // compila ou quebra o build
        Files.createDirectories(Paths.get(DIR));
        Files.write(Paths.get(DIR + file),
                MapJson.write(doc).getBytes(StandardCharsets.UTF_8));
    }

    /** Casa mobiliada num quintal de dia; um drone ronda lá fora. */
    private static MapDocument casa() {
        MapDocument doc = new MapDocument();
        doc.id = "exemplo-casa";
        doc.name = "Casa com quintal";
        doc.sky = "day";
        doc.ambient = 0.6f;
        doc.fogColor = new float[]{0.55f, 0.70f, 0.86f};
        doc.fogFar = 50f;

        floor(doc, "quintal", 0, 0, 8f, 6f,
                new float[]{0.32f, 0.48f, 0.30f});
        floor(doc, "casa-piso", -2f, 0, 3.2f, 2.6f,
                new float[]{0.55f, 0.44f, 0.32f});
        // casa 6,4×5,2 encostada à esquerda
        StructureObject south = wall(doc, "casa-s", -2f, 2.6f, 3.2f, true);
        WallOpening door = new WallOpening("casa-porta", WallOpening.DOOR);
        door.offset = 1.2f;
        door.width = 1.0f;
        door.height = 2.1f;
        south.openings.add(door);
        StructureObject north = wall(doc, "casa-n", -2f, -2.6f, 3.2f,
                true);
        WallOpening window = new WallOpening("casa-janela",
                WallOpening.WINDOW);
        window.offset = 0f;
        window.width = 1.4f;
        window.height = 1.2f;
        window.sill = 0.9f;
        north.openings.add(window);
        wall(doc, "casa-o", -5.2f, 0f, 2.6f, false);
        wall(doc, "casa-l", 1.2f, 0f, 2.6f, false);
        ceiling(doc, "casa-teto", -2f, 0, 3.35f, 2.75f);

        prefab(doc, "cama", "furniture.bed", -4.3f, 0f, -1.4f, 0f);
        prefab(doc, "guarda", "furniture.wardrobe", -2.5f, 0f, -2.1f, 0f);
        prefab(doc, "mesa", "furniture.table", -3f, 0f, 1.2f, 0f);
        prefab(doc, "cadeira", "furniture.chair", -3f, 0f, 0.3f, 180f);
        prefab(doc, "pia", "furniture.sink.kitchen", 0.4f, 0f, -2.05f,
                0f);
        prefab(doc, "lumi", "prop.lamp.floor", -4.5f, 0f, 1.9f, 0f);
        prefab(doc, "luz-teto", "prop.lamp.ceiling", -2f, 3.2f, 0f, 0f);
        prefab(doc, "planta", "prop.plant.tall", 0.6f, 0f, 1.9f, 0f);
        PrefabInstance drone = prefab(doc, "drone", "enemy.drone",
                5.5f, 1.8f, -4f, 0f);
        drone.properties.put("patrolX", 5.5f);
        drone.properties.put("patrolZ", 4f);
        prefab(doc, "kit", "pickup.health", 6.8f, 0.5f, 4.8f, 0f);

        spawn(doc, -4f, 0.6f, 180f);
        exit(doc, 6.8f, -4.8f, 1.2f);
        return doc;
    }

    /** Pátio noturno com caixas, luminárias e patrulhas cruzadas. */
    private static MapDocument patio() {
        MapDocument doc = new MapDocument();
        doc.id = "exemplo-patio";
        doc.name = "Pátio noturno";
        doc.sky = "night";
        doc.ambient = 0.2f;
        doc.fogColor = new float[]{0.02f, 0.03f, 0.08f};
        doc.fogFar = 26f;

        floor(doc, "chao", 0, 0, 9f, 9f,
                new float[]{0.26f, 0.28f, 0.33f});
        wall(doc, "mur-n", 0, -9f, 9f, true);
        wall(doc, "mur-s", 0, 9f, 9f, true);
        wall(doc, "mur-o", -9f, 0, 9f, false);
        wall(doc, "mur-l", 9f, 0, 9f, false);
        block(doc, "cob-1", -3f, 2f, 1.4f, 0.6f, 0.9f);
        block(doc, "cob-2", 3f, -2f, 1.4f, 0.6f, 0.9f);

        prefab(doc, "cx1", "obstacle.crate.large", -5f, 0f, -5f, 0f);
        prefab(doc, "cx2", "obstacle.crate.small", -4.2f, 0f, -5.4f, 0f);
        prefab(doc, "cx3", "obstacle.crate.large", 5f, 0f, 5f, 45f);
        prefab(doc, "barril1", "obstacle.barrel", 4.3f, 0f, 5.8f, 0f);
        prefab(doc, "barril2", "obstacle.barrel", -6f, 0f, 4f, 0f);
        prefab(doc, "lum1", "prop.lamp.floor", -2f, 0f, -2f, 0f);
        prefab(doc, "lum2", "prop.lamp.floor", 2f, 0f, 2f, 0f);
        prefab(doc, "lum3", "prop.lamp.floor", 6f, 0f, -6f, 0f);

        PrefabInstance d1 = prefab(doc, "drone1", "enemy.drone",
                -6f, 1.8f, 0f, 0f);
        d1.properties.put("patrolX", 6f);
        d1.properties.put("patrolZ", 0f);
        PrefabInstance d2 = prefab(doc, "drone2", "enemy.drone",
                0f, 2.2f, 6f, 0f);
        d2.properties.put("patrolX", 0f);
        d2.properties.put("patrolZ", -6f);
        PrefabInstance mut = prefab(doc, "mutante", "enemy.mutant",
                5f, 0.85f, -3f, 0f);
        mut.properties.put("patrolX", -5f);
        mut.properties.put("patrolZ", -3f);
        prefab(doc, "kit", "pickup.health", -2f, 0.5f, 2.6f, 0f);
        prefab(doc, "mun1", "pickup.ammo", -5f, 0.5f, -4.2f, 0f);
        prefab(doc, "mun2", "pickup.ammo", 5.8f, 0.5f, 4.3f, 0f);

        spawn(doc, -7.5f, -7.5f, 45f);
        exit(doc, 7.5f, 7.5f, 1.2f);
        return doc;
    }

    /** Terminal abre o portão; escada leva à plataforma de munição. */
    private static MapDocument fortaleza() {
        MapDocument doc = new MapDocument();
        doc.id = "exemplo-fortaleza";
        doc.name = "Fortaleza do terminal";
        doc.sky = "dusk";
        doc.ambient = 0.42f;
        doc.fogColor = new float[]{0.46f, 0.30f, 0.26f};
        doc.fogFar = 38f;

        floor(doc, "chao", 0, 0, 10f, 6f,
                new float[]{0.40f, 0.36f, 0.34f});
        wall(doc, "mur-n", 0, -6f, 10f, true);
        wall(doc, "mur-s", 0, 6f, 10f, true);
        wall(doc, "mur-o", -10f, 0, 6f, false);
        wall(doc, "mur-l", 10f, 0, 6f, false);
        // muro divisório com vão de 3m fechado pelo portão
        wall(doc, "div-n", 4f, -4f, 2f, false);
        wall(doc, "div-s", 4f, 4f, 2f, false);
        block(doc, "plataforma", -7f, -4f, 2.5f, 1.5f, 1.5f);

        PrefabInstance terminal = prefab(doc, "terminal",
                "terminal.wall", -9.6f, 1.4f, 4f, 0f);
        PrefabInstance gate = prefab(doc, "portao", "door.gate",
                4f, 1.4f, 0f, 0f);
        gate.properties.put("halfX", 0.2f);
        gate.properties.put("halfY", 1.4f);
        gate.properties.put("halfZ", 2f);
        gate.properties.put("controllerId", terminal.id);

        prefab(doc, "escada", "stairs.small", -7f, 0f, -1.3f, 180f);
        prefab(doc, "mun-alta", "pickup.ammo", -7f, 1.5f, -4f, 0f);
        prefab(doc, "kit", "pickup.health", -9f, 0.5f, -5f, 0f);
        prefab(doc, "mun", "pickup.ammo", 2f, 0.5f, 5f, 0f);
        prefab(doc, "barr1", "obstacle.barrel", -2f, 0f, 0f, 0f);
        prefab(doc, "barr2", "obstacle.crate.large", 0f, 0f, -3f, 0f);

        PrefabInstance m1 = prefab(doc, "mut1", "enemy.mutant",
                -2f, 0.85f, 3f, 0f);
        m1.properties.put("patrolX", -2f);
        m1.properties.put("patrolZ", -3f);
        PrefabInstance m2 = prefab(doc, "mut2", "enemy.mutant",
                1f, 0.85f, 1f, 0f);
        m2.properties.put("patrolX", 1f);
        m2.properties.put("patrolZ", -1f);
        PrefabInstance wave = prefab(doc, "guarda", "enemy.drone.wave",
                6f, 1.8f, 0f, 0f);
        wave.properties.put("patrolX", 8f);
        wave.properties.put("patrolZ", 0f);

        spawn(doc, -9f, 5f, 0f);
        exit(doc, 9f, 0f, 1.2f);
        return doc;
    }

    /**
     * Mapa-vitrine grande: nove núcleos, três terminais em sequência,
     * setores verticais, hazards, todos os inimigos e quase todo o catálogo.
     */
    private static MapDocument complexoOmega() {
        MapDocument doc = new MapDocument();
        doc.id = "exemplo-complexo-omega";
        doc.name = "Complexo Ômega — nove núcleos";
        doc.sky = "night";
        doc.ambient = 0.16f;
        doc.fogColor = new float[]{0.015f, 0.025f, 0.055f};
        doc.fogFar = 52f;
        doc.objective.type = ObjectiveSpec.COLLECT;
        doc.objective.target = 9;
        doc.objective.timeLimitSeconds = 600f;
        doc.objective.twoStarSeconds = 420f;
        doc.objective.threeStarSeconds = 270f;

        // Base e pisos temáticos. As placas finas ficam logo sobre a base.
        StructureObject base = surface(doc, "omega-base", 0f, 0f,
                18f, 14f, -0.24f, 0.20f, "checker",
                new float[]{0.22f, 0.24f, 0.28f});
        base.locked = true;
        surface(doc, "piso-hangar", -12.5f, -4.5f, 5.2f, 9.1f,
                -0.02f, 0.02f, "brick",
                new float[]{0.42f, 0.31f, 0.25f});
        surface(doc, "piso-alojamento", -12.5f, 9.5f, 5.2f, 4.1f,
                -0.02f, 0.02f, "wood",
                new float[]{0.48f, 0.34f, 0.20f});
        surface(doc, "piso-central", 0f, 0f, 6.7f, 6.2f,
                -0.02f, 0.02f, "metal",
                new float[]{0.34f, 0.39f, 0.45f});
        surface(doc, "piso-reator", 0f, -10.2f, 6.7f, 3.5f,
                -0.02f, 0.02f, "brick",
                new float[]{0.35f, 0.25f, 0.24f});
        surface(doc, "piso-laboratorio", 0f, 10.3f, 6.7f, 3.4f,
                -0.02f, 0.02f, "checker",
                new float[]{0.58f, 0.62f, 0.68f});
        surface(doc, "piso-leste", 12.5f, 5.8f, 5.2f, 7.8f,
                -0.02f, 0.02f, "checker",
                new float[]{0.28f, 0.35f, 0.44f});
        surface(doc, "piso-cofre", 12.5f, -9.8f, 5.2f, 3.8f,
                -0.02f, 0.02f, "metal",
                new float[]{0.31f, 0.34f, 0.39f});
        surface(doc, "agua-tratamento", -11.5f, -10.0f,
                3.2f, 1.8f, 0.03f, 0.03f, "water",
                new float[]{0.08f, 0.36f, 0.66f});
        surface(doc, "lava-reator", 0f, -10.2f,
                2.8f, 1.45f, 0.03f, 0.03f, "lava",
                new float[]{0.95f, 0.22f, 0.04f});

        float[] brick = {0.43f, 0.26f, 0.22f};
        float[] steel = {0.34f, 0.39f, 0.47f};
        float[] blue = {0.18f, 0.38f, 0.62f};
        float[] amber = {0.72f, 0.36f, 0.12f};

        // Perímetro travado, com janelas altas para dar profundidade.
        StructureObject outerN = styledWall(doc, "muro-norte", 0f, -14f,
                18f, true, "brick", brick, null, null, true);
        opening(outerN, "jan-n1", WallOpening.WINDOW,
                -12f, 1.8f, 1.0f, 1.15f);
        opening(outerN, "jan-n2", WallOpening.WINDOW,
                -4f, 1.8f, 1.0f, 1.15f);
        opening(outerN, "jan-n3", WallOpening.WINDOW,
                4f, 1.8f, 1.0f, 1.15f);
        opening(outerN, "jan-n4", WallOpening.WINDOW,
                12f, 1.8f, 1.0f, 1.15f);
        StructureObject outerS = styledWall(doc, "muro-sul", 0f, 14f,
                18f, true, "brick", brick, null, null, true);
        opening(outerS, "jan-s1", WallOpening.WINDOW,
                -12f, 1.8f, 1.0f, 1.15f);
        opening(outerS, "jan-s2", WallOpening.WINDOW,
                -4f, 1.8f, 1.0f, 1.15f);
        opening(outerS, "jan-s3", WallOpening.WINDOW,
                4f, 1.8f, 1.0f, 1.15f);
        opening(outerS, "jan-s4", WallOpening.WINDOW,
                12f, 1.8f, 1.0f, 1.15f);
        styledWall(doc, "muro-oeste", -18f, 0f, 14f, false,
                "brick", brick, null, null, true);
        styledWall(doc, "muro-leste", 18f, 0f, 14f, false,
                "brick", brick, null, null, true);

        // Eclusa A: separa o hangar do núcleo central.
        styledWall(doc, "div-a-n", -7f, -8.5f, 5.5f, false,
                "metal", steel, blue, amber, true);
        styledWall(doc, "div-a-s", -7f, 7f, 7f, false,
                "metal", steel, blue, amber, true);
        PrefabInstance alpha = terminal(doc, "terminal-alpha",
                -15.5f, 1.4f, -11.8f, 1);
        gate(doc, "eclusa-alpha", -7f, -1.5f, false, alpha.id);

        // Eclusa B: só o terminal do laboratório libera o setor leste.
        styledWall(doc, "div-b-n", 7f, -6f, 8f, false,
                "metal", steel, amber, blue, true);
        styledWall(doc, "div-b-s", 7f, 9.5f, 4.5f, false,
                "metal", steel, amber, blue, true);
        PrefabInstance beta = terminal(doc, "terminal-beta",
                3.2f, 1.4f, 11.7f, 2);
        gate(doc, "eclusa-beta", 7f, 3.5f, false, beta.id);

        // Alojamento: porta automática, janelas e rota elevada de 1 m.
        StructureObject barracks = styledWall(doc, "parede-alojamento",
                -12.5f, 5f, 5.5f, true, "wood",
                new float[]{0.48f, 0.34f, 0.22f},
                new float[]{0.68f, 0.50f, 0.30f},
                new float[]{0.30f, 0.22f, 0.16f}, false);
        opening(barracks, "porta-aloj", WallOpening.DOOR,
                0f, 1.2f, 2.1f, 0f);
        opening(barracks, "jan-aloj-o", WallOpening.WINDOW,
                -3f, 1.4f, 1.1f, 1.0f);
        opening(barracks, "jan-aloj-l", WallOpening.WINDOW,
                3f, 1.4f, 1.1f, 1.0f);
        autoDoor(doc, "porta-auto-aloj", -12.5f, 5f, true);
        ceiling(doc, "teto-alojamento", -12.5f, 9.5f, 5.35f, 4.2f);
        StructureObject deckWest = block(doc, "plataforma-oeste",
                -12f, 8f, 2.5f, 0.5f, 2f);
        deckWest.material = "wood";
        deckWest.color = new float[]{0.44f, 0.30f, 0.18f};
        prefab(doc, "rampa-curta-oeste", "ramp.short",
                -12f, 0f, 11.2f, 180f);

        // Laboratório: uma porta normal e uma abertura elevada para a rampa.
        StructureObject labWall = styledWall(doc, "parede-laboratorio",
                0f, 6.5f, 6.8f, true, "metal", steel,
                new float[]{0.62f, 0.70f, 0.78f}, blue, false);
        opening(labWall, "porta-lab", WallOpening.DOOR,
                -3f, 1.2f, 2.1f, 0f);
        opening(labWall, "janela-lab", WallOpening.WINDOW,
                1f, 1.8f, 1.2f, 0.9f);
        opening(labWall, "portal-rampa-lab", WallOpening.PORTAL,
                5f, 1.4f, 3f, 0f);
        autoDoor(doc, "porta-auto-lab", -3f, 6.5f, true);
        ceiling(doc, "teto-laboratorio", 0f, 10.25f, 6.65f, 3.6f);
        prefab(doc, "rampa-telhado", "ramp.floor", 5f, 0f, 3.5f, 0f);

        // Reator: entrada automática e duas paredes diagonais com portais.
        StructureObject reactorWall = styledWall(doc, "parede-reator",
                0f, -6.5f, 6.8f, true, "metal", steel,
                amber, blue, false);
        opening(reactorWall, "porta-reator", WallOpening.DOOR,
                3f, 1.2f, 2.1f, 0f);
        opening(reactorWall, "janela-reator", WallOpening.WINDOW,
                0f, 1.8f, 1.0f, 1.1f);
        autoDoor(doc, "porta-auto-reator", 3f, -6.5f, true);
        StructureObject diagA = diagonalWall(doc, "blindagem-diag-a",
                -5.7f, -12.9f, -1.0f, -8.0f, "brick", brick,
                new float[]{0.72f, 0.24f, 0.12f},
                new float[]{0.16f, 0.30f, 0.55f});
        opening(diagA, "portal-diag-a", WallOpening.PORTAL,
                0f, 1.4f, 2.4f, 0f);
        StructureObject diagB = diagonalWall(doc, "blindagem-diag-b",
                1.0f, -8.0f, 5.7f, -12.9f, "brick", brick,
                new float[]{0.16f, 0.30f, 0.55f},
                new float[]{0.72f, 0.24f, 0.12f});
        opening(diagB, "portal-diag-b", WallOpening.PORTAL,
                0f, 1.4f, 2.4f, 0f);

        // Plataforma central e torre leste usam os quatro tipos de acesso.
        StructureObject centerDeck = block(doc, "plataforma-central",
                0f, 1f, 2f, 0.5f, 2f);
        centerDeck.material = "metal";
        centerDeck.color = new float[]{0.30f, 0.38f, 0.46f};
        prefab(doc, "escada-central", "stairs.small", 0f, 0f,
                3.6f, 180f);
        StructureObject tower = block(doc, "torre-leste",
                12f, 8f, 3f, 1.5f, 2f);
        tower.material = "metal";
        tower.color = new float[]{0.29f, 0.34f, 0.42f};
        prefab(doc, "escada-torre", "stairs.floor", 12f, 0f,
                11.8f, 180f);
        PrefabInstance gamma = terminal(doc, "terminal-gamma",
                13.2f, 4.4f, 8f, 3);

        // Armaria e cofre: duas barreiras finais, uma automática e um portão.
        StructureObject armoryWall = styledWall(doc, "parede-armaria",
                12.5f, -2f, 5.5f, true, "metal", steel,
                blue, amber, false);
        opening(armoryWall, "porta-armaria", WallOpening.DOOR,
                -1.5f, 1.2f, 2.1f, 0f);
        opening(armoryWall, "jan-armaria", WallOpening.WINDOW,
                2.2f, 1.8f, 1.0f, 1.1f);
        autoDoor(doc, "porta-auto-armaria", 11f, -2f, true);
        ceiling(doc, "teto-armaria", 12.5f, -4f, 5.3f, 1.8f);

        StructureObject vaultFront = styledWall(doc, "parede-cofre-frente",
                12.5f, -6f, 4f, true, "metal",
                new float[]{0.27f, 0.30f, 0.36f}, amber, blue, true);
        opening(vaultFront, "portal-cofre", WallOpening.PORTAL,
                0.5f, 2.4f, 3f, 0f);
        styledWall(doc, "parede-cofre-o", 8.5f, -10f, 4f, false,
                "metal", steel, amber, blue, true);
        styledWall(doc, "parede-cofre-l", 16.5f, -10f, 4f, false,
                "metal", steel, blue, amber, true);
        gate(doc, "portao-cofre", 13f, -6f, true, gamma.id);
        ceiling(doc, "teto-cofre", 12.5f, -10f, 4.1f, 4f);

        // Nove núcleos: a rota completa exige água, rampas, telhado e cofre.
        token(doc, "nucleo-agua", -11.5f, 0.5f, -10f);
        token(doc, "nucleo-plataforma-o", -12f, 1.4f, 8f);
        token(doc, "nucleo-alojamento", -16f, 0.5f, 7.2f);
        token(doc, "nucleo-central", 0f, 1.4f, 1f);
        token(doc, "nucleo-reator", 0f, 0.5f, -12.6f);
        token(doc, "nucleo-lab", -3.5f, 0.5f, 11.5f);
        token(doc, "nucleo-telhado", 4f, 3.7f, 10f);
        token(doc, "nucleo-torre", 11f, 3.4f, 8f);
        token(doc, "nucleo-cofre", 13f, 0.5f, -11.3f);

        // Todos os arquétipos de inimigo, com patrulhas dentro dos setores.
        patrol(doc, "omega-drone-o", "enemy.drone",
                -9.5f, 1.8f, 1.5f, -15.5f, 1.5f);
        patrol(doc, "omega-mut-o", "enemy.mutant",
                -16f, 0.85f, -3f, -9f, -3f);
        patrol(doc, "omega-kami-o", "enemy.kamikaze",
                -10f, 1.8f, -6f, -15f, -6f);
        prefab(doc, "omega-torreta-agua", "enemy.turret",
                -15.5f, 0.55f, -10f, 90f);
        patrol(doc, "omega-drone-c", "enemy.drone",
                -4.5f, 1.8f, 4.7f, 4.5f, 4.7f);
        patrol(doc, "omega-mut-c", "enemy.mutant",
                -4.5f, 0.85f, -4.2f, 4.5f, -4.2f);
        patrol(doc, "omega-kami-r", "enemy.kamikaze",
                -4.5f, 1.8f, -9f, 4.5f, -9f);
        prefab(doc, "omega-torreta-r", "enemy.turret",
                0f, 0.55f, -13f, 0f);
        patrol(doc, "omega-wave-l1", "enemy.drone.wave",
                -4.5f, 1.8f, 9f, 4.5f, 9f);
        patrol(doc, "omega-wave-l2", "enemy.drone.wave",
                4f, 2.1f, 12.5f, -4f, 12.5f);
        patrol(doc, "omega-mut-l", "enemy.mutant",
                0f, 0.85f, 8f, 0f, 12.5f);
        patrol(doc, "omega-drone-leste", "enemy.drone",
                10f, 1.8f, 1f, 15.5f, 1f);
        patrol(doc, "omega-kami-leste", "enemy.kamikaze",
                9f, 1.8f, 5.5f, 15f, 5.5f);
        prefab(doc, "omega-torreta-torre", "enemy.turret",
                12f, 3.55f, 8.8f, 180f);
        prefab(doc, "omega-torreta-armaria", "enemy.turret",
                16f, 0.55f, -4f, 270f);
        prefab(doc, "omega-chefe", "enemy.boss",
                13f, 1.8f, -10f, 0f);
        patrol(doc, "omega-mut-cofre", "enemy.mutant",
                10f, 0.85f, -8f, 15.5f, -8f);
        patrol(doc, "omega-wave-cofre", "enemy.drone.wave",
                10.5f, 1.8f, -12f, 15.5f, -12f);

        // Suprimentos: o jogador precisa decidir quando gastar os especiais.
        pickup(doc, "vida-oeste", "pickup.health", -16.5f, -1f);
        pickup(doc, "vida-central", "pickup.health", -5f, 3.5f);
        pickup(doc, "vida-lab", "pickup.health", 5f, 12.5f);
        pickup(doc, "vida-cofre", "pickup.health", 9.5f, -12.5f);
        pickup(doc, "mun-oeste", "pickup.ammo", -9f, -12.5f);
        pickup(doc, "mun-central", "pickup.ammo", 5f, 0f);
        pickup(doc, "mun-reator", "pickup.ammo", -5f, -12.5f);
        pickup(doc, "mun-lab", "pickup.ammo", -5f, 8f);
        pickup(doc, "mun-armaria", "pickup.ammo", 15f, -4f);
        pickup(doc, "especial-agua", "pickup.special", -9f, -9f);
        prefab(doc, "especial-central", "pickup.special",
                0f, 1.4f, 1.8f, 0f);
        prefab(doc, "especial-telhado", "pickup.special",
                0f, 3.7f, 10f, 0f);
        pickup(doc, "especial-armaria", "pickup.special", 10f, -4f);

        // Iluminação real colorida; o renderer escolhe as quatro mais próximas.
        light(doc, "luz-aloj-1", "prop.lamp.ceiling",
                -15f, 3.1f, 10f, 1f, 0.72f, 0.42f, 7f);
        light(doc, "luz-aloj-2", "prop.lamp.ceiling",
                -10f, 3.1f, 8f, 0.65f, 0.78f, 1f, 6f);
        light(doc, "luz-agua", "prop.lamp.floor",
                -15f, 0f, -8f, 0.30f, 0.65f, 1f, 8f);
        light(doc, "luz-alpha", "prop.lamp.floor",
                -9f, 0f, -1.5f, 1f, 0.55f, 0.22f, 7f);
        light(doc, "luz-central", "prop.lamp.floor",
                0f, 1f, 1f, 0.55f, 0.75f, 1f, 8f);
        light(doc, "luz-reator-o", "prop.lamp.floor",
                -5f, 0f, -10f, 1f, 0.20f, 0.08f, 9f);
        light(doc, "luz-reator-l", "prop.lamp.floor",
                5f, 0f, -10f, 1f, 0.20f, 0.08f, 9f);
        light(doc, "luz-lab-1", "prop.lamp.ceiling",
                -3f, 3.1f, 10f, 0.55f, 0.80f, 1f, 7f);
        light(doc, "luz-lab-2", "prop.lamp.ceiling",
                3f, 3.1f, 12f, 0.72f, 0.55f, 1f, 7f);
        light(doc, "luz-torre", "prop.lamp.floor",
                12f, 3f, 7f, 1f, 0.48f, 0.18f, 10f);
        light(doc, "luz-armaria-1", "prop.lamp.ceiling",
                10f, 3.1f, -4f, 0.30f, 0.55f, 1f, 7f);
        light(doc, "luz-armaria-2", "prop.lamp.ceiling",
                15f, 3.1f, -4f, 0.30f, 0.55f, 1f, 7f);
        light(doc, "luz-cofre", "prop.lamp.ceiling",
                13f, 3.1f, -11f, 1f, 0.18f, 0.12f, 9f);
        streetLight(doc, "luz-poste-omega", 16.8f, 0f, 12.5f,
                270f, 1f, 0.68f, 0.32f, 8f);

        // Mobiliário de cada setor — também cria cobertura e silhuetas.
        prefab(doc, "mob-cama", "furniture.bed", -16f, 0f, 10.5f, 0f);
        prefab(doc, "mob-guarda", "furniture.wardrobe",
                -9f, 0f, 11.5f, 0f);
        prefab(doc, "mob-sofa", "furniture.sofa", -16f, 0f, 7.5f, 90f);
        prefab(doc, "mob-tv", "prop.tv", -17.6f, 1.4f, 7.5f, 90f);
        prefab(doc, "mob-mesa-o", "furniture.table",
                -15f, 0f, 2.5f, 0f);
        prefab(doc, "mob-cadeira-o1", "furniture.chair",
                -15f, 0f, 1.6f, 180f);
        prefab(doc, "mob-cadeira-o2", "furniture.chair",
                -14f, 0f, 2.5f, 270f);
        prefab(doc, "mob-pia-cozinha", "furniture.sink.kitchen",
                -9f, 0f, 3.8f, 0f);
        prefab(doc, "mob-prateleira-o", "furniture.shelf",
                -17f, 0f, -4f, 90f);
        prefab(doc, "mob-bancada-r", "furniture.workbench",
                -4.5f, 0f, -11.5f, 0f);
        prefab(doc, "mob-armario-r", "furniture.cabinet",
                4.8f, 0f, -12f, 0f);
        prefab(doc, "mob-bancada-l", "furniture.workbench",
                -4.5f, 0f, 9f, 180f);
        prefab(doc, "mob-armario-l", "furniture.cabinet",
                0f, 0f, 12.5f, 0f);
        prefab(doc, "mob-estante-l", "furniture.shelf",
                5.5f, 0f, 9f, 270f);
        prefab(doc, "mob-pia-banho", "furniture.sink.bath",
                5.7f, 0f, 12.5f, 270f);
        prefab(doc, "mob-vaso", "furniture.toilet",
                5.7f, 0f, 11.5f, 270f);
        prefab(doc, "mob-espelho", "prop.mirror.round",
                6.6f, 1.5f, 12.5f, 270f);
        prefab(doc, "mob-planta-p", "prop.plant.small",
                -1f, 0f, 8f, 0f);
        prefab(doc, "mob-planta-a", "prop.plant.tall",
                1f, 0f, 8f, 0f);
        prefab(doc, "mob-caixa-o1", "obstacle.crate.large",
                -9f, 0f, -6f, 0f);
        prefab(doc, "mob-caixa-o2", "obstacle.crate.small",
                -9.8f, 0f, -6.4f, 0f);
        prefab(doc, "mob-barril-o", "obstacle.barrel",
                -10.5f, 0f, -5.8f, 0f);
        prefab(doc, "mob-caixa-c1", "obstacle.crate.large",
                -4f, 0f, 2f, 0f);
        prefab(doc, "mob-caixa-c2", "obstacle.crate.small",
                4f, 0f, -2f, 0f);
        prefab(doc, "mob-barril-c", "obstacle.barrel",
                4.8f, 0f, 2.5f, 0f);
        prefab(doc, "mob-estante-arm", "furniture.shelf",
                17f, 0f, -4f, 90f);
        prefab(doc, "mob-caixa-arm1", "obstacle.crate.large",
                14f, 0f, -3.2f, 0f);
        prefab(doc, "mob-caixa-arm2", "obstacle.crate.large",
                14.8f, 0f, -3.2f, 0f);
        prefab(doc, "mob-barril-arm", "obstacle.barrel",
                9f, 0f, -4.8f, 0f);

        spawn(doc, -16f, 12.5f, 0f);
        return doc;
    }

    /**
     * Cidade aberta ao entardecer: núcleo de quatro quarteirões, anel viário,
     * bairros externos, garagem, mercado, estação e prefeitura de dois
     * andares. O apagão é resolvido recolhendo doze células, duas delas atrás
     * de uma sequência de terminais e uma no pavimento superior.
     */
    private static MapDocument cidadeAurora() {
        MapDocument doc = new MapDocument();
        doc.id = "exemplo-cidade-aurora";
        doc.name = "Cidade Aurora — apagão";
        doc.sky = "dusk";
        doc.ambient = 0.20f;
        doc.fogColor = new float[]{0.12f, 0.16f, 0.25f};
        doc.fogFar = 82f;
        doc.objective.type = ObjectiveSpec.COLLECT;
        doc.objective.target = 12;
        doc.objective.timeLimitSeconds = 720f;
        doc.objective.twoStarSeconds = 520f;
        doc.objective.threeStarSeconds = 360f;

        float[] concrete = {0.42f, 0.44f, 0.48f};
        float[] curb = {0.58f, 0.59f, 0.61f};
        float[] asphalt = {0.16f, 0.17f, 0.19f};
        float[] yellow = {0.96f, 0.72f, 0.12f};
        float[] white = {0.86f, 0.88f, 0.84f};
        float[] brick = {0.46f, 0.25f, 0.20f};
        float[] blue = {0.20f, 0.35f, 0.55f};
        float[] green = {0.25f, 0.43f, 0.31f};
        float[] steel = {0.31f, 0.35f, 0.41f};

        // Terreno, avenidas e calçadas no mesmo pavimento editável.
        StructureObject base = surface(doc, "cidade-base", 0f, 0f,
                44f, 40f, -0.28f, 0.20f, "plain", concrete);
        base.locked = true;
        StructureObject avenue = surface(doc, "avenida-ns", 0f, 0f,
                4.2f, 40f, -0.04f, 0.04f, "asphalt", asphalt);
        avenue.locked = true;
        surface(doc, "avenida-oeste", -24.1f, 0f,
                19.9f, 4.2f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;
        surface(doc, "avenida-leste", 24.1f, 0f,
                19.9f, 4.2f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;
        surface(doc, "calcada-no", -16.1f, -14.1f,
                11.7f, 9.7f, -0.04f, 0.04f, "checker", curb).locked = true;
        surface(doc, "calcada-ne", 16.1f, -14.1f,
                11.7f, 9.7f, -0.04f, 0.04f, "checker", curb).locked = true;
        surface(doc, "calcada-so", -16.1f, 14.1f,
                11.7f, 9.7f, -0.04f, 0.04f, "checker", curb).locked = true;
        surface(doc, "calcada-se", 16.1f, 14.1f,
                11.7f, 9.7f, -0.04f, 0.04f, "checker", curb).locked = true;

        // Linhas centrais interrompidas e quatro faixas de pedestres.
        for (int i = 0; i < 4; i++) {
            float z = -19f + i * 12.5f;
            surface(doc, "faixa-ns-" + i, 0f, z,
                    0.07f, 3.1f, 0.005f, 0.005f,
                    "plain", yellow).locked = true;
            float x = -21f + i * 14f;
            surface(doc, "faixa-lo-" + i, x, 0f,
                    3.1f, 0.07f, 0.005f, 0.005f,
                    "plain", yellow).locked = true;
        }
        for (int i = 0; i < 4; i++) {
            float shift = (i - 1.5f) * 0.58f;
            surface(doc, "zebra-n-" + i, 0f, -5.25f + shift,
                    3.45f, 0.11f, 0.008f, 0.008f,
                    "plain", white).locked = true;
            surface(doc, "zebra-s-" + i, 0f, 5.25f + shift,
                    3.45f, 0.11f, 0.008f, 0.008f,
                    "plain", white).locked = true;
            surface(doc, "zebra-o-" + i, -5.25f + shift, 0f,
                    0.11f, 3.45f, 0.008f, 0.008f,
                    "plain", white).locked = true;
            surface(doc, "zebra-l-" + i, 5.25f + shift, 0f,
                    0.11f, 3.45f, 0.008f, 0.008f,
                    "plain", white).locked = true;
        }

        // Um anel viário cria oito setores externos sem multiplicar objetos.
        surface(doc, "anel-oeste", -32f, 0f,
                2f, 40f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;
        surface(doc, "anel-leste", 32f, 0f,
                2f, 40f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;
        surface(doc, "anel-norte", 0f, -28f,
                44f, 2f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;
        surface(doc, "anel-sul", 0f, 28f,
                44f, 2f, -0.04f, 0.04f, "asphalt", asphalt).locked = true;

        // Volumes de skyline delimitam bairros sem o custo de interiores.
        cityMass(doc, "predio-noroeste", -18f, -35f,
                8f, 5f, 4f, "brick", brick);
        cityMass(doc, "predio-nordeste", 18f, -35f,
                8f, 6f, 4f, "metal", blue);
        cityMass(doc, "predio-sudoeste", -18f, 35f,
                8f, 4f, 4f, "wood", green);
        cityMass(doc, "predio-sudeste", 18f, 35f,
                8f, 5.5f, 4f, "brick", brick);
        cityMass(doc, "predio-oeste-n", -39f, -14f,
                4f, 4.5f, 8f, "metal", steel);
        cityMass(doc, "predio-oeste-s", -39f, 14f,
                4f, 3.5f, 8f, "brick", brick);
        cityMass(doc, "predio-leste-n", 39f, -14f,
                4f, 5.5f, 8f, "brick", brick);
        cityMass(doc, "predio-leste-s", 39f, 14f,
                4f, 4.5f, 8f, "metal", blue);

        // Fachadas externas também impedem sair da área jogável.
        styledWallAt(doc, "fachada-norte", 0f, -40f, 44f, true,
                0f, "brick", brick, null, null, true);
        styledWallAt(doc, "fachada-sul", 0f, 40f, 44f, true,
                0f, "brick", brick, null, null, true);
        styledWallAt(doc, "fachada-oeste", -44f, 0f, 40f, false,
                0f, "metal", steel, null, null, true);
        styledWallAt(doc, "fachada-leste", 44f, 0f, 40f, false,
                0f, "metal", steel, null, null, true);

        // Garagem e pátio cercado (noroeste); o terminal 1 abre o portão.
        styledWallAt(doc, "garagem-n", -19f, -21f, 5f, true,
                0f, "metal", steel, blue, null, false);
        styledWallAt(doc, "garagem-s", -19f, -13f, 5f, true,
                0f, "metal", steel, blue, null, false);
        styledWallAt(doc, "garagem-o", -24f, -17f, 4f, false,
                0f, "metal", steel, blue, null, false);
        StructureObject garageEast = styledWallAt(doc, "garagem-l",
                -14f, -17f, 4f, false, 0f,
                "metal", steel, blue, null, false);
        opening(garageEast, "garagem-porta", WallOpening.DOOR,
                0f, 2.4f, 2.3f, 0f);
        autoDoorAt(doc, "garagem-auto", -14f, -17f, false, 0f);
        ceilingAt(doc, "garagem-teto", -19f, -17f,
                4.85f, 3.85f, 0f, "metal", steel);
        styledWallAt(doc, "patio-leste", -8f, -15f, 6f, false,
                0f, "metal", steel, null, null, true);
        styledWallAt(doc, "patio-s-o", -13.1f, -9f, 0.9f, true,
                0f, "metal", steel, null, null, true);
        styledWallAt(doc, "patio-s-l", -8.9f, -9f, 0.9f, true,
                0f, "metal", steel, null, null, true);
        PrefabInstance powerA = terminal(doc, "terminal-rede-a",
                -13f, 1.4f, -7.5f, 1);
        gateAt(doc, "portao-patio", -11f, -9f,
                true, 0f, powerA.id);

        // Prefeitura (nordeste): laje do térreo vira piso do andar superior.
        styledWallAt(doc, "prefeitura-n", 16.5f, -22f, 8.5f, true,
                0f, "brick", brick, blue, null, false);
        StructureObject civicSouth = styledWallAt(doc, "prefeitura-s",
                16.5f, -9f, 8.5f, true, 0f,
                "brick", brick, blue, null, false);
        opening(civicSouth, "prefeitura-entrada", WallOpening.DOOR,
                -5f, 1.5f, 2.3f, 0f);
        opening(civicSouth, "prefeitura-escada", WallOpening.PORTAL,
                4.5f, 1.6f, 3f, 0f);
        autoDoorAt(doc, "prefeitura-auto", 11.5f, -9f,
                true, 0f);
        styledWallAt(doc, "prefeitura-o", 8f, -15.5f, 6.5f, false,
                0f, "brick", brick, blue, null, false);
        styledWallAt(doc, "prefeitura-l", 25f, -15.5f, 6.5f, false,
                0f, "brick", brick, blue, null, false);
        ceilingAt(doc, "prefeitura-laje", 16.5f, -15.5f,
                8.35f, 6.35f, 0f, "metal", steel);
        prefab(doc, "prefeitura-escada-ext", "stairs.floor",
                21f, 0f, -7.35f, 180f).locked = true;

        // Sala superior menor; chega-se pela cobertura transitável.
        styledWallAt(doc, "gabinete-n", 14f, -21f, 5f, true,
                3.3f, "brick", brick, blue, null, false);
        styledWallAt(doc, "gabinete-s", 14f, -11f, 5f, true,
                3.3f, "brick", brick, blue, null, false);
        styledWallAt(doc, "gabinete-o", 9f, -16f, 5f, false,
                3.3f, "brick", brick, blue, null, false);
        StructureObject officeEast = styledWallAt(doc, "gabinete-l",
                19f, -16f, 5f, false, 3.3f,
                "brick", brick, blue, null, false);
        opening(officeEast, "gabinete-porta", WallOpening.DOOR,
                0f, 1.3f, 2.2f, 0f);
        autoDoorAt(doc, "gabinete-auto", 19f, -16f,
                false, 3.3f);
        ceilingAt(doc, "gabinete-teto", 14f, -16f,
                4.85f, 4.85f, 3.3f, "metal", steel);
        PrefabInstance powerB = terminal(doc, "terminal-rede-b",
                12f, 1.4f, -19.5f, 2);

        // Mercado (sudoeste), completo por dentro e acessível pela avenida.
        styledWallAt(doc, "mercado-n", -17f, 10f, 7f, true,
                0f, "wood", green, brick, null, false);
        styledWallAt(doc, "mercado-s", -17f, 21f, 7f, true,
                0f, "wood", green, brick, null, false);
        styledWallAt(doc, "mercado-o", -24f, 15.5f, 5.5f, false,
                0f, "wood", green, brick, null, false);
        StructureObject marketEast = styledWallAt(doc, "mercado-l",
                -10f, 15.5f, 5.5f, false, 0f,
                "wood", green, brick, null, false);
        opening(marketEast, "mercado-entrada", WallOpening.DOOR,
                -1.5f, 1.5f, 2.3f, 0f);
        opening(marketEast, "mercado-vitrine", WallOpening.WINDOW,
                2.3f, 2.2f, 1.3f, 1.0f);
        autoDoorAt(doc, "mercado-auto", -10f, 14f,
                false, 0f);
        ceilingAt(doc, "mercado-teto", -17f, 15.5f,
                6.85f, 5.35f, 0f, "wood", green);

        // Estação (sudeste), com sala de controle e trecho alagado.
        StructureObject stationNorth = styledWallAt(doc, "estacao-n",
                17f, 11f, 7f, true, 0f,
                "metal", steel, yellow, null, false);
        opening(stationNorth, "estacao-entrada", WallOpening.DOOR,
                -4f, 1.5f, 2.3f, 0f);
        autoDoorAt(doc, "estacao-auto", 13f, 11f,
                true, 0f);
        styledWallAt(doc, "estacao-s", 17f, 21f, 7f, true,
                0f, "metal", steel, yellow, null, false);
        styledWallAt(doc, "estacao-o", 10f, 16f, 5f, false,
                0f, "metal", steel, yellow, null, false);
        styledWallAt(doc, "estacao-l", 24f, 16f, 5f, false,
                0f, "metal", steel, yellow, null, false);
        ceilingAt(doc, "estacao-teto", 17f, 16f,
                6.85f, 4.85f, 0f, "metal", steel);
        styledWallAt(doc, "controle-n", 18f, 13.2f, 2.2f, false,
                0f, "metal", steel, null, null, true);
        styledWallAt(doc, "controle-s", 18f, 19.1f, 1.9f, false,
                0f, "metal", steel, null, null, true);
        gateAt(doc, "portao-controle", 18f, 16f,
                false, 0f, powerB.id);
        surface(doc, "estacao-agua", 13f, 18f,
                2.2f, 1.6f, 0.03f, 0.03f, "water",
                new float[]{0.08f, 0.36f, 0.66f});

        // Doze células: interiores, primeiro andar e os quatro bairros novos.
        token(doc, "celula-cruzamento", 2.2f, 0.5f, 2.2f);
        token(doc, "celula-patio", -10f, 0.5f, -18f);
        token(doc, "celula-garagem", -20f, 0.5f, -17f);
        token(doc, "celula-prefeitura", 14f, 0.5f, -18f);
        token(doc, "celula-gabinete", 14f, 3.8f, -16f);
        token(doc, "celula-mercado", -21f, 0.5f, 18f);
        token(doc, "celula-alagada", 13f, 0.5f, 18f);
        token(doc, "celula-controle", 21f, 0.5f, 18f);
        token(doc, "celula-bairro-oeste", -36f, 0.5f, 0f);
        token(doc, "celula-bairro-leste", 36f, 0.5f, 0f);
        token(doc, "celula-bairro-norte", 0f, 0.5f, -34f);
        token(doc, "celula-bairro-sul", 0f, 0.5f, 34f);

        // Trânsito hostil distribuído sem bloquear a rota inicial.
        patrol(doc, "cidade-drone-ns", "enemy.drone",
                -2f, 1.8f, 12f, -2f, -12f);
        patrol(doc, "cidade-mutante-o", "enemy.mutant",
                -16f, 0.85f, 2f, -23f, 2f);
        patrol(doc, "cidade-kamikaze", "enemy.kamikaze",
                2f, 1.8f, -5f, -2f, 5f);
        prefab(doc, "cidade-torreta-n", "enemy.turret",
                3f, 0.55f, -18f, 180f);
        patrol(doc, "garagem-mutante", "enemy.mutant",
                -19f, 0.85f, -17f, -16f, -17f);
        prefab(doc, "patio-torreta", "enemy.turret",
                -9f, 0.55f, -20f, 180f);
        patrol(doc, "prefeitura-wave", "enemy.drone.wave",
                15f, 1.8f, -13f, 20f, -13f);
        patrol(doc, "gabinete-drone", "enemy.drone",
                14f, 5.1f, -14f, 14f, -19f);
        prefab(doc, "gabinete-torreta", "enemy.turret",
                17f, 3.85f, -19f, 90f);
        patrol(doc, "mercado-mutante", "enemy.mutant",
                -19f, 0.85f, 16f, -13f, 16f);
        patrol(doc, "mercado-wave", "enemy.drone.wave",
                -15f, 1.8f, 19f, -21f, 19f);
        prefab(doc, "estacao-chefe", "enemy.boss",
                21f, 1.8f, 17f, 0f);
        patrol(doc, "estacao-mutante", "enemy.mutant",
                13f, 0.85f, 14f, 13f, 19f);
        patrol(doc, "bairro-oeste-drone", "enemy.drone",
                -37f, 1.8f, 2f, -34f, -2f);
        patrol(doc, "bairro-leste-mutante", "enemy.mutant",
                37f, 0.85f, -2f, 34f, 2f);
        patrol(doc, "bairro-norte-kamikaze", "enemy.kamikaze",
                2f, 1.8f, -34f, -2f, -31f);
        patrol(doc, "bairro-sul-wave", "enemy.drone.wave",
                -2f, 1.8f, 34f, 2f, 31f);

        // Recursos e mobiliário dão cobertura e identidade aos bairros.
        pickup(doc, "cidade-vida-rua", "pickup.health", -2.5f, -8f);
        pickup(doc, "cidade-municao-rua", "pickup.ammo", 2.5f, 8f);
        pickup(doc, "cidade-especial", "pickup.special", -2.5f, 1f);
        pickup(doc, "garagem-vida", "pickup.health", -16f, -15f);
        pickup(doc, "mercado-municao", "pickup.ammo", -12f, 19f);
        pickup(doc, "estacao-especial", "pickup.special", 11.5f, 13f);
        prefab(doc, "garagem-bancada", "furniture.workbench",
                -20f, 0f, -20f, 0f);
        prefab(doc, "garagem-armario", "furniture.cabinet",
                -16f, 0f, -20f, 0f);
        prefab(doc, "patio-caixa-a", "obstacle.crate.large",
                -10f, 0f, -12f, 0f);
        prefab(doc, "patio-caixa-b", "obstacle.crate.small",
                -9.2f, 0f, -12.5f, 0f);
        prefab(doc, "patio-barril", "obstacle.barrel",
                -11.2f, 0f, -12.5f, 0f);
        prefab(doc, "prefeitura-mesa", "furniture.table",
                15f, 0f, -15f, 0f);
        prefab(doc, "prefeitura-cadeira-a", "furniture.chair",
                15f, 0f, -14f, 180f);
        prefab(doc, "prefeitura-cadeira-b", "furniture.chair",
                16.2f, 0f, -15f, 270f);
        prefab(doc, "gabinete-mesa", "furniture.table",
                14f, 3.3f, -18f, 0f);
        prefab(doc, "gabinete-cadeira", "furniture.chair",
                14f, 3.3f, -17f, 180f);
        prefab(doc, "gabinete-sofa", "furniture.sofa",
                11f, 3.3f, -14f, 90f);
        prefab(doc, "gabinete-tv", "prop.tv",
                9.2f, 4.7f, -14f, 90f);
        prefab(doc, "mercado-estante-a", "furniture.shelf",
                -22f, 0f, 13f, 90f);
        prefab(doc, "mercado-estante-b", "furniture.shelf",
                -19f, 0f, 13f, 90f);
        prefab(doc, "mercado-balcao", "furniture.sink.kitchen",
                -14f, 0f, 20f, 0f);
        prefab(doc, "mercado-mesa", "furniture.table",
                -15f, 0f, 17f, 0f);
        prefab(doc, "mercado-cadeira", "furniture.chair",
                -15f, 0f, 16f, 180f);
        prefab(doc, "estacao-banco", "furniture.sofa",
                13f, 0f, 13f, 180f);
        prefab(doc, "estacao-armario", "furniture.cabinet",
                22f, 0f, 20f, 0f);
        prefab(doc, "calcada-planta-a", "prop.plant.tall",
                6f, 0f, -6f, 0f);
        prefab(doc, "calcada-planta-b", "prop.plant.tall",
                -6f, 0f, 6f, 0f);
        PrefabInstance lia = prefab(doc, "cidade-lia", "npc.human",
                -1.4f, 0f, 20f, 180f);
        lia.properties.put("name", "Lia");
        lia.properties.put("role", "engenheira da rede elétrica");
        lia.properties.put("greeting",
                "E aí! Deu ruim na cidade; bora ligar os terminais na ordem.");
        lia.properties.put("background", "Lia cuida da rede de Cidade Aurora, "
                + "conhece a garagem, a prefeitura e a estação alagada. "
                + "É prática, parceira e fala sem cerimônia. Ela orienta o "
                + "jogador, mas não controla portas nem combate.");

        light(doc, "garagem-luz", "prop.lamp.ceiling",
                -19f, 3.1f, -17f, 0.55f, 0.72f, 1f, 7f);
        light(doc, "prefeitura-luz", "prop.lamp.ceiling",
                14f, 3.1f, -16f, 0.60f, 0.75f, 1f, 7f);
        light(doc, "gabinete-luz", "prop.lamp.ceiling",
                14f, 6.4f, -16f, 1f, 0.66f, 0.34f, 7f);
        light(doc, "mercado-luz", "prop.lamp.ceiling",
                -17f, 3.1f, 16f, 1f, 0.72f, 0.38f, 7f);
        light(doc, "estacao-luz", "prop.lamp.ceiling",
                17f, 3.1f, 16f, 0.45f, 0.65f, 1f, 7f);

        // Dezesseis postes iluminam as quatro aproximações do cruzamento.
        float[] avenuePoints = {-19f, -9f, 9f, 19f};
        for (int i = 0; i < avenuePoints.length; i++) {
            streetLight(doc, "poste-ns-o-" + i,
                    -4.7f, 0f, avenuePoints[i], 90f,
                    1f, 0.70f, 0.34f, 8.5f);
            streetLight(doc, "poste-ns-l-" + i,
                    4.7f, 0f, avenuePoints[i], 270f,
                    1f, 0.70f, 0.34f, 8.5f);
            streetLight(doc, "poste-lo-n-" + i,
                    avenuePoints[i], 0f, -4.7f, 180f,
                    1f, 0.70f, 0.34f, 8.5f);
            streetLight(doc, "poste-lo-s-" + i,
                    avenuePoints[i], 0f, 4.7f, 0f,
                    1f, 0.70f, 0.34f, 8.5f);
        }

        // Iluminação do anel: mais área, mas só quatro luzes são sombreadas.
        float[] outerPoints = {-16f, 16f};
        for (int i = 0; i < outerPoints.length; i++) {
            float point = outerPoints[i];
            streetLight(doc, "poste-anel-o-o-" + i,
                    -34.5f, 0f, point, 90f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-o-l-" + i,
                    -29.5f, 0f, point, 270f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-l-o-" + i,
                    29.5f, 0f, point, 90f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-l-l-" + i,
                    34.5f, 0f, point, 270f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-n-n-" + i,
                    point, 0f, -30.5f, 180f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-n-s-" + i,
                    point, 0f, -25.5f, 0f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-s-n-" + i,
                    point, 0f, 25.5f, 180f,
                    0.72f, 0.82f, 1f, 9f);
            streetLight(doc, "poste-anel-s-s-" + i,
                    point, 0f, 30.5f, 0f,
                    0.72f, 0.82f, 1f, 9f);
        }

        spawn(doc, 2.2f, 20f, 180f);
        return doc;
    }

    // ---- blocos de montagem ----

    private static void floor(MapDocument doc, String id, float x,
                              float z, float hx, float hz, float[] c) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_FLOOR;
        s.transform.x = x;
        s.transform.y = -0.15f;
        s.transform.z = z;
        s.half = new float[]{hx, 0.15f, hz};
        s.color = c;
        doc.structures.add(s);
    }

    private static StructureObject wall(MapDocument doc, String id,
                                        float x, float z, float halfLen,
                                        boolean alongX) {
        return wallAt(doc, id, x, z, halfLen, alongX, 0f);
    }

    private static StructureObject wallAt(MapDocument doc, String id,
                                          float x, float z, float halfLen,
                                          boolean alongX, float baseY) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_WALL;
        s.transform.x = x;
        s.transform.y = baseY + 1.5f;
        s.transform.z = z;
        float half = halfLen + 0.15f;
        s.half = alongX ? new float[]{half, 1.5f, 0.15f}
                : new float[]{0.15f, 1.5f, half};
        s.color = new float[]{0.46f, 0.48f, 0.55f};
        doc.structures.add(s);
        return s;
    }

    private static void ceiling(MapDocument doc, String id, float x,
                                float z, float hx, float hz) {
        ceilingAt(doc, id, x, z, hx, hz, 0f, "plain",
                new float[]{0.38f, 0.41f, 0.48f});
    }

    private static StructureObject ceilingAt(MapDocument doc, String id,
                                              float x, float z,
                                              float hx, float hz,
                                              float baseY, String material,
                                              float[] color) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_CEILING;
        s.transform.x = x;
        s.transform.y = baseY + 3.15f;
        s.transform.z = z;
        s.half = new float[]{hx, 0.15f, hz};
        s.material = material;
        s.color = color.clone();
        doc.structures.add(s);
        return s;
    }

    private static StructureObject block(MapDocument doc, String id, float x,
                                         float z, float hx, float hy,
                                         float hz) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_BLOCK;
        s.transform.x = x;
        s.transform.y = hy;
        s.transform.z = z;
        s.half = new float[]{hx, hy, hz};
        s.color = new float[]{0.62f, 0.45f, 0.30f};
        doc.structures.add(s);
        return s;
    }

    private static StructureObject cityMass(MapDocument doc, String id,
                                            float x, float z,
                                            float hx, float height,
                                            float hz, String material,
                                            float[] color) {
        StructureObject value = block(doc, id, x, z,
                hx, height / 2f, hz);
        value.material = material;
        value.color = color.clone();
        value.locked = true;
        return value;
    }

    private static StructureObject surface(MapDocument doc, String id,
                                            float x, float z,
                                            float hx, float hz,
                                            float y, float halfY,
                                            String material, float[] color) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_FLOOR;
        s.transform.x = x;
        s.transform.y = y;
        s.transform.z = z;
        s.half = new float[]{hx, halfY, hz};
        s.material = material;
        s.color = color.clone();
        doc.structures.add(s);
        return s;
    }

    private static StructureObject styledWall(MapDocument doc, String id,
                                               float x, float z,
                                               float halfLen,
                                               boolean alongX,
                                               String material, float[] base,
                                               float[] positive,
                                               float[] negative,
                                               boolean locked) {
        StructureObject s = wall(doc, id, x, z, halfLen, alongX);
        s.material = material;
        s.color = base.clone();
        s.color2 = positive == null ? null : positive.clone();
        s.color3 = negative == null ? null : negative.clone();
        s.locked = locked;
        return s;
    }

    private static StructureObject styledWallAt(MapDocument doc, String id,
                                                 float x, float z,
                                                 float halfLen,
                                                 boolean alongX,
                                                 float baseY,
                                                 String material,
                                                 float[] base,
                                                 float[] positive,
                                                 float[] negative,
                                                 boolean locked) {
        StructureObject s = wallAt(doc, id, x, z, halfLen, alongX, baseY);
        s.material = material;
        s.color = base.clone();
        s.color2 = positive == null ? null : positive.clone();
        s.color3 = negative == null ? null : negative.clone();
        s.locked = locked;
        return s;
    }

    private static StructureObject diagonalWall(MapDocument doc, String id,
                                                 float ax, float az,
                                                 float bx, float bz,
                                                 String material,
                                                 float[] base,
                                                 float[] positive,
                                                 float[] negative) {
        float length = (float) Math.hypot(bx - ax, bz - az);
        float nx = -(bz - az) / length * 0.15f;
        float nz = (bx - ax) / length * 0.15f;
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_POLY);
        s.role = StructureObject.ROLE_WALL;
        s.transform.y = 1.5f;
        s.half = new float[]{0f, 1.5f, 0f};
        s.polygon = new float[]{ax + nx, az + nz, bx + nx, bz + nz,
                bx - nx, bz - nz, ax - nx, az - nz};
        s.material = material;
        s.color = base.clone();
        s.color2 = positive.clone();
        s.color3 = negative.clone();
        s.syncPolyBounds();
        doc.structures.add(s);
        return s;
    }

    private static WallOpening opening(StructureObject wall, String id,
                                       String type, float offset,
                                       float width, float height,
                                       float sill) {
        WallOpening o = new WallOpening(id, type);
        o.offset = offset;
        o.width = width;
        o.height = height;
        o.sill = sill;
        wall.openings.add(o);
        return o;
    }

    private static PrefabInstance terminal(MapDocument doc, String id,
                                           float x, float y, float z,
                                           int order) {
        PrefabInstance p = prefab(doc, id, "terminal.wall", x, y, z, 0f);
        p.properties.put("order", (float) order);
        return p;
    }

    /** alongX = folha larga no eixo X; false = larga no eixo Z. */
    private static PrefabInstance gate(MapDocument doc, String id,
                                       float x, float z, boolean alongX,
                                       String controllerId) {
        return gateAt(doc, id, x, z, alongX, 0f, controllerId);
    }

    private static PrefabInstance gateAt(MapDocument doc, String id,
                                         float x, float z, boolean alongX,
                                         float baseY, String controllerId) {
        PrefabInstance p = prefab(doc, id, "door.gate",
                x, baseY + 1.4f, z, 0f);
        p.properties.put("halfX", alongX ? 1.2f : 0.16f);
        p.properties.put("halfY", 1.4f);
        p.properties.put("halfZ", alongX ? 0.16f : 1.45f);
        p.properties.put("controllerId", controllerId);
        return p;
    }

    private static PrefabInstance autoDoor(MapDocument doc, String id,
                                           float x, float z,
                                           boolean alongX) {
        return autoDoorAt(doc, id, x, z, alongX, 0f);
    }

    private static PrefabInstance autoDoorAt(MapDocument doc, String id,
                                             float x, float z,
                                             boolean alongX, float baseY) {
        PrefabInstance p = prefab(doc, id, "door.auto",
                x, baseY + 1.05f, z, 0f);
        p.properties.put("halfX", alongX ? 0.60f : 0.09f);
        p.properties.put("halfY", 1.05f);
        p.properties.put("halfZ", alongX ? 0.09f : 0.60f);
        return p;
    }

    private static PrefabInstance patrol(MapDocument doc, String id,
                                         String prefabId, float x, float y,
                                         float z, float targetX,
                                         float targetZ) {
        PrefabInstance p = prefab(doc, id, prefabId, x, y, z, 0f);
        p.properties.put("patrolX", targetX);
        p.properties.put("patrolZ", targetZ);
        return p;
    }

    private static PrefabInstance token(MapDocument doc, String id,
                                        float x, float y, float z) {
        return prefab(doc, id, "pickup.token", x, y, z, 0f);
    }

    private static PrefabInstance pickup(MapDocument doc, String id,
                                         String prefabId, float x, float z) {
        return prefab(doc, id, prefabId, x, 0.5f, z, 0f);
    }

    private static PrefabInstance light(MapDocument doc, String id,
                                        String prefabId, float x, float y,
                                        float z, float r, float g, float b,
                                        float radius) {
        PrefabInstance p = prefab(doc, id, prefabId, x, y, z, 0f);
        p.properties.put("lightR", r);
        p.properties.put("lightG", g);
        p.properties.put("lightB", b);
        p.properties.put("lightRadius", radius);
        p.locked = true;
        return p;
    }

    private static PrefabInstance streetLight(MapDocument doc, String id,
                                              float x, float y, float z,
                                              float yaw, float r, float g,
                                              float b, float radius) {
        PrefabInstance p = light(doc, id, "prop.lamp.street",
                x, y, z, r, g, b, radius);
        p.transform.yaw = yaw;
        p.properties.put("lightOffsetY", 3.35f);
        return p;
    }

    private static PrefabInstance prefab(MapDocument doc, String id,
                                         String prefabId, float x,
                                         float y, float z, float yaw) {
        PrefabInstance p = new PrefabInstance(id, prefabId);
        p.transform.x = x;
        p.transform.y = y;
        p.transform.z = z;
        p.transform.yaw = yaw;
        doc.prefabs.add(p);
        return p;
    }

    private static void spawn(MapDocument doc, float x, float z,
                              float yaw) {
        LogicMarker m = new LogicMarker("spawn", LogicMarker.PLAYER_SPAWN);
        m.x = x;
        m.z = z;
        m.yaw = yaw;
        doc.markers.add(m);
    }

    private static void exit(MapDocument doc, float x, float z,
                             float radius) {
        LogicMarker m = new LogicMarker("exit", LogicMarker.EXIT);
        m.x = x;
        m.z = z;
        m.radius = radius;
        doc.markers.add(m);
    }
}
