package br.com.termia.construajogue.util;

import java.util.UUID;

/** IDs estáveis: UUID aleatório em texto minúsculo. */
public final class Ids {

    private Ids() {
    }

    public static String create() {
        return UUID.randomUUID().toString();
    }
}
