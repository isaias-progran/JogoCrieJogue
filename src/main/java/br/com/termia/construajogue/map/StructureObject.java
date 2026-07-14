package br.com.termia.construajogue.map;

/**
 * Estrutura desenhada pelo usuário. Fase 1 suporta apenas `block`
 * (paralelepípedo alinhado aos eixos: transform = centro, half = meias
 * dimensões — sem conversão para reproduzir bit a bit os níveis legados).
 * Fases seguintes acrescentam floor/wall/ceiling com polygon/path.
 */
public final class StructureObject {

    public static final String KIND_BLOCK = "block";

    public String id;
    public String kind;
    public Transform transform = new Transform();
    /** Meias dimensões {hx, hy, hz} para `block`. */
    public float[] half;
    /** Cor {r, g, b} 0..1. */
    public float[] color;

    public StructureObject() {
    }

    public StructureObject(String id, String kind) {
        this.id = id;
        this.kind = kind;
    }
}
