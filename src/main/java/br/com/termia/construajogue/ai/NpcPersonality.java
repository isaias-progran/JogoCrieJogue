package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.runtime.RuntimeNpc;

import java.util.Locale;

/**
 * Personalidade local e determinística compartilhada pelo texto e pelo TTS.
 * Não vem da rede nem concede capacidade nova ao modelo.
 */
public final class NpcPersonality {

    private static final NpcPersonality PARCEIRO = new NpcPersonality(
            "parceiro",
            "descontraído, parceiro e bem-humorado; usa de vez em quando "
                    + "gírias leves como 'bora', 'beleza', 'pô' e 'tá'",
            1.04f, 1.02f);
    private static final NpcPersonality PRATICO = new NpcPersonality(
            "prático",
            "direto, esperto e gente boa; fala sem enrolar e pode usar "
                    + "'bora', 'deu ruim', 'se liga' e 'tá de boa'",
            1.06f, 0.97f);
    private static final NpcPersonality CALMO = new NpcPersonality(
            "calmo",
            "calmo, acolhedor e paciente; fala de forma simples e pode usar "
                    + "'fica tranquilo', 'na boa' e 'vai dar certo'",
            0.92f, 0.98f);
    private static final NpcPersonality FIRME = new NpcPersonality(
            "firme",
            "firme, atento e econômico nas palavras; usa um tom cotidiano "
                    + "com 'olha só', 'fica esperto' e 'fechou'",
            0.98f, 0.91f);
    private static final NpcPersonality ANIMADO = new NpcPersonality(
            "animado",
            "animado, curioso e espontâneo; pode usar 'eita', 'bora lá', "
                    + "'massa' e 'valeu', sem transformar tudo em piada",
            1.10f, 1.07f);
    private static final NpcPersonality RESERVADO = new NpcPersonality(
            "reservado",
            "reservado, observador e levemente misterioso; fala de modo "
                    + "coloquial com 'sei não', 'vai por mim' e 'fica ligado'",
            0.89f, 0.92f);

    private static final NpcPersonality[] ALL = {
            PARCEIRO, PRATICO, CALMO, FIRME, ANIMADO, RESERVADO
    };

    private final String label;
    private final String promptDirection;
    private final float speechRate;
    private final float pitch;

    private NpcPersonality(String label, String promptDirection,
                           float speechRate, float pitch) {
        this.label = label;
        this.promptDirection = promptDirection;
        this.speechRate = speechRate;
        this.pitch = pitch;
    }

    public static NpcPersonality forNpc(RuntimeNpc npc) {
        if (npc == null) return PARCEIRO;
        return choose(npc.id, npc.name, npc.role, npc.background);
    }

    /** Público para manter a seleção estável verificável nos testes JVM. */
    public static NpcPersonality choose(String id, String name, String role,
                                        String background) {
        String story = lower(background);
        if (containsAny(story, "prátic", "diret", "objetiv")) {
            return PRATICO;
        }
        if (containsAny(story, "calm", "tranquil", "acolhed", "pacient")) {
            return CALMO;
        }
        if (containsAny(story, "firme", "séri", "disciplin", "atent")) {
            return FIRME;
        }
        if (containsAny(story, "animad", "brincalh", "extrovert")) {
            return ANIMADO;
        }
        if (containsAny(story, "reservad", "mister", "desconfiad")) {
            return RESERVADO;
        }
        if (containsAny(story, "parceir", "descontra", "gente boa")) {
            return PARCEIRO;
        }

        String occupation = lower(role);
        if (containsAny(occupation, "engenheir", "mecânic", "tecnic",
                "técnic", "eletric", "piloto", "guia")) {
            return PRATICO;
        }
        if (containsAny(occupation, "médic", "medic", "curandeir",
                "professor", "sábi", "sabi")) {
            return CALMO;
        }
        if (containsAny(occupation, "guard", "vigia", "soldad", "seguran",
                "comand", "policial", "militar")) {
            return FIRME;
        }
        if (containsAny(occupation, "artista", "músic", "music",
                "comerciante")) {
            return ANIMADO;
        }
        if (containsAny(occupation, "detetiv", "espi",
                "contraband", "sobrevivente", "furtiv")) {
            return RESERVADO;
        }
        String identity = safe(id) + '|' + safe(name) + '|' + safe(role);
        int hash = identity.hashCode() & 0x7fffffff;
        return ALL[hash % ALL.length];
    }

    public String label() {
        return label;
    }

    public String promptDirection() {
        return promptDirection;
    }

    public float speechRate() {
        return speechRate;
    }

    public float pitch() {
        return pitch;
    }

    private static String lower(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
