package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/** Recorte de vãos: passagem realmente livre e laterais/verga sólidas. */
public final class WallOpeningTest {

    public static void main(String[] args) throws IOException {
        // parede de 8m (eixo X) × 3m de altura, centro na origem
        StructureObject wall = new StructureObject("w",
                StructureObject.KIND_BLOCK);
        wall.role = StructureObject.ROLE_WALL;
        wall.transform.y = 1.5f;
        wall.half = new float[]{4f, 1.5f, 0.15f};
        wall.color = new float[]{0.5f, 0.5f, 0.5f};

        WallOpening door = new WallOpening("d", WallOpening.DOOR);
        door.offset = -1f;
        door.width = 1.0f;
        door.height = 2.1f;
        wall.openings.add(door);
        WallOpening window = new WallOpening("j", WallOpening.WINDOW);
        window.offset = 2f;
        window.width = 1.2f;
        window.height = 1.2f;
        window.sill = 0.9f;
        wall.openings.add(window);

        float[] bounds = new float[6];
        LegacyLevelLoader.toBounds(wall.transform.x, wall.transform.y,
                wall.transform.z, wall.half[0], wall.half[1], wall.half[2],
                bounds);
        List<float[]> pieces = LevelCompiler.cutOpenings(wall, bounds);
        // 3 trechos cheios + verga da porta + peitoril e verga da janela
        Check.equal(pieces.size(), 6, "número de pedaços");

        Check.that(free(pieces, -1f, 1.0f), "porta atravessável");
        Check.that(free(pieces, 2f, 1.2f), "janela vazada no meio");
        Check.that(!free(pieces, -3f, 1.0f), "trecho esquerdo sólido");
        Check.that(!free(pieces, 0.5f, 1.0f), "trecho central sólido");
        Check.that(!free(pieces, 2f, 0.4f), "peitoril sólido embaixo");
        Check.that(!free(pieces, -1f, 2.5f), "verga sólida em cima");
        Check.that(!free(pieces, 2f, 2.5f), "verga da janela sólida");

        // documento completo: valida, persiste e compila sem erro
        MapDocument doc = new MapDocument();
        doc.id = "map-vaos";
        doc.name = "vãos";
        StructureObject floor = new StructureObject("f",
                StructureObject.KIND_BLOCK);
        floor.role = StructureObject.ROLE_FLOOR;
        floor.transform.y = -0.15f;
        floor.half = new float[]{6f, 0.15f, 6f};
        floor.color = new float[]{0.3f, 0.3f, 0.3f};
        doc.structures.add(floor);
        doc.structures.add(wall);
        br.com.termia.construajogue.map.LogicMarker spawn =
                new br.com.termia.construajogue.map.LogicMarker("s",
                        br.com.termia.construajogue.map.LogicMarker
                                .PLAYER_SPAWN);
        spawn.z = 3f;
        doc.markers.add(spawn);
        br.com.termia.construajogue.map.LogicMarker exit =
                new br.com.termia.construajogue.map.LogicMarker("e",
                        br.com.termia.construajogue.map.LogicMarker.EXIT);
        exit.z = -3f;
        exit.radius = 1f;
        doc.markers.add(exit);

        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }
        Check.that(!MapValidator.hasError(
                MapValidator.validate(doc, catalog)), "documento válido");
        // parede pintada por lado: mesma contagem, sem quebrar nada
        wall.color2 = new float[]{0.8f, 0.2f, 0.2f};
        MapDocument back = MapJson.read(MapJson.write(doc));
        Check.equal(back.structures.get(1).openings.size(), 2,
                "vãos persistidos");
        Check.that(LevelCompiler.compile(back, catalog)
                .boxCount() == 7, "compila piso + 6 pedaços");

        // móvel: visual detalhado, mas UM collider simplificado
        br.com.termia.construajogue.map.PrefabInstance table =
                new br.com.termia.construajogue.map.PrefabInstance(
                        "mesa", "furniture.table");
        table.transform.x = 2f;
        doc.prefabs.add(table);
        Check.that(!MapValidator.hasError(
                MapValidator.validate(doc, catalog)), "mesa válida");
        Check.that(LevelCompiler.compile(doc, catalog).boxCount() == 8,
                "mesa soma 1 collider (7 estruturais + 1)");
        // girar 90°: collider da mesa troca largura × profundidade
        float[] flat = LevelCompiler.compile(doc, catalog).colliders()[7];
        table.transform.yaw = 90f;
        float[] turned = LevelCompiler.compile(doc, catalog).colliders()[7];
        Check.that(Math.abs((flat[3] - flat[0]) - (turned[5] - turned[2]))
                < 1e-4f && Math.abs((flat[5] - flat[2])
                - (turned[3] - turned[0])) < 1e-4f,
                "mesa girada troca as dimensões do collider");
        table.transform.yaw = 0f;
        // lâmpada de teto não colide
        doc.prefabs.add(new br.com.termia.construajogue.map
                .PrefabInstance("luz", "prop.lamp.ceiling"));
        Check.that(LevelCompiler.compile(doc, catalog).boxCount() == 8,
                "lâmpada de teto sem collider");
        doc.prefabs.clear();

        // céu: persiste e compila no modo certo (legado fica NONE)
        doc.sky = "night";
        Check.that(LevelCompiler.compile(MapJson.read(MapJson.write(doc)),
                catalog).skyMode() == br.com.termia.construajogue.runtime
                .RuntimeLevel.SKY_NIGHT, "céu noturno compilado");
        doc.sky = "none";
        Check.that(LevelCompiler.compile(doc, catalog).skyMode() == 0,
                "sem skybox por padrão");

        // canto em L: a pintura do lado para na face interna da outra
        // parede (sem faixa de cor interna vazando pela ponta)
        StructureObject wa = new StructureObject("wa",
                StructureObject.KIND_BLOCK);
        wa.role = StructureObject.ROLE_WALL;
        wa.transform.y = 1.5f;
        wa.half = new float[]{2f, 1.5f, 0.15f};
        wa.color = new float[]{0.5f, 0.5f, 0.5f};
        wa.color2 = new float[]{0.9f, 0.2f, 0.2f};
        StructureObject wb = new StructureObject("wb",
                StructureObject.KIND_BLOCK);
        wb.role = StructureObject.ROLE_WALL;
        wb.transform.x = 2f;
        wb.transform.y = 1.5f;
        wb.transform.z = 2f;
        wb.half = new float[]{0.15f, 1.5f, 2f};
        wb.color = new float[]{0.5f, 0.5f, 0.5f};
        java.util.List<StructureObject> both =
                java.util.Arrays.asList(wa, wb);
        float[] stubs = LevelCompiler.wallStubPlanes(wa, both);
        Check.that(Float.isNaN(stubs[0]), "ponta baixa sem canto");
        Check.that(Math.abs(stubs[1] - 1.85f) < 1e-4f,
                "pintura para na face interna (x=1,85)");
        float[] stubsB = LevelCompiler.wallStubPlanes(wb, both);
        Check.that(Float.isNaN(stubsB[1]), "wb: ponta alta sem canto");
        Check.that(Math.abs(stubsB[0] - 0.15f) < 1e-4f,
                "wb corta em z=0,15 se pintada");

        // vão sobreposto e vão fora da parede são recusados
        WallOpening bad = new WallOpening("b", WallOpening.DOOR);
        bad.offset = -1.2f;
        bad.width = 1.0f;
        bad.height = 2.0f;
        wall.openings.add(bad);
        Check.that(MapValidator.hasError(
                MapValidator.validate(doc, catalog)), "sobreposto barrado");
        wall.openings.remove(bad);
        bad.offset = 4.2f;
        wall.openings.add(bad);
        Check.that(MapValidator.hasError(
                MapValidator.validate(doc, catalog)), "fora barrado");
        Check.done("WallOpeningTest");
    }

    /** true se nenhum pedaço cobre o ponto (along, y) da parede. */
    private static boolean free(List<float[]> pieces, float x, float y) {
        for (float[] p : pieces) {
            if (x > p[0] && x < p[3] && y > p[1] && y < p[4]) {
                return false;
            }
        }
        return true;
    }
}
