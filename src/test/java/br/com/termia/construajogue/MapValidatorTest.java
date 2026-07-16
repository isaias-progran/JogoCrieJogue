package br.com.termia.construajogue;

import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public final class MapValidatorTest {

    private static PrefabCatalog catalog;

    public static void main(String[] args) throws IOException {
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }

        Check.that(!hasError(valid()), "mapa mínimo válido passa");

        MapDocument doc = valid();
        doc.markers.remove(doc.firstMarker(LogicMarker.PLAYER_SPAWN));
        expectError(doc, "inicio.unico", "sem início");

        doc = valid();
        LogicMarker extra = new LogicMarker("spawn2",
                LogicMarker.PLAYER_SPAWN);
        doc.markers.add(extra);
        expectError(doc, "inicio.unico", "dois inícios");

        doc = valid();
        doc.markers.remove(doc.firstMarker(LogicMarker.EXIT));
        expectError(doc, "saida.unica", "sem saída");

        doc = valid();
        StructureObject pillar = new StructureObject("pilar",
                StructureObject.KIND_BLOCK);
        LogicMarker blockedSpawn = doc.firstMarker(LogicMarker.PLAYER_SPAWN);
        pillar.transform.x = blockedSpawn.x;
        pillar.transform.y = 1f;
        pillar.transform.z = blockedSpawn.z;
        pillar.half = new float[]{0.5f, 1f, 0.5f};
        pillar.color = new float[]{0.5f, 0.5f, 0.5f};
        doc.structures.add(pillar);
        expectError(doc, "inicio.bloqueado", "início dentro do bloco");

        doc = valid();
        doc.prefabs.add(new PrefabInstance("x", "nao.existe"));
        expectError(doc, "peca.desconhecida", "prefab inexistente");

        doc = valid();
        PrefabInstance door = new PrefabInstance("porta", "door.gate");
        door.properties.put("halfX", 1f);
        door.properties.put("halfY", 1f);
        door.properties.put("halfZ", 0.2f);
        door.properties.put("controllerId", "terminal-fantasma");
        doc.prefabs.add(door);
        expectError(doc, "porta.terminal", "porta sem terminal");

        doc = valid();
        PrefabInstance kit = new PrefabInstance("kit", "pickup.health");
        kit.properties.put("patrolX", 3f);
        doc.prefabs.add(kit);
        expectError(doc, "peca.propriedade", "propriedade proibida");

        doc = valid();
        doc.structures.get(0).half[1] = 0f;
        expectError(doc, "estrutura.dimensao", "dimensão nula");

        doc = valid();
        doc.structures.get(0).transform.x = Float.NaN;
        expectError(doc, "numero.invalido", "NaN");

        doc = valid();
        doc.structures.clear();
        expectError(doc, "estrutura.nenhuma", "sem estruturas");

        doc = valid();
        doc.prefabs.get(0).id = doc.structures.get(0).id;
        expectError(doc, "id.duplicado", "id repetido");

        doc = valid();
        doc.objective.type = ObjectiveSpec.ELIMINATE_ALL;
        doc.prefabs.clear();
        doc.markers.remove(doc.firstMarker(LogicMarker.EXIT));
        expectError(doc, "objetivo.inimigos", "combate sem inimigos");

        doc = valid();
        doc.objective.type = ObjectiveSpec.COLLECT;
        doc.objective.target = 2;
        doc.markers.remove(doc.firstMarker(LogicMarker.EXIT));
        PrefabInstance token = new PrefabInstance("ficha", "pickup.token");
        doc.prefabs.add(token);
        expectError(doc, "objetivo.fichas", "fichas insuficientes");

        doc = valid();
        doc.objective.type = ObjectiveSpec.SURVIVE;
        doc.objective.durationSeconds = 10f;
        doc.objective.timeLimitSeconds = 5f;
        doc.markers.remove(doc.firstMarker(LogicMarker.EXIT));
        expectError(doc, "objetivo.tempo", "limite menor que sobrevivência");

        doc = valid();
        PrefabInstance terminal = new PrefabInstance("terminal",
                "terminal.wall");
        terminal.properties.put("order", 2f);
        doc.prefabs.add(terminal);
        expectError(doc, "terminal.ordem", "sequência começa em dois");

        doc = valid();
        doc.prefabs.get(0).properties.put("patrolX", "não é número");
        expectError(doc, "peca.propriedade", "propriedade com tipo errado");

        doc = valid();
        PrefabInstance npc = new PrefabInstance("pessoa", "npc.human");
        npc.properties.put("name", "Lia");
        npc.properties.put("role", "guia");
        npc.properties.put("greeting", "Olá!");
        npc.properties.put("background", "Conhece a cidade.");
        doc.prefabs.add(npc);
        Check.that(!hasError(doc), "textos válidos do NPC são aceitos");

        doc = valid();
        npc = new PrefabInstance("pessoa", "npc.human");
        npc.properties.put("greeting", 12f);
        doc.prefabs.add(npc);
        expectError(doc, "peca.propriedade", "fala do NPC precisa ser texto");

        doc = valid();
        doc.markers.add(new LogicMarker("estranho", "teleporte"));
        expectError(doc, "marcador.tipo", "marcador desconhecido");

        Check.done("MapValidatorTest");
    }

    /** Piso + um drone + início e saída: o menor mapa jogável. */
    private static MapDocument valid() {
        MapDocument doc = new MapDocument();
        doc.id = "map-valido";
        doc.name = "válido";
        StructureObject floor = new StructureObject("piso",
                StructureObject.KIND_BLOCK);
        floor.transform.y = -0.5f;
        floor.half = new float[]{8f, 0.5f, 8f};
        floor.color = new float[]{0.5f, 0.5f, 0.5f};
        doc.structures.add(floor);

        PrefabInstance drone = new PrefabInstance("drone", "enemy.drone");
        drone.transform.x = 2f;
        drone.transform.y = 1.7f;
        doc.prefabs.add(drone);

        LogicMarker spawn = new LogicMarker("spawn",
                LogicMarker.PLAYER_SPAWN);
        spawn.z = 6f;
        doc.markers.add(spawn);
        LogicMarker exit = new LogicMarker("exit", LogicMarker.EXIT);
        exit.z = -6f;
        exit.radius = 1.2f;
        doc.markers.add(exit);
        return doc;
    }

    private static boolean hasError(MapDocument doc) {
        return MapValidator.hasError(MapValidator.validate(doc, catalog));
    }

    private static void expectError(MapDocument doc, String code,
                                    String what) {
        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        for (ValidationIssue issue : issues) {
            if (issue.isError() && issue.code.equals(code)) {
                Check.that(true, what);
                return;
            }
        }
        throw new AssertionError("FALHOU: " + what + " deveria gerar "
                + code + "; veio " + issues);
    }
}
