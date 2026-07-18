package br.com.termia.construajogue.map;

import java.util.Arrays;
import java.util.List;

/**
 * Limites de texto de NPC compartilhados entre o parser do modo Livre
 * (AiFreeMapScript) e o MapValidator — fonte única dos valores.
 */
public final class TextLimits {

    /** Propriedades de texto do NPC humano aceitas pelo formato. */
    public static final List<String> NPC_TEXT_PROPS = Arrays.asList(
            "name", "role", "greeting", "background", "combatLine1",
            "combatLine2", "combatLine3");

    private TextLimits() {
    }

    public static boolean isNpcTextProperty(String name) {
        return NPC_TEXT_PROPS.contains(name);
    }

    /** Máximo de caracteres por propriedade de texto. */
    public static int limit(String name) {
        if ("name".equals(name)) return 48;
        if ("role".equals(name)) return 80;
        if ("greeting".equals(name)) return 240;
        if ("background".equals(name)) return 600;
        if (name != null && name.startsWith("combatLine")) return 120;
        // controllerId e qualquer futuro texto restrito continuam pequenos.
        return 128;
    }
}
