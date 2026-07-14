package br.com.termia.construajogue.editor;

import br.com.termia.construajogue.map.StructureObject;

/**
 * Papel semântico e regra de altura das estruturas do editor. Mapas
 * antigos/convertidos não trazem `role`: cai numa heurística pelas
 * dimensões. A "altura" editável muda de sentido por papel:
 * piso = espessura (topo fixo), parede/bloco = altura (base fixa),
 * teto = elevação da base (espessura fixa).
 */
public final class StructureRoles {

    private StructureRoles() {
    }

    public static String roleOf(StructureObject s) {
        if (s.role != null) {
            return s.role;
        }
        boolean slab = s.half[1] <= 0.2f;
        if (slab) {
            return s.transform.y - s.half[1] >= 1f
                    ? StructureObject.ROLE_CEILING
                    : StructureObject.ROLE_FLOOR;
        }
        boolean thin = Math.min(s.half[0], s.half[2]) <= 0.3f;
        return thin && s.half[1] >= 1f
                ? StructureObject.ROLE_WALL : StructureObject.ROLE_BLOCK;
    }

    public static boolean isCeiling(StructureObject s) {
        return StructureObject.ROLE_CEILING.equals(roleOf(s));
    }

    public static String name(StructureObject s) {
        switch (roleOf(s)) {
            case StructureObject.ROLE_FLOOR: return "piso";
            case StructureObject.ROLE_WALL: return "parede";
            case StructureObject.ROLE_CEILING: return "teto";
            default: return "bloco";
        }
    }

    /** Valor que o diálogo ALTURA mostra/edita para esta estrutura. */
    public static float heightValue(StructureObject s) {
        return isCeiling(s) ? s.transform.y - s.half[1] : s.half[1] * 2f;
    }

    public static String heightLabel(StructureObject s) {
        switch (roleOf(s)) {
            case StructureObject.ROLE_CEILING:
                return "Elevação do teto (m)";
            case StructureObject.ROLE_FLOOR:
                return "Espessura do piso (m)";
            default:
                return "Altura (m)";
        }
    }

    public static void applyHeight(StructureObject s, float value) {
        float clamped = Math.max(0.05f, Math.min(10f, value));
        switch (roleOf(s)) {
            case StructureObject.ROLE_CEILING:
                // sobe/desce a placa inteira; espessura fica como está
                s.transform.y = clamped + s.half[1];
                break;
            case StructureObject.ROLE_FLOOR: {
                float top = s.transform.y + s.half[1];
                s.half[1] = clamped / 2f;
                s.transform.y = top - s.half[1];
                break;
            }
            default: {
                float base = s.transform.y - s.half[1];
                s.half[1] = clamped / 2f;
                s.transform.y = base + s.half[1];
                break;
            }
        }
    }

    /** Resumo da seleção mostrado na barra de status. */
    public static String describe(StructureObject s) {
        String size = String.format("%s  %.2f × %.2f m", name(s),
                s.half[0] * 2f, s.half[2] * 2f);
        if (isCeiling(s)) {
            return size + String.format("  ·  elevação %.2f m",
                    heightValue(s));
        }
        return size + String.format("  ·  altura %.2f m", heightValue(s));
    }
}
