package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Portão da Fase 1: a arena JSON, validada e compilada, precisa
 * reproduzir BIT A BIT o RuntimeLevel carregado da arena legada em texto.
 */
public final class LevelCompilerTest {

    public static void main(String[] args) throws IOException {
        RuntimeLevel legacy;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/levels/arena.txt")) {
            legacy = LegacyLevelLoader.load(input, "arena.txt");
        }

        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }
        String json = new String(Files.readAllBytes(
                Paths.get("src/main/assets/maps/arena.json")),
                StandardCharsets.UTF_8);
        MapDocument doc = MapJson.read(json);
        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        for (ValidationIssue issue : issues) {
            System.out.println("  " + issue);
        }
        Check.that(!MapValidator.hasError(issues),
                "arena.json passa na validação");
        RuntimeLevel compiled = LevelCompiler.compile(doc, catalog);

        Check.sameRows(compiled.colliders(), legacy.colliders(),
                "colliders");
        Check.sameFloats(compiled.vertexData(), legacy.vertexData(),
                "malha do cenário");
        Check.sameFloats(compiled.doorVertexData(),
                legacy.doorVertexData(), "malha da porta");
        Check.equal(compiled.doorIndex(), legacy.doorIndex(),
                "índice da porta");
        Check.sameFloats(compiled.doorOriginal(), legacy.doorOriginal(),
                "bounds da porta");
        Check.sameFloats(compiled.terminal(), legacy.terminal(),
                "terminal");
        Check.sameFloats(compiled.exit(), legacy.exit(), "saída");
        Check.that(compiled.exitElevationKnown()
                        && compiled.exitElevation() == 0f,
                "saída JSON térrea guarda Y sem quebrar array legado");
        Check.sameRows(compiled.items(), legacy.items(), "itens");
        Check.sameFloats(compiled.spawn(), legacy.spawn(), "início");
        Check.sameRows(compiled.droneSpawns(), legacy.droneSpawns(),
                "drones");
        Check.sameRows(compiled.waveSpawns(), legacy.waveSpawns(),
                "drones dormentes");
        Check.sameRows(compiled.mutantSpawns(), legacy.mutantSpawns(),
                "mutantes");
        Check.that(compiled.ambient() == legacy.ambient(), "ambiente");
        Check.sameFloats(compiled.fogColor(), legacy.fogColor(),
                "cor da neblina");
        Check.that(compiled.fogFar() == legacy.fogFar(),
                "distância da neblina");

        MapDocument upper = new MapDocument();
        LogicMarker upperExit = new LogicMarker("saida-alta",
                LogicMarker.EXIT);
        upperExit.x = 2f;
        upperExit.y = 3.3f;
        upperExit.z = 4f;
        upperExit.radius = 1.2f;
        upper.markers.add(upperExit);
        StructureObject water = new StructureObject("agua-alta",
                StructureObject.KIND_BLOCK);
        water.role = StructureObject.ROLE_FLOOR;
        water.material = "water";
        water.transform.y = 3.15f;
        water.half = new float[]{2f, 0.15f, 2f};
        water.color = new float[]{0.2f, 0.4f, 0.7f};
        upper.structures.add(water);
        RuntimeLevel upperLevel = LevelCompiler.compile(upper, catalog);
        Check.sameFloats(upperLevel.exit(),
                new float[]{2f, 3.3f, 4f, 1.2f},
                "saída de andar preserva Y");
        Check.sameFloats(upperLevel.hazards()[0], new float[]{
                RuntimeLevel.HAZARD_WATER, -2f,
                water.transform.y - water.half[1], -2f, 2f,
                water.transform.y + water.half[1], 2f},
                "perigo de andar preserva volume Y");
        Check.done("LevelCompilerTest (arena JSON == arena texto)");
    }
}
