package br.com.termia.construajogue.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * UI e trabalho assíncrono da IA, isolados da Activity e da thread GL.
 * Os fluxos moram em arquivos próprios do pacote: AiScenarioFlow (geração),
 * AiImproveFlow (revisão), AiPreviewDialogs (prévias) e AiNpcTalk (conversa);
 * este controller guarda chave, gate, executor e os ajudantes de diálogo.
 */
public final class AiFeatureController {

    public interface Listener {
        PrefabCatalog aiCatalog() throws IOException;

        void onAiScenarioSaved(List<MapDocument> documents);

        boolean aiGameActive();

        void pauseForAiDialog();

        void resumeAfterAiDialog();
    }

    final Activity activity;
    final MapStore store;
    final Listener listener;
    final AiKeyStore keys;
    final AiOpenAiClient client = new AiOpenAiClient();
    final AiRequestGate gate = new AiRequestGate();
    final NpcConversationMemory conversations = new NpcConversationMemory();
    final NpcVoice voice;
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> activeRequest;
    volatile AiOpenAiClient.Cancellation activeCancellation;

    private final AiScenarioFlow scenarios = new AiScenarioFlow(this);
    final AiImproveFlow improve = new AiImproveFlow(this);
    final AiPreviewDialogs previews = new AiPreviewDialogs(this);
    private final AiNpcTalk npcTalk = new AiNpcTalk(this);

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
                + "Alpine. A IA só devolve um plano Guiado ou comandos "
                + "fechados do modo Livre; nunca recebe terminal, arquivos "
                + "ou permissão para executar código.\n\n"
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
                .setTitle("IA pessoal")
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
        scenarios.promptScenario();
    }

    /** Abre a revisão de qualquer mapa, inclusive um ainda não salvo. */
    public void promptImproveMap(MapDocument current) {
        improve.promptImproveMap(current,
                AiOpenAiClient.DEFAULT_SCENARIO_MODEL, null);
    }

    /** Chamado na UI após a thread GL detectar o botão FALAR. */
    public void talk(RuntimeNpc npc, String mapName) {
        npcTalk.talk(npc, mapName);
    }

    /** Saudação por aproximação; nunca abre tela nem chama a API. */
    public void greet(RuntimeNpc npc) {
        npcTalk.greet(npc);
    }

    /** Fala já salva no mapa; nunca abre rede nem diálogo durante combate. */
    public void speakCombat(RuntimeNpc npc, String line) {
        npcTalk.speakCombat(npc, line);
    }

    /** Consome somente o resultado do reconhecedor de pergunta do NPC. */
    public boolean onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        return npcTalk.onActivityResult(requestCode, resultCode, data);
    }

    AlertDialog busy(String message, Runnable cancel) {
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

    void showError(String title, Exception failure) {
        showError(title, failure, null);
    }

    /** Em revisões de uma prévia, OK devolve ao mapa anterior. */
    void showError(String title, Exception failure, Runnable onBack) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message(failure))
                .setPositiveButton("OK", null)
                .create();
        if (onBack != null) {
            dialog.setOnDismissListener(ignored -> returnTo(onBack));
        }
        dialog.show();
    }

    /** Posta após o fechamento do diálogo atual para não sobrepor janelas. */
    void returnTo(Runnable action) {
        if (action == null || activity.isFinishing()
                || activity.isDestroyed()) return;
        activity.getWindow().getDecorView().post(action);
    }

    public void shutdown() {
        AiOpenAiClient.Cancellation cancellation = activeCancellation;
        if (cancellation != null) cancellation.cancel();
        Future<?> request = activeRequest;
        if (request != null) request.cancel(true);
        executor.shutdownNow();
        keys.clearSession();
        conversations.clear();
        voice.shutdown();
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources()
                .getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        return layout;
    }

    /** Conteúdo rola dentro do diálogo; a barra de ações fica sempre fora. */
    ScrollView scrollable(View content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    TextView text(String value) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(0xFFDDE7EE);
        view.setTextSize(14f);
        view.setPadding(0, 4, 0, 12);
        return view;
    }

    void toast(String value) {
        Toast.makeText(activity, value, Toast.LENGTH_LONG).show();
    }

    static String firstError(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.isError()) return issue.message;
        }
        return "erro desconhecido";
    }

    static String message(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.trim().isEmpty()
                ? failure.getClass().getSimpleName() : value;
    }

    static String compact(String value, int max) {
        String text = value == null ? "" : value.replace('\n', ' ')
                .replace('\r', ' ').trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
