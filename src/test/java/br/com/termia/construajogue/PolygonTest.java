package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.geometry.Triangulator;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.io.IOException;

/** Lajes de contorno livre: triangulação, colisão andável e JSON. */
public final class PolygonTest {

    public static void main(String[] args) throws IOException {
        // L de 6×6 com recorte 3×3: área 27, 4 triângulos do earclip
        float[] l = {0, 0, 6, 0, 6, 3, 3, 3, 3, 6, 0, 6};
        Check.that(Math.abs(Triangulator.area2(l) / 2f - 27f) < 1e-3f,
                "área do L");
        Check.that(!Triangulator.selfIntersects(l), "L não se cruza");
        int[] tris = Triangulator.earClip(Triangulator.ccw(l));
        Check.equal(tris.length, 12, "4 triângulos no L");
        float[] crossed = {0, 0, 4, 4, 4, 0, 0, 4};
        Check.that(Triangulator.selfIntersects(crossed),
                "laço cruzado detectado");

        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }
        MapDocument doc = new MapDocument();
        doc.id = "map-poly";
        doc.name = "contorno";
        StructureObject poly = new StructureObject("laje",
                StructureObject.KIND_POLY);
        poly.role = StructureObject.ROLE_FLOOR;
        poly.polygon = l.clone();
        poly.half = new float[]{0f, 0.15f, 0f};
        poly.transform.y = -0.15f;
        poly.color = new float[]{0.3f, 0.3f, 0.3f};
        poly.syncPolyBounds();
        Check.that(poly.transform.x == 3f && poly.half[0] == 3f,
                "envolvente sincronizado");
        doc.structures.add(poly);
        LogicMarker spawn = new LogicMarker("spawn",
                LogicMarker.PLAYER_SPAWN);
        spawn.x = 1f;
        spawn.z = 1f;
        doc.markers.add(spawn);
        LogicMarker exit = new LogicMarker("exit", LogicMarker.EXIT);
        exit.x = 5f;
        exit.z = 1f;
        exit.radius = 1f;
        doc.markers.add(exit);

        Check.that(!MapValidator.hasError(
                MapValidator.validate(doc, catalog)), "contorno L válido");
        MapDocument back = MapJson.read(MapJson.write(doc));
        Check.sameFloats(back.structures.get(0).polygon, l,
                "polígono persistido");
        RuntimeLevel level = LevelCompiler.compile(back, catalog);
        Check.that(level.vertexData().length > 0, "malha gerada");
        // dentro do L há chão; no recorte não há (pontos fora das
        // fronteiras exatas das faixas de 0,5 m)
        Check.that(covered(level, 1.2f, 1.3f), "chão em 1,2×1,3");
        Check.that(covered(level, 5.2f, 1.3f), "chão em 5,2×1,3");
        Check.that(covered(level, 1.2f, 5.3f), "chão em 1,2×5,3");
        Check.that(!covered(level, 5.2f, 5.3f), "recorte sem chão");

        // contorno cruzado é bloqueado pelo validador
        back.structures.get(0).polygon = crossed;
        back.structures.get(0).syncPolyBounds();
        Check.that(MapValidator.hasError(
                MapValidator.validate(back, catalog)), "cruzado barrado");
        Check.done("PolygonTest");
    }

    private static boolean covered(RuntimeLevel level, float x, float z) {
        for (float[] b : level.colliders()) {
            if (x > b[0] && x < b[3] && z > b[2] && z < b[5]) {
                return true;
            }
        }
        return false;
    }
}
