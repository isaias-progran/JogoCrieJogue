package br.com.termia.construajogue.ai;

import android.app.AlertDialog;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Prévias de geração/revisão (diálogos de confirmação antes de salvar),
 * extraídas mecanicamente do AiFeatureController.
 */
final class AiPreviewDialogs {

    private final AiFeatureController host;

    AiPreviewDialogs(AiFeatureController host) {
        this.host = host;
    }

    void previewFreeMap(AiFreeMapScript.Result parsed,
                        String modelLabel, int modelIndex,
                        boolean improved, String sourceName,
                        Runnable onDiscard) {
        MapDocument document = parsed.document;
        int combatants = 0;
        for (PrefabInstance prefab : document.prefabs) {
            if ("npc.human".equals(prefab.prefabId)
                    && prefab.booleanProperty("combatant", false)) {
                combatants++;
            }
        }
        StringBuilder details = new StringBuilder();
        details.append(improved ? "Revisão proposta pela IA.\n"
                        : "Mapa desenhado livremente pela IA.\n")
                .append("Modelo: ").append(modelLabel).append('\n')
                .append(document.structures.size()).append(" estruturas · ")
                .append(document.prefabs.size()).append(" peças\n")
                .append("Objetivo: ").append(document.objective.type);
        if (combatants > 0) {
            details.append("\n").append(combatants)
                    .append(combatants == 1
                            ? " aliado combatente" : " aliados combatentes");
        }
        if (!parsed.warnings.isEmpty()) {
            int fixes = 0;
            for (String warning : parsed.warnings) {
                if (warning.startsWith("resgate:")) fixes++;
            }
            if (fixes > 0) appendWarnings(details, "Corrigidos pelo jogo",
                    parsed.warnings, true, 8);
            if (fixes < parsed.warnings.size()) {
                appendWarnings(details, "Pontos de atenção",
                        parsed.warnings, false, 8);
            }
        }
        details.append(improved
                ? "\n\nO mapa original continua intacto. Nada desta "
                + "revisão foi salvo ainda."
                : "\n\nNada foi salvo ainda.");
        AlertDialog.Builder builder = new AlertDialog.Builder(host.activity)
                .setTitle(document.name)
                .setMessage(details.toString())
                .setPositiveButton(improved
                                ? "SALVAR CÓPIA E EDITAR"
                                : "SALVAR E EDITAR",
                        (dialog, which) -> {
                    try {
                        MapDocument saved = document;
                        if (improved) {
                            saved = AiMapRevision.copyForSave(document,
                                    sourceName);
                        }
                        host.store.save(saved);
                        List<MapDocument> documents = new ArrayList<>();
                        documents.add(saved);
                        host.listener.onAiScenarioSaved(documents);
                    } catch (IOException failure) {
                        host.showError("Não consegui salvar", failure);
                    }
                })
                .setNeutralButton(improved ? "MELHORAR DE NOVO"
                                : "MELHORAR COM IA",
                        (dialog, which) -> host.improve.promptImproveMap(
                                document, modelIndex,
                                () -> previewFreeMap(parsed,
                                        modelLabel, modelIndex, improved,
                                        sourceName, onDiscard)))
                .setNegativeButton(onDiscard != null ? "VOLTAR"
                                : improved ? "DESCARTAR REVISÃO" : "Descartar",
                        (dialog, which) -> host.returnTo(onDiscard));
        builder.show();
    }

    private static void appendWarnings(StringBuilder details, String title,
                                       List<String> warnings,
                                       boolean repairs, int limit) {
        details.append("\n\n").append(title).append(":");
        int shown = 0;
        for (String warning : warnings) {
            if (warning.startsWith("resgate:") != repairs) continue;
            if (shown++ >= limit) {
                details.append("\n…");
                break;
            }
            String clean = warning.startsWith("resgate:")
                    ? warning.substring("resgate:".length()).trim()
                    : warning.startsWith("validador:")
                    ? warning.substring("validador:".length()).trim()
                    : warning;
            details.append("\n• ").append(clean);
        }
    }

    void previewScenario(AiScenarioPlan plan,
                         List<MapDocument> documents,
                         AiScenarioProfile profile,
                         String modelLabel, int modelIndex) {
        int structures = 0;
        int prefabs = 0;
        for (MapDocument document : documents) {
            structures += document.structures.size();
            prefabs += document.prefabs.size();
        }
        MapDocument first = documents.get(0);
        String details = plan.description() + "\n\nModelo: " + modelLabel
                + "\n"
                + profile.description() + "\n"
                + documents.size() + (documents.size() == 1
                ? " setor" : " setores") + " · " + structures
                + " estruturas · " + prefabs + " peças\n"
                + "Objetivo final: "
                + documents.get(documents.size() - 1).objective.type
                + (documents.size() > 1
                ? "\nSó um setor será carregado por vez; isto substituirá "
                + "a lista Minha campanha." : "")
                + "\n\nNada foi salvo ainda.";
        AlertDialog.Builder builder = new AlertDialog.Builder(host.activity)
                .setTitle(first.name)
                .setMessage(details)
                .setPositiveButton(documents.size() == 1
                                ? "SALVAR E EDITAR"
                                : "SALVAR E JOGAR SETORES",
                        (dialog, which) -> {
                    List<String> saved = new ArrayList<>();
                    try {
                        for (MapDocument document : documents) {
                            host.store.save(document);
                            saved.add(document.id);
                        }
                        host.listener.onAiScenarioSaved(documents);
                    } catch (IOException failure) {
                        for (String id : saved) host.store.delete(id);
                        host.showError("Não consegui salvar", failure);
                        }
                })
                .setNegativeButton("Descartar", null);
        if (documents.size() == 1) {
            builder.setNeutralButton("MELHORAR COM IA",
                    (dialog, which) -> host.improve.promptImproveMap(first,
                            modelIndex,
                            () -> previewScenario(plan, documents, profile,
                                    modelLabel, modelIndex)));
        }
        builder.show();
    }
}
