package br.com.termia.construajogue.ai;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fluxo de GERAÇÃO de cenário (diálogo inicial, modo Guiado e modo Livre),
 * extraído mecanicamente do AiFeatureController; lê o host pelo pacote.
 */
final class AiScenarioFlow {

    private final AiFeatureController host;

    AiScenarioFlow(AiFeatureController host) {
        this.host = host;
    }

    void promptScenario() {
        if (!host.keys.hasKey()) {
            host.toast("Configure sua chave primeiro");
            host.showSettings();
            return;
        }
        LinearLayout form = host.column();
        TextView modeLabel = host.text("Modo de criação");
        modeLabel.setPadding(0, 0, 0, 2);
        form.addView(modeLabel);
        Spinner mode = new Spinner(host.activity);
        ArrayAdapter<String> modes = new ArrayAdapter<>(host.activity,
                android.R.layout.simple_spinner_item, new String[]{
                "Guiado — plano seguro (recomendado)",
                "Livre — a IA desenha tudo (criativo)"});
        modes.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(modes);
        mode.setSelection(0);
        form.addView(mode);
        form.addView(host.text("Guiado: a IA escolhe planta, rota, cômodos, "
                + "objetivo e NPC; o jogo monta com receitas prontas. "
                + "Livre: a IA desenha cada parede, peça e inimigo com "
                + "coordenadas próprias — mais criativo, gasta mais tokens "
                + "e pode precisar de reparos; o validador recusa mapa "
                + "quebrado antes de salvar."));
        EditText idea = new EditText(host.activity);
        idea.setHint("Ex.: cidade noturna abandonada, grande, com portão, "
                + "água, moradores e drones protegendo energia");
        idea.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        idea.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        idea.setMinLines(4);
        idea.setMaxLines(7);
        idea.setGravity(Gravity.TOP | Gravity.START);
        idea.setHorizontallyScrolling(false);
        idea.setVerticalScrollBarEnabled(true);
        idea.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        idea.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        form.addView(idea);
        TextView promptLimit = host.text("Até 1.000 caracteres. O campo rola "
                + "internamente quando o texto é grande.");
        promptLimit.setTextColor(0xFF9FB2BF);
        form.addView(promptLimit);
        TextView modelLabel = host.text("Modelo para criar este mapa");
        modelLabel.setPadding(0, 10, 0, 2);
        form.addView(modelLabel);
        Spinner model = new Spinner(host.activity);
        ArrayAdapter<String> models = new ArrayAdapter<>(host.activity,
                android.R.layout.simple_spinner_item,
                AiOpenAiClient.SCENARIO_MODEL_LABELS);
        models.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        model.setAdapter(models);
        model.setSelection(AiOpenAiClient.DEFAULT_SCENARIO_MODEL);
        form.addView(model);
        form.addView(host.text("Terra equilibra qualidade e custo; Sol pensa "
                + "mais e pode demorar/custar mais. Esta escolha afeta somente "
                + "a criação do mapa, não as falas dos NPCs."));
        TextView profileLabel = host.text("Tamanho e desempenho");
        profileLabel.setPadding(0, 16, 0, 2);
        form.addView(profileLabel);
        Spinner profile = new Spinner(host.activity);
        ArrayAdapter<String> profiles = new ArrayAdapter<>(host.activity,
                android.R.layout.simple_spinner_item,
                AiScenarioProfile.MODE_LABELS);
        profiles.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        profile.setAdapter(profiles);
        profile.setSelection(AiScenarioProfile.MODE_AUTO);
        form.addView(profile);
        form.addView(host.text("No modo enorme, portas ligam setores e apenas "
                + "um setor fica ativo na RAM/GPU por vez."));
        AlertDialog dialog = new AlertDialog.Builder(host.activity)
                .setTitle("Criar cenário com IA")
                .setView(host.scrollable(form))
                .setPositiveButton("GERAR", (ignored, which) -> {
                    if (mode.getSelectedItemPosition() == 1) {
                        generateFreeScenario(idea.getText().toString(),
                                model.getSelectedItemPosition());
                    } else {
                        generateScenario(idea.getText().toString(),
                                profile.getSelectedItemPosition(),
                                model.getSelectedItemPosition());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void generateScenario(String idea, int profileMode,
                                  int modelIndex) {
        final String key;
        final PrefabCatalog catalog;
        final String scenarioModel = AiOpenAiClient.scenarioModelAt(modelIndex);
        final String scenarioModelLabel =
                AiOpenAiClient.scenarioModelLabelAt(modelIndex);
        final long totalRam;
        final int heapMb;
        final int processors = Runtime.getRuntime().availableProcessors();
        try {
            host.gate.acquire();
            key = host.keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
            catalog = host.listener.aiCatalog();
            ActivityManager manager = (ActivityManager) host.activity
                    .getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory =
                    new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            totalRam = memory.totalMem;
            heapMb = manager.getMemoryClass();
            // Valida o texto antes de abrir a conexão.
            AiOpenAiClient.buildScenarioRequest(idea, scenarioModel);
        } catch (Exception failure) {
            host.toast(AiFeatureController.message(failure));
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AiOpenAiClient.Cancellation cancellation =
                new AiOpenAiClient.Cancellation();
        host.activeCancellation = cancellation;
        AlertDialog progress = host.busy("IA criando o plano…", () -> {
            cancelled.set(true);
            cancellation.cancel();
            Future<?> request = host.activeRequest;
            if (request != null) request.cancel(true);
        });
        final long started = android.os.SystemClock.elapsedRealtime();
        android.os.Handler ticker = new android.os.Handler(
                host.activity.getMainLooper());
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!progress.isShowing()) return;
                long seconds = (android.os.SystemClock.elapsedRealtime()
                        - started) / 1000;
                progress.setMessage("Pensando e escrevendo o plano… "
                        + seconds / 60 + "m" + String.format("%02d",
                        seconds % 60) + "s");
                ticker.postDelayed(this, 1000);
            }
        };
        ticker.post(tick);
        host.activeRequest = host.executor.submit(() -> {
            try {
                AiScenarioPlan plan = host.client.generateScenario(key, idea,
                        scenarioModel, cancellation);
                AiScenarioProfile resolved = AiScenarioProfile.resolve(
                        profileMode, plan.size, totalRam, heapMb, processors);
                List<MapDocument> documents =
                        AiScenarioBuilder.buildSeries(plan, resolved);
                for (MapDocument document : documents) {
                    List<ValidationIssue> issues =
                            MapValidator.validate(document, catalog);
                    if (MapValidator.hasError(issues)) {
                        throw new IOException("plano recusado pelo validador: "
                                + AiFeatureController.firstError(issues));
                    }
                    // Compilar prova que não há comportamento desconhecido.
                    LevelCompiler.compile(document, catalog);
                }
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.previews.previewScenario(plan, documents, resolved,
                            scenarioModelLabel, modelIndex);
                });
            } catch (Exception failure) {
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.showError("Não consegui gerar o cenário", failure);
                });
            } finally {
                if (host.activeCancellation == cancellation) {
                    host.activeCancellation = null;
                }
            }
        });
    }

    /** Modo LIVRE: a IA escreve o roteiro; validador/compilador filtram. */
    private void generateFreeScenario(String idea, int modelIndex) {
        final String key;
        final PrefabCatalog catalog;
        final List<String> prefabIds = new ArrayList<>();
        final String scenarioModel = AiOpenAiClient.scenarioModelAt(modelIndex);
        final String scenarioModelLabel =
                AiOpenAiClient.scenarioModelLabelAt(modelIndex);
        try {
            host.gate.acquire();
            key = host.keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
            catalog = host.listener.aiCatalog();
            for (br.com.termia.construajogue.prefab.PrefabDefinition
                    definition : catalog.all()) {
                prefabIds.add(definition.id);
            }
            // Valida o texto antes de abrir a conexão.
            AiOpenAiClient.buildFreeMapRequest(idea, scenarioModel,
                    prefabIds);
        } catch (Exception failure) {
            host.toast(AiFeatureController.message(failure));
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AiOpenAiClient.Cancellation cancellation =
                new AiOpenAiClient.Cancellation();
        host.activeCancellation = cancellation;
        AlertDialog progress = host.busy("IA desenhando o mapa (modo livre)",
                () -> {
                    cancelled.set(true);
                    cancellation.cancel();
                    Future<?> request = host.activeRequest;
                    if (request != null) request.cancel(true);
                });
        // Mostra a IA trabalhando: fase + cronômetro + comandos recebidos.
        AtomicReference<String> phase = new AtomicReference<>(
                "Conectando e raciocinando sobre o pedido…");
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
                        seconds % 60) + "s. Pode levar vários minutos; "
                        + "deixe o app aberto.");
                ticker.postDelayed(this, 1000);
            }
        };
        ticker.post(tick);
        host.activeRequest = host.executor.submit(() -> {
            try {
                String script = host.client.generateFreeMapScript(key, idea,
                        scenarioModel, prefabIds, (chars, lines) ->
                                phase.set("Desenhando o mapa: " + lines
                                        + " comandos (" + chars
                                        + " caracteres) recebidos…"),
                        cancellation);
                AiFreeMapScript.Result parsed = host.improve
                        .validateFreeScript(script, catalog);
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.previews.previewFreeMap(parsed, scenarioModelLabel,
                            modelIndex, false, null, null);
                });
            } catch (Exception failure) {
                host.activity.runOnUiThread(() -> {
                    if (cancelled.get() || host.activity.isFinishing()
                            || host.activity.isDestroyed()) return;
                    progress.dismiss();
                    host.showError("Não consegui desenhar o mapa livre",
                            failure);
                });
            } finally {
                if (host.activeCancellation == cancellation) {
                    host.activeCancellation = null;
                }
            }
        });
    }
}
