package br.com.termia.construajogue.ai;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Memória curta, somente em RAM, para o NPC não reiniciar a conversa. */
public final class NpcConversationMemory {

    private static final int MAX_NPCS = 8;
    private static final int MAX_TURNS = 3;

    private static final class Turn {
        final String question;
        final String answer;

        Turn(String question, String answer) {
            this.question = clean(question, 500);
            this.answer = clean(answer, 800);
        }
    }

    private final LinkedHashMap<String, ArrayDeque<Turn>> conversations =
            new LinkedHashMap<String, ArrayDeque<Turn>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, ArrayDeque<Turn>> eldest) {
                    return size() > MAX_NPCS;
                }
            };

    public synchronized void remember(String key, String question,
                                      String answer) {
        if (key == null || key.isEmpty()) return;
        ArrayDeque<Turn> turns = conversations.get(key);
        if (turns == null) {
            turns = new ArrayDeque<>();
            conversations.put(key, turns);
        }
        while (turns.size() >= MAX_TURNS) turns.removeFirst();
        turns.addLast(new Turn(question, answer));
    }

    /** Texto rotulado, limitado, tratado como dados não confiáveis na API. */
    public synchronized String recent(String key) {
        ArrayDeque<Turn> turns = conversations.get(key);
        if (turns == null || turns.isEmpty()) return "(primeira conversa)";
        StringBuilder out = new StringBuilder();
        for (Turn turn : turns) {
            if (out.length() > 0) out.append('\n');
            out.append("JOGADOR: ").append(turn.question)
                    .append("\nNPC: ").append(turn.answer);
        }
        return out.length() <= 2400 ? out.toString()
                : out.substring(out.length() - 2400);
    }

    public synchronized void clear() {
        conversations.clear();
    }

    private static String clean(String value, int max) {
        String text = value == null ? "" : value.trim()
                .replace('\u0000', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
