package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
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
        write(catalog, casa(), "casa.json");
        write(catalog, patio(), "patio.json");
        write(catalog, fortaleza(), "fortaleza.json");
        System.out.println("exemplos OK em " + DIR);
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
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_WALL;
        s.transform.x = x;
        s.transform.y = 1.5f;
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
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_CEILING;
        s.transform.x = x;
        s.transform.y = 3.15f;
        s.transform.z = z;
        s.half = new float[]{hx, 0.15f, hz};
        s.color = new float[]{0.38f, 0.41f, 0.48f};
        doc.structures.add(s);
    }

    private static void block(MapDocument doc, String id, float x,
                              float z, float hx, float hy, float hz) {
        StructureObject s = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        s.role = StructureObject.ROLE_BLOCK;
        s.transform.x = x;
        s.transform.y = hy;
        s.transform.z = z;
        s.half = new float[]{hx, hy, hz};
        s.color = new float[]{0.62f, 0.45f, 0.30f};
        doc.structures.add(s);
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
