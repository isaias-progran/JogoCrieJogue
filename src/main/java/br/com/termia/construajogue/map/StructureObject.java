package br.com.termia.construajogue.map;

/**
 * Estrutura desenhada pelo usuário. Fase 1 suporta apenas `block`
 * (paralelepípedo alinhado aos eixos: transform = centro, half = meias
 * dimensões — sem conversão para reproduzir bit a bit os níveis legados).
 * Fases seguintes acrescentam floor/wall/ceiling com polygon/path.
 */
public final class StructureObject {

    public static final String KIND_BLOCK = "block";

    /** Papel semântico p/ o editor (altura padrão, desenho, encaixe). */
    public static final String ROLE_FLOOR = "floor";
    public static final String ROLE_WALL = "wall";
    public static final String ROLE_BLOCK = "block";
    public static final String ROLE_CEILING = "ceiling";

    public String id;
    public String kind;
    /** Opcional (mapas convertidos não têm); só o editor usa. */
    public String role;
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
