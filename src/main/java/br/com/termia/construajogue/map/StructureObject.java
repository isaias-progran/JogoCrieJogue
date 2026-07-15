package br.com.termia.construajogue.map;

/**
 * Estrutura desenhada pelo usuário. Fase 1 suporta apenas `block`
 * (paralelepípedo alinhado aos eixos: transform = centro, half = meias
 * dimensões — sem conversão para reproduzir bit a bit os níveis legados).
 * Fases seguintes acrescentam floor/wall/ceiling com polygon/path.
 */
public final class StructureObject {

    public static final String KIND_BLOCK = "block";
    /** Laje poligonal desenhada por pontos (contorno livre). */
    public static final String KIND_POLY = "poly";

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
    /**
     * Cor da face larga voltada para o lado POSITIVO do eixo fino
     * (parede pintada por lado). null = usa `color`.
     */
    public float[] color2;
    /**
     * Cor da face larga do lado NEGATIVO. null = usa `color`. A cor
     * base `color` fica só nas pontas/topo — pintar um lado nunca
     * muda a base (senão a cor vaza pela ponta no canto).
     */
    public float[] color3;
    /** Vãos (só faz sentido em paredes). */
    public final java.util.List<WallOpening> openings =
            new java.util.ArrayList<>();
    /**
     * Contorno em pares x,z ABSOLUTOS (kind poly). transform.x/z e
     * half[0]/half[2] guardam o retângulo envolvente sincronizado
     * (seleção/medidas); transform.y e half[1] dão base e espessura.
     */
    public float[] polygon;

    /** Recalcula o envolvente a partir do contorno. */
    public void syncPolyBounds() {
        if (polygon == null) {
            return;
        }
        if (half == null) {
            half = new float[]{0f, 0.15f, 0f};
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < polygon.length; i += 2) {
            minX = Math.min(minX, polygon[i]);
            maxX = Math.max(maxX, polygon[i]);
            minZ = Math.min(minZ, polygon[i + 1]);
            maxZ = Math.max(maxZ, polygon[i + 1]);
        }
        transform.x = (minX + maxX) / 2f;
        transform.z = (minZ + maxZ) / 2f;
        half[0] = (maxX - minX) / 2f;
        half[2] = (maxZ - minZ) / 2f;
    }

    public StructureObject() {
    }

    public StructureObject(String id, String kind) {
        this.id = id;
        this.kind = kind;
    }
}
