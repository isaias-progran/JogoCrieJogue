package br.com.termia.construajogue.ai;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI e trabalho assíncrono da IA, isolados da Activity e da thread GL. */
public final class AiFeatureController {

    private static final int REQUEST_NPC_SPEECH = 7341;

    public interface Listener {
        PrefabCatalog aiCatalog() throws IOException;

        void onAiScenarioSaved(List<MapDocument> documents);

        boolean aiGameActive();

        void pauseForAiDialog();

        void resumeAfterAiDialog();
    }

    private final Activity activity;
    private final MapStore store;
    private final Listener listener;
    private final AiKeyStore keys;
    private final AiOpenAiClient client = new AiOpenAiClient();
    private final AiRequestGate gate = new AiRequestGate();
    private final NpcConversationMemory conversations =
            new NpcConversationMemory();
    private final NpcVoice voice;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> activeRequest;
    private boolean npcDialogOpen;
    private RuntimeNpc voiceQuestionNpc;
    private String voiceQuestionMap;

    public AiFeatureController(Activity activity, MapStore store,
                               Listener listener) {
        this.activity = activity;
        this.store = store;
        this.listener = listener;
        keys = new AiKeyStore(activity);
        voice = new NpcVoice(activity);
    }

    public void showSettings() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout form = column();
        TextView explanation = text("Integração pessoal, sem servidor e sem "
                + "Alpine. A IA só devolve um plano limitado; nunca recebe "
                + "terminal, arquivos ou permissão para executar código.\n\n"
                + "Mapas: modelo escolhido a cada geração (padrão "
                + AiOpenAiClient.MODEL + ").\n"
                + "NPCs: " + AiOpenAiClient.NPC_MODEL + "\n"
                + "Fala: " + voice.description() + "\n"
                + (keys.hasKey() ? "Chave já configurada. Deixe o campo "
                + "vazio para mantê-la." : "Digite uma chave exclusiva do "
                + "Project deste jogo."));
        form.addView(explanation);
        EditText input = new EditText(activity);
        input.setHint("chave API");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        form.addView(input);
        CheckBox remember = new CheckBox(activity);
        remember.setText("Lembrar neste aparelho (Android Keystore)");
        remember.setChecked(keys.remembersKey());
        form.addView(remember);
        TextView warning = text("Mais seguro: deixe desmarcado. A chave ficará "
                + "somente na memória até fechar o app. Em aparelho com root "
                + "não existe proteção absoluta. Use um Project exclusivo, "
                + "chave restrita e limite de gasto baixo; a API pode gerar "
                + "cobrança.");
        warning.setTextColor(0xFFFFC46B);
        form.addView(warning);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("IA experimental")
                .setView(scrollable(form))
                .setPositiveButton("SALVAR", null)
                .setNeutralButton(keys.hasKey() ? "APAGAR CHAVE" : "",
                        null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        try {
                            String value = input.getText().toString().trim();
                            if (value.isEmpty()) value = keys.get();
                            if (value == null) {
                                throw new IllegalArgumentException(
                                        "digite a chave API");
                            }
                            keys.save(value, remember.isChecked());
                            toast(remember.isChecked()
                                    ? "Chave protegida pelo Android Keystore"
                                    : "Chave mantida somente nesta sessão");
                            dialog.dismiss();
                        } catch (Exception failure) {
                            toast("Não consegui guardar: "
                                    + message(failure));
                        }
                    });
            if (keys.hasKey()) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                        .setOnClickListener(v -> {
                            keys.clear();
                            toast("Chave apagada");
                            dialog.dismiss();
                        });
            }
        });
        dialog.setOnDismissListener(ignored -> activity.getWindow()
                .clearFlags(WindowManager.LayoutParams.FLAG_SECURE));
        dialog.show();
    }

    public void promptScenario() {
        if (!keys.hasKey()) {
            toast("Configure sua chave primeiro");
            showSettings();
            return;
        }
        LinearLayout form = column();
        TextView modeLabel = text("Modo de criação");
        modeLabel.setPadding(0, 0, 0, 2);
        form.addView(modeLabel);
        Spinner mode = new Spinner(activity);
        ArrayAdapter<String> modes = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, new String[]{
                "Guiado — plano seguro (recomendado)",
                "Livre — a IA desenha tudo (experimental)"});
        modes.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(modes);
        mode.setSelection(0);
        form.addView(mode);
        form.addView(text("Guiado: a IA escolhe planta, rota, cômodos, "
                + "objetivo e NPC; o jogo monta com receitas prontas. "
                + "Livre: a IA desenha cada parede, peça e inimigo com "
                + "coordenadas próprias — mais criativo, gasta mais tokens "
                + "e pode falhar; o validador recusa mapa quebrado antes "
                + "de salvar."));
        EditText idea = new EditText(activity);
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
        TextView promptLimit = text("Até 1.000 caracteres. O campo rola "
                + "internamente quando o texto é grande.");
        promptLimit.setTextColor(0xFF9FB2BF);
        form.addView(promptLimit);
        TextView modelLabel = text("Modelo para criar este mapa");
        modelLabel.setPadding(0, 10, 0, 2);
        form.addView(modelLabel);
        Spinner model = new Spinner(activity);
        ArrayAdapter<String> models = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                AiOpenAiClient.SCENARIO_MODEL_LABELS);
        models.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        model.setAdapter(models);
        model.setSelection(AiOpenAiClient.DEFAULT_SCENARIO_MODEL);
        form.addView(model);
        form.addView(text("Terra equilibra qualidade e custo; Sol pensa mais "
                + "e pode demorar/custar mais. Esta escolha afeta somente a "
                + "criação do mapa, não as falas dos NPCs."));
        TextView profileLabel = text("Tamanho e desempenho");
        profileLabel.setPadding(0, 16, 0, 2);
        form.addView(profileLabel);
        Spinner profile = new Spinner(activity);
        ArrayAdapter<String> profiles = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                AiScenarioProfile.MODE_LABELS);
        profiles.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        profile.setAdapter(profiles);
        profile.setSelection(AiScenarioProfile.MODE_AUTO);
        form.addView(profile);
        form.addView(text("No modo enorme, portas ligam setores e apenas "
                + "um setor fica ativo na RAM/GPU por vez."));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Criar cenário com IA")
                .setView(scrollable(form))
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
            gate.acquire();
            key = keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
            catalog = listener.aiCatalog();
            ActivityManager manager = (ActivityManager) activity
                    .getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory =
                    new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            totalRam = memory.totalMem;
            heapMb = manager.getMemoryClass();
            // Valida o texto antes de abrir a conexão.
            AiOpenAiClient.buildScenarioRequest(idea, scenarioModel);
        } catch (Exception failure) {
            toast(message(failure));
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AlertDialog progress = busy("IA criando o plano…", () -> {
            cancelled.set(true);
            Future<?> request = activeRequest;
            if (request != null) request.cancel(true);
        });
        activeRequest = executor.submit(() -> {
            try {
                AiScenarioPlan plan = client.generateScenario(key, idea,
                        scenarioModel);
                AiScenarioProfile resolved = AiScenarioProfile.resolve(
                        profileMode, plan.size, totalRam, heapMb, processors);
                List<MapDocument> documents =
                        AiScenarioBuilder.buildSeries(plan, resolved);
                for (MapDocument document : documents) {
                    List<ValidationIssue> issues =
                            MapValidator.validate(document, catalog);
                    if (MapValidator.hasError(issues)) {
                        throw new IOException("plano recusado pelo validador: "
                                + firstError(issues));
                    }
                    // Compilar prova que não há comportamento desconhecido.
                    LevelCompiler.compile(document, catalog);
                }
                activity.runOnUiThread(() -> {
                    if (cancelled.get() || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    progress.dismiss();
                    previewScenario(plan, documents, resolved,
                            scenarioModelLabel);
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> {
                    if (cancelled.get() || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    progress.dismiss();
                    showError("Não consegui gerar o cenário", failure);
                });
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
            gate.acquire();
            key = keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
            catalog = listener.aiCatalog();
            for (br.com.termia.construajogue.prefab.PrefabDefinition
                    definition : catalog.all()) {
                prefabIds.add(definition.id);
            }
            // Valida o texto antes de abrir a conexão.
            AiOpenAiClient.buildFreeMapRequest(idea, scenarioModel,
                    prefabIds);
        } catch (Exception failure) {
            toast(message(failure));
            return;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        AlertDialog progress = busy("IA desenhando o mapa (modo livre)…",
                () -> {
                    cancelled.set(true);
                    Future<?> request = activeRequest;
                    if (request != null) request.cancel(true);
                });
        activeRequest = executor.submit(() -> {
            try {
                String script = client.generateFreeMapScript(key, idea,
                        scenarioModel, prefabIds);
                AiFreeMapScript.Result parsed =
                        AiFreeMapScript.parse(script, catalog);
                List<ValidationIssue> issues =
                        MapValidator.validate(parsed.document, catalog);
                if (MapValidator.hasError(issues)) {
                    throw new IOException("o desenho da IA foi recusado "
                            + "pelo validador: " + firstError(issues)
                            + "\n\nTente gerar de novo ou mude o pedido.");
                }
                LevelCompiler.compile(parsed.document, catalog);
                activity.runOnUiThread(() -> {
                    if (cancelled.get() || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    progress.dismiss();
                    previewFreeMap(parsed, scenarioModelLabel);
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> {
                    if (cancelled.get() || activity.isFinishing()
                            || activity.isDestroyed()) return;
                    progress.dismiss();
                    showError("Não consegui desenhar o mapa livre", failure);
                });
            }
        });
    }

    private void previewFreeMap(AiFreeMapScript.Result parsed,
                                String modelLabel) {
        MapDocument document = parsed.document;
        StringBuilder details = new StringBuilder();
        details.append("Mapa desenhado livremente pela IA.\n")
                .append("Modelo: ").append(modelLabel).append('\n')
                .append(document.structures.size()).append(" estruturas · ")
                .append(document.prefabs.size()).append(" peças\n")
                .append("Objetivo: ").append(document.objective.type);
        if (!parsed.warnings.isEmpty()) {
            details.append("\n\nAvisos (").append(parsed.warnings.size())
                    .append("):");
            int shown = 0;
            for (String warning : parsed.warnings) {
                if (shown++ >= 8) {
                    details.append("\n…");
                    break;
                }
                details.append("\n• ").append(warning);
            }
        }
        details.append("\n\nNada foi salvo ainda.");
        new AlertDialog.Builder(activity)
                .setTitle(document.name)
                .setMessage(details.toString())
                .setPositiveButton("SALVAR E CONSTRUIR", (dialog, which) -> {
                    try {
                        store.save(document);
                        List<MapDocument> documents = new ArrayList<>();
                        documents.add(document);
                        listener.onAiScenarioSaved(documents);
                    } catch (IOException failure) {
                        showError("Não consegui salvar", failure);
                    }
                })
                .setNegativeButton("Descartar", null)
                .show();
    }

    private void previewScenario(AiScenarioPlan plan,
                                 List<MapDocument> documents,
                                 AiScenarioProfile profile,
                                 String modelLabel) {
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
        new AlertDialog.Builder(activity)
                .setTitle(first.name)
                .setMessage(details)
                .setPositiveButton(documents.size() == 1
                                ? "SALVAR E CONSTRUIR"
                                : "SALVAR E JOGAR SETORES",
                        (dialog, which) -> {
                    List<String> saved = new ArrayList<>();
                    try {
                        for (MapDocument document : documents) {
                            store.save(document);
                            saved.add(document.id);
                        }
                        listener.onAiScenarioSaved(documents);
                    } catch (IOException failure) {
                        for (String id : saved) store.delete(id);
                        showError("Não consegui salvar", failure);
                    }
                })
                .setNegativeButton("Descartar", null)
                .show();
    }

    /** Chamado na UI após a thread GL detectar o botão FALAR. */
    public void talk(RuntimeNpc npc, String mapName) {
        if (npcDialogOpen || !listener.aiGameActive()) return;
        npcDialogOpen = true;
        voice.stop();
        listener.pauseForAiDialog();
        if (!keys.hasKey()) {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(npc.name + " — " + npc.role)
                    .setMessage(npc.greeting + "\n\n" + npc.background
                            + "\n\nConfigure a IA na biblioteca para "
                            + "conversar livremente.")
                    .setPositiveButton("Fechar", null)
                    .create();
            dialog.setOnDismissListener(ignored -> finishNpc());
            dialog.show();
            return;
        }

        EditText question = new EditText(activity);
        question.setHint("O que você quer perguntar?");
        question.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        question.setMinLines(2);
        question.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        boolean[] asking = {false};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(npc.name + " — " + npc.role)
                .setMessage("Conversa em andamento. Pergunte por texto ou voz.")
                .setView(question)
                .setPositiveButton("PERGUNTAR À IA", (ignored, which) -> {
                    asking[0] = true;
                    askNpc(npc, mapName, question.getText().toString());
                })
                .setNeutralButton("🎤 FALAR PERGUNTA", null)
                .setNegativeButton("Só ouvir", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (!asking[0]) finishNpc();
        });
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
                .setOnClickListener(v -> {
                    if (startVoiceQuestion(npc, mapName)) {
                        asking[0] = true;
                        dialog.dismiss();
                    }
                });
    }

    /** Saudação por aproximação; nunca abre tela nem chama a API. */
    public void greet(RuntimeNpc npc) {
        if (npc == null || npcDialogOpen || !listener.aiGameActive()) return;
        voice.speak(npc, npc.greeting);
    }

    private boolean startVoiceQuestion(RuntimeNpc npc, String mapName) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Pergunte a " + npc.name);
        try {
            voiceQuestionNpc = npc;
            voiceQuestionMap = mapName;
            activity.startActivityForResult(intent, REQUEST_NPC_SPEECH);
            return true;
        } catch (ActivityNotFoundException unavailable) {
            voiceQuestionNpc = null;
            voiceQuestionMap = null;
            toast("Reconhecimento de voz não disponível; use o teclado");
            return false;
        }
    }

    /** Consome somente o resultado do reconhecedor de pergunta do NPC. */
    public boolean onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode != REQUEST_NPC_SPEECH) return false;
        RuntimeNpc npc = voiceQuestionNpc;
        String mapName = voiceQuestionMap;
        voiceQuestionNpc = null;
        voiceQuestionMap = null;
        if (resultCode != Activity.RESULT_OK || data == null || npc == null) {
            finishNpc();
            return true;
        }
        ArrayList<String> results = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()
                || results.get(0).trim().isEmpty()) {
            toast("Não entendi a pergunta");
            finishNpc();
            return true;
        }
        askNpc(npc, mapName, results.get(0));
        return true;
    }

    private void askNpc(RuntimeNpc npc, String mapName, String question) {
        final String key;
        try {
            gate.acquire();
            key = keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
        } catch (Exception failure) {
            showNpcFailure(npc, failure);
            return;
        }
        // O jogador continua andando enquanto a resposta é calculada.
        listener.resumeAfterAiDialog();
        toast(npc.name + " responderá por voz");
        String conversationKey = mapName + "\u0000" + npc.id;
        String history = conversations.recent(conversationKey);
        activeRequest = executor.submit(() -> {
            try {
                String answer = client.replyAsNpc(key, npc, mapName, question,
                        history);
                conversations.remember(conversationKey, question, answer);
                activity.runOnUiThread(() -> {
                    if (!listener.aiGameActive()) {
                        npcDialogOpen = false;
                        return;
                    }
                    if (voice.speak(npc, answer)) {
                        toast(npc.name + ": " + compact(answer, 180));
                        finishNpc();
                        return;
                    }
                    listener.pauseForAiDialog();
                    AlertDialog result = new AlertDialog.Builder(activity)
                            .setTitle(npc.name)
                            .setMessage(answer)
                            .setPositiveButton("Continuar", null)
                            .create();
                    result.setOnDismissListener(ignored -> finishNpc());
                    result.show();
                });
            } catch (Exception failure) {
                activity.runOnUiThread(() -> {
                    if (!listener.aiGameActive()) {
                        npcDialogOpen = false;
                        return;
                    }
                    showNpcFailure(npc, failure);
                });
            }
        });
    }

    private void showNpcFailure(RuntimeNpc npc, Exception failure) {
        listener.pauseForAiDialog();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(npc.name)
                .setMessage(npc.greeting + "\n\nIA indisponível: "
                        + message(failure))
                .setPositiveButton("Continuar", null)
                .create();
        dialog.setOnDismissListener(ignored -> finishNpc());
        dialog.show();
    }

    private void finishNpc() {
        if (!npcDialogOpen) return;
        npcDialogOpen = false;
        listener.resumeAfterAiDialog();
    }

    private AlertDialog busy(String message, Runnable cancel) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(message)
                .setMessage("A partida e a interface não executam a resposta "
                        + "como código.")
                .setNegativeButton("Cancelar", (ignored, which) ->
                        cancel.run())
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }

    private void showError(String title, Exception failure) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message(failure))
                .setPositiveButton("OK", null)
                .show();
    }

    public void shutdown() {
        Future<?> request = activeRequest;
        if (request != null) request.cancel(true);
        executor.shutdownNow();
        keys.clearSession();
        conversations.clear();
        voice.shutdown();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources()
                .getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        return layout;
    }

    /** Conteúdo rola dentro do diálogo; a barra de ações fica sempre fora. */
    private ScrollView scrollable(View content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView text(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(0xFFDDE7EE);
        view.setTextSize(14f);
        view.setPadding(0, 4, 0, 12);
        return view;
    }

    private void toast(String value) {
        Toast.makeText(activity, value, Toast.LENGTH_LONG).show();
    }

    private static String firstError(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.isError()) return issue.message;
        }
        return "erro desconhecido";
    }

    private static String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.trim().isEmpty()
                ? failure.getClass().getSimpleName() : value;
    }

    private static String compact(String value, int max) {
        String text = value == null ? "" : value.replace('\n', ' ')
                .replace('\r', ' ').trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
