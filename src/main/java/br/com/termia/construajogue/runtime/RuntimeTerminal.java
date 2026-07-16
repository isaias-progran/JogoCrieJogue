package br.com.termia.construajogue.runtime;

/** Terminal interativo compilado, identificado para controlar portas. */
public final class RuntimeTerminal {
    public final String id;
    public final float x;
    public final float y;
    public final float z;
    /** Zero = livre; 1..N = precisa ser ativado nessa ordem. */
    public final int order;

    public RuntimeTerminal(String id, float x, float y, float z, int order) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.order = order;
    }

    public float[] point() {
        return new float[]{x, y, z};
    }
}
