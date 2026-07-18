package br.com.termia.construajogue.ai;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fluxo de REVISÃO/melhoria de mapa com IA e o funil único de validação do
 * modo Livre, extraídos mecanicamente do AiFeatureController.
 */
final class AiImproveFlow {

    private final AiFeatureController host;

    AiImproveFlow(AiFeatureController host) {
        this.host = host;
    }

    void promptImproveMap(MapDocument current, int modelIndex,
                          Runnable onBack) {
        if (current == null) {
            host.toast("Mapa atual ausente");
            host.returnTo(onBack);
            return;
        }
        if (!host.keys.hasKey()) {
            host.toast("Configure sua chave primeiro");
            host.showSettings();
            return;
        }

        final String currentJson;
        final MapDocument snapshot;
        try {
            // Congela o mapa desta revisão; edições posteriores não entram
            // escondidas no pedido que será enviado.
            currentJson = MapJson.write(current);
            snapshot = MapJson.read(currentJson);
            AiOpenAiClient.scenarioModelAt(modelIndex);
        } catch (RuntimeException failure) {
            host.toast("Não consegui preparar o mapa: "
                    + AiFeatureController.message(failure));
            host.returnTo(onBack);
            return;
        }

        LinearLayout form = host.column();
        form.addView(host.text("Diga somente o que deve mudar. A IA deve "
                + "manter todo o restante e devolverá um mapa completo para "
                + "você conferir antes de salvar."));
        EditText instruction = new EditText(host.activity);
        instruction.setHint("Ex.: crie interiores nos prédios, adicione "
                + "mais cobertura e deixe o aliado protegendo a saída");
        instruction.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        instruction.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        instruction.setMinLines(4);
        instruction.setMaxLines(7);
        instruction.setGravity(Gravity.TOP | Gravity.START);
        instruction.setHorizontallyScrolling(false);
        instruction.setVerticalScrollBarEnabled(true);
        instruction.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(1000)});
        form.addView(instruction);
        TextView modelLabel = host.text("Modelo para esta melhoria");
        modelLabel.setPadding(0, 10, 0, 2);
        form.addView(modelLabel);
        Spinner model = new Spinner(host.activity);
        ArrayAdapter<String> models = new ArrayAdapter<>(host.activity,
                android.R.layout.simple_spinner_item,
                AiOpenAiClient.SCENARIO_MODEL_LABELS);
        models.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        model.setAdapter(models);
        model.setSelection(modelIndex);
        form.addView(model);
        TextView warning = host.text("Esta ação faz uma nova chamada e pode "
                + "consumir créditos. O JSON inteiro do mapa — inclusive "
                + "nomes e textos de NPC — será enviado à API como dado. "
                + "O original não será sobrescrito; a aprovação salva uma "
                + "nova cópia.");
        warning.setTextColor(0xFFFFC46B);
        form.addView(warning);

        AlertDialog dialog = new AlertDialog.Builder(host.activity)
                .setTitle("✦ Melhorar com IA")
                .setView(host.scrollable(form))
                .setPositiveButton("GERAR MELHORIA", null)
                .setNegativeButton(onBack == null ? "Cancelar" : "VOLTAR",
                        (ignored, which) -> host.returnTo(onBack))
                .create();
        dialog.setOnCancelListener(ignored -> host.returnTo(onBack));
        dialog.setOnShowListener(ignored -> dialog.getButton(
                DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String requested = instruction.getText().toString().trim();
            if (requested.length() < 3) {
                host.toast("Descreva o que deseja melhorar");
                return;
            }
            int selectedModel = model.getSelectedItemPosition();
            dialog.dismiss();
            generateImprovedMap(currentJson, snapshot.name, requested,
                    selectedModel, onBack);
        }));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void generateImprovedMap(String currentJson, String sourceName,
                                     String instruction, int modelIndex,
                                     Runnable onBack) {
        final String key;
        final PrefabCatalog catalog;
        final List<String> prefabIds = new ArrayList<>();
        final String scenarioModel = AiOpenAiClient.scenarioModelAt(modelIndex);
        final String scenarioModelLabel =
                AiOpenAiClient.scenarioModelLabelAt(modelIndex);
        try {
            catalog = host.listener.aiCatalog();
            for (br.com.termia.construajogue.prefab.PrefabDefinition
                    definition : catalog.all()) {
                prefabIds.add(definition.id);
            }
            // Limites e corpo são conferidos ANTES do gate: recusa local
            // (mapa enorme, pedido curto) não pode queimar vaga da sessão.
            AiOpenAiClient.buildImproveMapRequest(instruction, currentJson,
                    scenarioModel, prefabIds);
            host.gate.acquire();
            key = host.keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
        } catch (Exception failure) {
            host.toast(AiFeatureController.message(failure));
            host.returnTo(onBack);
            return;
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        AiOpenAiClient.Cancellation cancellation =
                new AiOpenAiClient.Cancellation();
        host.activeCancellation = cancellation;
        AlertDialog progress = host.busy("IA melhorando o mapa", () -> {
            cancelled.set(true);
            cancellation.cancel();
            Future<?> request = host.activeRequest;
            if (request != null) request.cancel(true);
            host.returnTo(onBack);
        });
        AtomicReference<String> phase = new AtomicReference<>(
                "Lendo o mapa e planejando somente as mudanças pedidas…");
        final long started = android.os.SystemClock.elapsedRealtime();
        android.os.Handler ticker = new android.os.Handler(
                host.activity.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!progress.isShowing()) return;
                long seconds = (android.os.SystemClock.elapsedRealtime()
                        - started) / 1000;
                progress.setMessage(phase.get() + "\n\nTrabalhando há "
                        + seconds / 60 + "m" + String.format("%02d",
                        seconds % 60) + "s. O mapa original permanece "
                        + "intacto.");
                ticker.postDelayed(this, 1000);
            }
        };
        ticker.post(tick);
        host.activeRequest = host.executor.submit(() -> {
            try {
                String script = host.client.improveFreeMapScript(key,
                        instruction, currentJson, scenarioModel, prefabIds,
                        (chars, lines) -> phase.set("Recebendo a revisão: "
                                + lines + " comandos (" + chars
                                + " caracteres)…"), cancellation);
                AiFreeMapScript.Result parsed = validateFreeScript(script,
                        catalog);
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.previews.previewFreeMap(parsed, scenarioModelLabel,
                            modelIndex, true, sourceName, onBack);
                });
            } catch (Exception failure) {
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.showError("Não consegui melhorar o mapa", failure,
                            onBack);
                });
            } finally {
                if (host.activeCancellation == cancellation) {
                    host.activeCancellation = null;
                }
            }
        });
    }

    /** Único funil de confiança para geração e revisão do modo Livre. */
    AiFreeMapScript.Result validateFreeScript(String script,
                                              PrefabCatalog catalog)
            throws IOException {
        AiFreeMapScript.Result parsed = AiFreeMapScript.parse(script,
                catalog);
        List<ValidationIssue> issues = MapValidator.validate(parsed.document,
                catalog);
        if (MapValidator.hasError(issues)) {
            // Alternativa antes de desistir: conserta o que o validador
            // recusou e tenta validar de novo.
            int fixes = AiFreeMapScript.salvage(parsed.document, catalog,
                    parsed.warnings);
            if (fixes > 0) {
                issues = MapValidator.validate(parsed.document, catalog);
            }
        }
        if (MapValidator.hasError(issues)) {
            throw new IOException("o desenho da IA foi recusado pelo "
                    + "validador mesmo após o resgate: "
                    + AiFeatureController.firstError(issues)
                    + "\n\nTente de novo ou simplifique o pedido.");
        }
        for (ValidationIssue issue : issues) {
            if (!issue.isError()) {
                parsed.warnings.add("validador: " + issue.message);
            }
        }
        // A compilação já foi provada dentro do validador (checkCompiledSpace
        // compila e converte falha em erro), então não se compila de novo.
        return parsed;
    }
}
