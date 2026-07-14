package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
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
        Check.done("LevelCompilerTest (arena JSON == arena texto)");
    }
}
