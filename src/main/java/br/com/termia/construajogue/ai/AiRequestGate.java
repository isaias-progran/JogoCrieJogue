package br.com.termia.construajogue.ai;

/** Limite local: a IA nunca pode ser chamada por quadro nem em loop. */
public final class AiRequestGate {

    public static final int MAX_REQUESTS_PER_SESSION = 40;
    private static final long MIN_INTERVAL_MS = 2500L;

    private int count;
    private long last;

    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        if (count >= MAX_REQUESTS_PER_SESSION) {
            throw new IllegalStateException(
                    "limite de IA desta sessão atingido; reabra o jogo");
        }
        if (last != 0L && now - last < MIN_INTERVAL_MS) {
            throw new IllegalStateException(
                    "aguarde alguns segundos antes de chamar a IA novamente");
        }
        count++;
        last = now;
    }

    public synchronized int count() {
        return count;
    }
}
