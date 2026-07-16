package br.com.termia.construajogue;

import br.com.termia.construajogue.ai.AiFreeMapScript;
import br.com.termia.construajogue.ai.AiOpenAiClient;
import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/** Contratos do modo LIVRE: roteiro de comandos vira mapa validado. */
public final class AiFreeMapTest {

    public static void main(String[] args) throws Exception {
        PrefabCatalog catalog;
        try (FileInputStream input = new FileInputStream(
                "src/main/assets/prefabs/catalog.json")) {
            catalog = PrefabCatalog.load(input);
        }

        request(catalog);
        streamEvents();
        fullScript(catalog);
        safetyNets(catalog);

        Check.done("AiFreeMapTest");
    }

    private static void request(PrefabCatalog catalog) {
        List<String> ids = new ArrayList<>();
        for (PrefabDefinition definition : catalog.all()) {
            ids.add(definition.id);
        }
        String request = AiOpenAiClient.buildFreeMapRequest(
                "cidade cheia à noite", "gpt-5.6-terra", ids);
        Check.that(request.contains("\"gpt-5.6-terra\""),
                "modo livre usa o modelo escolhido");
        Check.that(request.contains("16000"),
                "modo livre reserva saída maior");
        Check.that(request.contains("pickup.weapon.rifle"),
                "instrução lista o catálogo inteiro");
        Check.that(request.contains("PEDIDO DO JOGADOR"),
                "pedido do jogador passa direto no input");
        Check.that(!request.contains("json_schema"),
                "modo livre não prende a saída a schema");
        Check.that(request.contains("\"stream\":true")
                        || request.contains("\"stream\": true"),
                "modo livre pede streaming para mostrar progresso");
        Check.that(request.contains("CADA cômodo fechado"),
                "instrução cobra porta em todo cômodo");
        Check.that(request.contains("12 a 24 inimigos"),
                "instrução cobra combate povoado");
        Check.that(request.contains("NÃO repita o mesmo material"),
                "instrução cobra variedade de textura nas paredes");
        boolean rejected = false;
        try {
            AiOpenAiClient.buildFreeMapRequest("ab", "gpt-5.6-terra", ids);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        Check.that(rejected, "pedido curto demais é recusado antes da rede");
    }

    private static void streamEvents() throws Exception {
        Check.equal(AiOpenAiClient.sseDelta("{\"type\":"
                        + "\"response.output_text.delta\",\"delta\":"
                        + "\"parede 0 0 4 0\\n\"}"),
                "parede 0 0 4 0\n", "evento de texto devolve o trecho");
        Check.that(AiOpenAiClient.sseDelta("{\"type\":"
                        + "\"response.in_progress\"}") == null,
                "evento de controle é ignorado");
        Check.that(AiOpenAiClient.sseDelta("linha estranha") == null,
                "dado não-JSON é ignorado sem quebrar");
        boolean refused = false;
        try {
            AiOpenAiClient.sseDelta("{\"type\":"
                    + "\"response.refusal.done\",\"refusal\":\"nao\"}");
        } catch (java.io.IOException expected) {
            refused = true;
        }
        Check.that(refused, "recusa no streaming vira erro claro");
        boolean failed = false;
        try {
            AiOpenAiClient.sseDelta("{\"type\":\"response.failed\","
                    + "\"response\":{\"error\":{\"message\":\"cota\"}}}");
        } catch (java.io.IOException expected) {
            failed = expected.getMessage().contains("cota");
        }
        Check.that(failed, "falha da API propaga a mensagem segura");
    }

    private static void fullScript(PrefabCatalog catalog) {
        String script = String.join("\n",
                "```",
                "nome Vila do Teste",
                "ceu night",
                "som outdoor",
                "ambiente 0,3",
                "neblina 0.04 0.05 0.08 60",
                "objetivo collect 3 300",
                "# quarteirão",
                "piso 0 0 20 20 asphalt 0.2 0.22 0.25",
                "parede -20 -20 20 -20 3 brick 0.5 0.4 0.3",
                "parede -20 20 20 20 3 brick 0.5 0.4 0.3",
                "parede -20 -20 -20 20 3 brick 0.5 0.4 0.3",
                "parede 20 -20 20 20 3 brick 0.5 0.4 0.3",
                "parede -6 4 6 4 3 wood 0.6 0.5 0.4",
                "vao porta 0",
                "vao janela 3.5",
                "parede -10 -10 -4 -14 3 metal 0.4 0.4 0.45",
                "teto 0 8 4 3 3 plain 0.3 0.3 0.3",
                "bloco 10 1 10 1 1 1 metal 0.4 0.42 0.45",
                "peca npc.human 2 0 6",
                "texto name Rui",
                "texto role vigia",
                "texto greeting E aí, chegou agora?",
                "texto background Vigia noturno prático e direto.",
                "peca enemy.drone -8 1.8 -8",
                "patrulha 8 -8",
                "peca terminal.wall 5 1.4 12",
                "peca door.gate 0 1.4 15",
                "prop halfX 2",
                "peca pickup.token -12 0.5 -12",
                "peca pickup.token 12 0.5 -12 ",
                "peca pickup.token 0 0.5 14",
                "peca peca.inexistente 0 0 0",
                "dancar 1 2 3",
                "inicio 0 -16 0 0",
                "saida 0 18");
        AiFreeMapScript.Result parsed = AiFreeMapScript.parse(script,
                catalog);
        MapDocument doc = parsed.document;
        Check.equal(doc.name, "Vila do Teste", "nome vem do roteiro");
        Check.equal(doc.sky, "night", "céu vem do roteiro");
        Check.equal(doc.objective.type, "collect",
                "objetivo collect aceito");
        Check.equal(doc.objective.target, 3, "alvo de fichas do roteiro");
        Check.that(doc.objective.twoStarSeconds > 0f,
                "metas de estrela derivadas do tempo-limite");
        Check.that(Math.abs(doc.ambient - 0.3f) < 1e-4,
                "vírgula decimal aceita");
        Check.equal(doc.structures.size(), 9,
                "estruturas do roteiro criadas");
        StructureObject door = doc.structures.get(5);
        Check.equal(door.openings.size(), 2, "vãos aplicados à última parede");
        Check.equal(door.openings.get(0).type, "door", "porta recortada");
        Check.equal(door.openings.get(1).type, "window", "janela recortada");
        StructureObject diagonal = doc.structures.get(6);
        Check.equal(diagonal.kind, StructureObject.KIND_POLY,
                "parede diagonal vira poly");
        Check.equal(doc.prefabs.size(), 7, "peças conhecidas entram");
        PrefabInstance npc = doc.prefabs.get(0);
        Check.equal(npc.properties.get("name"), "Rui", "texto do NPC gravado");
        Check.equal(npc.properties.get("greeting"), "E aí, chegou agora?",
                "fala do NPC com espaços preservada");
        PrefabInstance drone = doc.prefabs.get(1);
        Check.that(Math.abs(drone.floatProperty("patrolX", 0f) - 8f) < 1e-4,
                "patrulha aplicada ao último inimigo");
        PrefabInstance gate = doc.prefabs.get(3);
        Check.equal(gate.properties.get("controllerId"),
                doc.prefabs.get(2).id, "portão liga sozinho ao terminal");
        Check.that(Math.abs(gate.floatProperty("halfX", 0f) - 2f) < 1e-4,
                "prop numérica aplicada");
        Check.that(doc.firstMarker(LogicMarker.PLAYER_SPAWN) != null
                        && doc.firstMarker(LogicMarker.EXIT) != null,
                "início e saída marcados");
        Check.equal(parsed.warnings.size(), 2,
                "peça inexistente e comando desconhecido viram avisos: "
                        + parsed.warnings);

        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        Check.that(!MapValidator.hasError(issues),
                "mapa livre passa no validador: " + issues);
        RuntimeLevel runtime = LevelCompiler.compile(doc, catalog);
        Check.equal(runtime.npcs().length, 1, "mapa livre compila com NPC");
        Check.equal(runtime.npcs()[0].name, "Rui",
                "NPC do modo livre conversa com a identidade do roteiro");
    }

    private static void safetyNets(PrefabCatalog catalog) {
        AiFreeMapScript.Result bare = AiFreeMapScript.parse(String.join("\n",
                "piso 0 0 10 10 plain 0.4 0.4 0.4",
                "piso 30 30 6 6 plain 0.4 0.4 0.4"), catalog);
        Check.that(bare.document.firstMarker(LogicMarker.PLAYER_SPAWN) != null,
                "sem inicio, o spawn nasce em 0 0");
        LogicMarker exit = bare.document.firstMarker(LogicMarker.EXIT);
        Check.that(exit != null && exit.x == 30f && exit.z == 30f,
                "sem saida, ela nasce na estrutura mais distante");
        Check.that(bare.warnings.size() >= 2,
                "redes de segurança geram avisos");

        // Erro real do aparelho (v0.25.1): início dentro de estrutura.
        AiFreeMapScript.Result stuck = AiFreeMapScript.parse(String.join(
                "\n",
                "piso 0 0 20 20 plain 0.4 0.4 0.4",
                "bloco 5 1.5 5 2 1.5 2 metal 0.4 0.4 0.4",
                "inicio 5 5",
                "saida 5 5"), catalog);
        LogicMarker moved = stuck.document.firstMarker(
                LogicMarker.PLAYER_SPAWN);
        List<ValidationIssue> stuckIssues = MapValidator.validate(
                stuck.document, catalog);
        Check.that(!MapValidator.hasError(stuckIssues),
                "início dentro de bloco é empurrado e o mapa passa: "
                        + stuckIssues);
        Check.that(Math.hypot(moved.x - 5f, moved.z - 5f) > 1.9f,
                "início movido para fora do bloco");

        AiFreeMapScript.Result clampedOpening = AiFreeMapScript.parse(
                String.join("\n",
                        "piso 0 0 20 20 plain 0.4 0.4 0.4",
                        "parede -3 0 3 0 3 brick 0.5 0.4 0.3",
                        "vao porta 10",
                        "vao porta 10",
                        "inicio 0 5",
                        "saida 0 -5"), catalog);
        StructureObject small = clampedOpening.document.structures.get(1);
        Check.equal(small.openings.size(), 1,
                "vão sobreposto é recusado com aviso");
        Check.that(Math.abs(small.openings.get(0).offset)
                        + small.openings.get(0).width / 2f <= 3f,
                "vão preso ao comprimento da parede");
        Check.that(!MapValidator.hasError(MapValidator.validate(
                        clampedOpening.document, catalog)),
                "parede com vão preso passa no validador");

        // Erro real do aparelho (v0.25.3): patrulha em torreta fixa.
        AiFreeMapScript.Result turret = AiFreeMapScript.parse(String.join(
                "\n",
                "piso 0 0 15 15 plain 0.4 0.4 0.4",
                "peca enemy.turret 4 0.55 4",
                "patrulha -4 -4",
                "inicio 0 -10",
                "saida 0 10"), catalog);
        Check.that(turret.document.prefabs.get(0)
                        .properties.get("patrolX") == null,
                "patrulha em torreta é recusada na hora, com aviso");
        Check.that(!MapValidator.hasError(MapValidator.validate(
                        turret.document, catalog)),
                "mapa com torreta passa no validador");

        // Resgate: mapa quebrado de propósito volta a ser válido.
        AiFreeMapScript.Result broken = AiFreeMapScript.parse(String.join(
                "\n",
                "piso 0 0 15 15 plain 0.4 0.4 0.4",
                "objetivo collect 9 300",
                "peca pickup.token 3 0.5 3",
                "peca pickup.token -3 0.5 -3",
                "inicio 0 -10",
                "saida 0 10"), catalog);
        broken.document.prefabs.get(0).properties.put("patrolX", 5f);
        Check.that(MapValidator.hasError(MapValidator.validate(
                        broken.document, catalog)),
                "mapa sabotado é mesmo recusado antes do resgate");
        int fixes = AiFreeMapScript.salvage(broken.document, catalog,
                broken.warnings);
        Check.that(fixes >= 2,
                "resgate conserta propriedade proibida e alvo de fichas");
        Check.equal(broken.document.objective.target, 2,
                "alvo de fichas ajustado ao que existe no mapa");
        Check.that(!MapValidator.hasError(MapValidator.validate(
                        broken.document, catalog)),
                "mapa resgatado passa no validador");
        Check.that(broken.warnings.toString().contains("resgate"),
                "consertos do resgate aparecem como avisos");

        boolean emptyRejected = false;
        try {
            AiFreeMapScript.parse("   \n  ", catalog);
        } catch (IllegalArgumentException expected) {
            emptyRejected = true;
        }
        Check.that(emptyRejected, "roteiro vazio é recusado com mensagem");

        AiFreeMapScript.Result clamped = AiFreeMapScript.parse(String.join(
                "\n",
                "piso 0 0 400 400 plain 2 -1 0.5",
                "inicio 0 0",
                "saida 500 500"), catalog);
        StructureObject ground = clamped.document.structures.get(0);
        Check.that(ground.half[0] <= 48f, "medidas presas à grade");
        Check.that(ground.color[0] <= 1f && ground.color[1] >= 0f,
                "cores presas a 0..1");
        LogicMarker far = clamped.document.firstMarker(LogicMarker.EXIT);
        Check.that(far.x <= 48f, "coordenadas presas à grade");
    }
}
