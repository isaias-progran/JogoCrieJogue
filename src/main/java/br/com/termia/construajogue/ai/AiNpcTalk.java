package br.com.termia.construajogue.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.util.ArrayList;

/**
 * Conversa com NPC durante a partida (texto, voz e falas locais),
 * extraída mecanicamente do AiFeatureController.
 */
final class AiNpcTalk {

    private static final int REQUEST_NPC_SPEECH = 7341;

    private final AiFeatureController host;
    private boolean npcDialogOpen;
    private RuntimeNpc voiceQuestionNpc;
    private String voiceQuestionMap;

    AiNpcTalk(AiFeatureController host) {
        this.host = host;
    }

    /** Chamado na UI após a thread GL detectar o botão FALAR. */
    void talk(RuntimeNpc npc, String mapName) {
        if (npcDialogOpen || !host.listener.aiGameActive()) return;
        npcDialogOpen = true;
        host.voice.stop();
        host.listener.pauseForAiDialog();
        if (!host.keys.hasKey()) {
            AlertDialog dialog = new AlertDialog.Builder(host.activity)
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

        EditText question = new EditText(host.activity);
        question.setHint("O que você quer perguntar?");
        question.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        question.setMinLines(2);
        question.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(500)});
        boolean[] asking = {false};
        AlertDialog dialog = new AlertDialog.Builder(host.activity)
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
    void greet(RuntimeNpc npc) {
        if (npc == null || npcDialogOpen
                || !host.listener.aiGameActive()) return;
        host.voice.speak(npc, npc.greeting);
    }

    /** Fala já salva no mapa; nunca abre rede nem diálogo durante combate. */
    void speakCombat(RuntimeNpc npc, String line) {
        if (npc == null || line == null || npcDialogOpen
                || !host.listener.aiGameActive()) return;
        host.voice.speak(npc, AiFeatureController.compact(line, 120));
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
            host.activity.startActivityForResult(intent, REQUEST_NPC_SPEECH);
            return true;
        } catch (ActivityNotFoundException unavailable) {
            voiceQuestionNpc = null;
            voiceQuestionMap = null;
            host.toast("Reconhecimento de voz não disponível; use o teclado");
            return false;
        }
    }

    /** Consome somente o resultado do reconhecedor de pergunta do NPC. */
    boolean onActivityResult(int requestCode, int resultCode,
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
            host.toast("Não entendi a pergunta");
            finishNpc();
            return true;
        }
        askNpc(npc, mapName, results.get(0));
        return true;
    }

    private void askNpc(RuntimeNpc npc, String mapName, String question) {
        final String key;
        try {
            host.gate.acquire();
            key = host.keys.get();
            if (key == null) throw new IllegalStateException("chave ausente");
        } catch (Exception failure) {
            showNpcFailure(npc, failure);
            return;
        }
        // O jogador continua andando enquanto a resposta é calculada.
        host.listener.resumeAfterAiDialog();
        host.toast(npc.name + " responderá por voz");
        String conversationKey = mapName + "\u0000" + npc.id;
        String history = host.conversations.recent(conversationKey);
        AiOpenAiClient.Cancellation cancellation =
                new AiOpenAiClient.Cancellation();
        host.activeCancellation = cancellation;
        host.activeRequest = host.executor.submit(() -> {
            try {
                String answer = host.client.replyAsNpc(key, npc, mapName,
                        question, history, cancellation);
                host.conversations.remember(conversationKey, question, answer);
                host.activity.runOnUiThread(() -> {
                    if (!host.listener.aiGameActive()) {
                        npcDialogOpen = false;
                        return;
                    }
                    if (host.voice.speak(npc, answer)) {
                        host.toast(npc.name + ": "
                                + AiFeatureController.compact(answer, 180));
                        finishNpc();
                        return;
                    }
                    host.listener.pauseForAiDialog();
                    AlertDialog result = new AlertDialog.Builder(host.activity)
                            .setTitle(npc.name)
                            .setMessage(answer)
                            .setPositiveButton("Continuar", null)
                            .create();
                    result.setOnDismissListener(ignored -> finishNpc());
                    result.show();
                });
            } catch (Exception failure) {
                host.activity.runOnUiThread(() -> {
                    if (!host.listener.aiGameActive()) {
                        npcDialogOpen = false;
                        return;
                    }
                    showNpcFailure(npc, failure);
                });
            } finally {
                if (host.activeCancellation == cancellation) {
                    host.activeCancellation = null;
                }
            }
        });
    }

    private void showNpcFailure(RuntimeNpc npc, Exception failure) {
        host.listener.pauseForAiDialog();
        AlertDialog dialog = new AlertDialog.Builder(host.activity)
                .setTitle(npc.name)
                .setMessage(npc.greeting + "\n\nIA indisponível: "
                        + AiFeatureController.message(failure))
                .setPositiveButton("Continuar", null)
                .create();
        dialog.setOnDismissListener(ignored -> finishNpc());
        dialog.show();
    }

    private void finishNpc() {
        if (!npcDialogOpen) return;
        npcDialogOpen = false;
        host.listener.resumeAfterAiDialog();
    }
}
