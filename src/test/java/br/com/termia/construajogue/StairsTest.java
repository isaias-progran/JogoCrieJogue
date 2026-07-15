package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Sobe a escada de andar ANDANDO, com a colisão real do jogador
 * (raio 0.35, altura 1.75, degrau 0.35): o teste caminha no eixo Z e
 * precisa terminar em cima (y = 3 m). Também confere a rampa.
 */
public final class StairsTest {

    private static final float RADIUS = 0.35f;
    private static final float HEIGHT = 1.75f;
    private static final float STEP = 0.35f;

    public static void main(String[] args) throws IOException {
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }

        Check.that(climb(catalog, "stairs.floor") > 2.9f,
                "escada de andar leva a 3 m");
        Check.that(climb(catalog, "stairs.small") > 0.9f,
                "escada pequena leva a 1 m");
        Check.that(climb(catalog, "ramp.floor") > 2.9f,
                "rampa de andar leva a 3 m");
        Check.that(climb(catalog, "ramp.short") > 0.9f,
                "rampa curta leva a 1 m");
        Check.done("StairsTest");
    }

    /** Anda de -4 m até o fim da peça e devolve a altura alcançada. */
    private static float climb(PrefabCatalog catalog, String prefabId)
            throws IOException {
        MapDocument doc = new MapDocument();
        doc.id = "map-escada";
        doc.name = "escada";
        StructureObject floor = new StructureObject("piso",
                StructureObject.KIND_BLOCK);
        floor.transform.y = -0.5f;
        floor.half = new float[]{10f, 0.5f, 10f};
        floor.color = new float[]{0.4f, 0.4f, 0.4f};
        doc.structures.add(floor);
        doc.prefabs.add(new PrefabInstance("peca", prefabId));
        LogicMarker spawn = new LogicMarker("spawn",
                LogicMarker.PLAYER_SPAWN);
        spawn.z = -4f;
        doc.markers.add(spawn);
        LogicMarker exit = new LogicMarker("exit", LogicMarker.EXIT);
        exit.z = 8f;
        exit.radius = 1f;
        doc.markers.add(exit);
        Check.that(!MapValidator.hasError(
                MapValidator.validate(doc, catalog)), prefabId
                + " valida");
        RuntimeLevel level = LevelCompiler.compile(doc, catalog);
        float[][] boxes = level.colliders();

        float[] pos = {0f, 0f, -4f};
        float top = 0f;
        // caminha para +Z em passos de 2 cm, com gravidade entre passos
        // (500 passos = 10 m: cobre a rampa de andar de 6 m com folga)
        for (int i = 0; i < 500; i++) {
            Collision.moveHorizontal(pos, 2, 0.02f, RADIUS, HEIGHT, STEP,
                    boxes);
            Collision.moveVertical(pos, -0.1f, RADIUS, HEIGHT, boxes);
            top = Math.max(top, pos[1]);
        }
        return top;
    }
}
