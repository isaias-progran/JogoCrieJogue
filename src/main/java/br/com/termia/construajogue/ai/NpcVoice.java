package br.com.termia.construajogue.ai;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.util.Locale;
import java.util.Set;

/** Voz curta do NPC pelo mecanismo TTS escolhido no Android. */
public final class NpcVoice implements TextToSpeech.OnInitListener {

    private final TextToSpeech speech;
    private boolean ready;
    private boolean failed;
    private boolean closed;
    private PendingSpeech pending;
    private String selected = "selecionando voz pt-BR";

    private static final class PendingSpeech {
        final String text;
        final NpcPersonality personality;

        PendingSpeech(String text, NpcPersonality personality) {
            this.text = text;
            this.personality = personality;
        }
    }

    public NpcVoice(Context context) {
        speech = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public synchronized void onInit(int status) {
        if (closed) return;
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            pending = null;
            return;
        }
        Locale portuguese = new Locale("pt", "BR");
        int language = speech.setLanguage(portuguese);
        if (language == TextToSpeech.LANG_MISSING_DATA
                || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            failed = true;
            pending = null;
            return;
        }
        Voice best = bestVoice(speech.getVoices(), portuguese);
        if (best != null && speech.setVoice(best) == TextToSpeech.SUCCESS) {
            selected = voiceDescription(best);
        } else {
            Voice fallback = speech.getVoice();
            selected = fallback == null ? "voz pt-BR padrão"
                    : voiceDescription(fallback);
        }
        ready = true;
        PendingSpeech queued = pending;
        pending = null;
        if (queued != null) speakNow(queued.text, queued.personality);
    }

    /** Retorna false somente quando já sabemos que não há voz utilizável. */
    public synchronized boolean speak(String value) {
        return speak(null, value);
    }

    /** Usa a mesma voz instalada com ritmo e tom estáveis para cada NPC. */
    public synchronized boolean speak(RuntimeNpc npc, String value) {
        String text = clean(value);
        if (text.isEmpty() || closed || failed) return false;
        NpcPersonality personality = NpcPersonality.forNpc(npc);
        if (!ready) {
            pending = new PendingSpeech(text, personality);
            return true;
        }
        return speakNow(text, personality);
    }

    public synchronized void stop() {
        pending = null;
        if (ready && !closed) speech.stop();
    }

    public synchronized void shutdown() {
        if (closed) return;
        closed = true;
        pending = null;
        speech.stop();
        speech.shutdown();
    }

    public synchronized String description() {
        return failed ? "voz pt-BR indisponível"
                : selected + " · tom variável por NPC";
    }

    private boolean speakNow(String text, NpcPersonality personality) {
        speech.setSpeechRate(personality.speechRate());
        speech.setPitch(personality.pitch());
        Bundle parameters = new Bundle();
        int result = speech.speak(text, TextToSpeech.QUEUE_FLUSH, parameters,
                "npc-" + System.nanoTime());
        return result == TextToSpeech.SUCCESS;
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.trim()
                .replace('\u0000', ' ');
        return text.length() <= 800 ? text : text.substring(0, 800);
    }

    /** Prefere a voz pt-BR de maior qualidade, inclusive a natural online. */
    private static Voice bestVoice(Set<Voice> voices, Locale wanted) {
        if (voices == null) return null;
        Voice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice voice : voices) {
            Locale locale = voice.getLocale();
            if (locale == null || !"pt".equalsIgnoreCase(
                    locale.getLanguage())) continue;
            Set<String> features = voice.getFeatures();
            if (features != null && features.contains(
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                continue;
            }
            int score = voice.getQuality() * 100;
            if (wanted.getCountry().equalsIgnoreCase(locale.getCountry())) {
                score += 100000;
            } else {
                score += 50000;
            }
            // Nas implementações Google, as vozes de rede costumam ser as
            // variantes naturais; qualidade declarada ainda pesa mais.
            if (voice.isNetworkConnectionRequired()) score += 3500;
            score -= voice.getLatency() * 2;
            String name = voice.getName() == null ? ""
                    : voice.getName().toLowerCase(Locale.ROOT);
            if (name.contains("natural") || name.contains("neural")
                    || name.contains("wavenet") || name.contains("network")) {
                score += 2500;
            }
            if (name.contains("legacy") || name.contains("compact")) {
                score -= 2500;
            }
            if (score > bestScore) {
                best = voice;
                bestScore = score;
            }
        }
        return best;
    }

    private static String voiceDescription(Voice voice) {
        String locale = voice.getLocale() == null ? "pt-BR"
                : voice.getLocale().toLanguageTag();
        String quality = voice.getQuality() >= Voice.QUALITY_VERY_HIGH
                ? "muito alta" : voice.getQuality() >= Voice.QUALITY_HIGH
                ? "alta" : "padrão";
        return "voz " + locale + " · qualidade " + quality
                + (voice.isNetworkConnectionRequired() ? " · online" : "");
    }
}
