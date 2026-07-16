package br.com.termia.construajogue.editor.tools;

import br.com.termia.construajogue.editor.StructureRoles;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Regras de pavimentos do editor. O formato do mapa já guarda Y em todos os
 * objetos; esta classe deriva os andares dessa geometria sem introduzir um
 * campo novo nem invalidar mapas antigos.
 */
public final class StoryLevels {

    public static final float WALL_HEIGHT = 3f;
    public static final float SLAB_THICKNESS = 0.3f;
    private static final float EPSILON = 0.08f;

    private StoryLevels() {
    }

    /** Bases encontradas, incluindo o térreo e o topo de cada teto/laje. */
    public static List<Float> discover(MapDocument doc) {
        List<Float> values = new ArrayList<>();
        addUnique(values, 0f);
        for (StructureObject s : doc.structures) {
            String role = StructureRoles.roleOf(s);
            if (StructureObject.ROLE_FLOOR.equals(role) && s.role != null) {
                addUnique(values, top(s));
            } else if (StructureObject.ROLE_CEILING.equals(role)) {
                // O topo da laje é uma superfície pronta para o novo andar.
                addUnique(values, top(s));
            } else if (StructureObject.ROLE_WALL.equals(role)) {
                addUnique(values, bottom(s));
            }
        }
        for (LogicMarker marker : doc.markers) {
            addUnique(values, marker.y);
        }
        Collections.sort(values);
        return values;
    }

    /** Estruturas visíveis/editáveis no pavimento informado. */
    public static boolean belongs(StructureObject s, float baseY) {
        String role = StructureRoles.roleOf(s);
        if (StructureObject.ROLE_FLOOR.equals(role)) {
            return near(top(s), baseY);
        }
        if (StructureObject.ROLE_CEILING.equals(role)) {
            // Aparece no andar que ela cobre e também como laje compartilhada
            // no pavimento imediatamente acima.
            return near(bottom(s), baseY + WALL_HEIGHT)
                    || near(top(s), baseY);
        }
        return near(bottom(s), baseY);
    }

    /** Variante que também associa tetos de altura personalizada. */
    public static boolean belongs(StructureObject s, float baseY,
                                  List<Float> levels) {
        String role = StructureRoles.roleOf(s);
        if (StructureObject.ROLE_CEILING.equals(role)) {
            return near(top(s), baseY) || near(baseOf(s, levels), baseY);
        }
        if (StructureObject.ROLE_FLOOR.equals(role) && s.role == null) {
            // Heurística antiga pode chamar o primeiro degrau de "piso";
            // ele continua pertencendo ao pavimento abaixo, não vira andar.
            return near(ownerBelow(bottom(s), levels), baseY);
        }
        if (StructureObject.ROLE_BLOCK.equals(role)) {
            return near(ownerBelow(bottom(s), levels), baseY);
        }
        return belongs(s, baseY);
    }

    /**
     * Prefabs comuns ocupam o volume entre piso e teto. Luminárias de teto
     * são a exceção: ficam exatamente no limite superior do pavimento.
     */
    public static boolean belongs(PrefabInstance p, float baseY) {
        if ("prop.lamp.ceiling".equals(p.prefabId)) {
            return Math.abs(p.transform.y - (baseY + WALL_HEIGHT)) <= 0.36f;
        }
        return p.transform.y >= baseY - 0.12f
                && p.transform.y < baseY + WALL_HEIGHT - 0.12f;
    }

    public static boolean belongs(PrefabInstance p, float baseY,
                                  List<Float> levels) {
        return near(baseOf(p, levels), baseY);
    }

    public static boolean belongs(LogicMarker marker, float baseY) {
        return near(marker.y, baseY);
    }

    /** Pavimento natural de uma estrutura escolhida pela lista de objetos. */
    public static float baseOf(StructureObject s) {
        String role = StructureRoles.roleOf(s);
        if (StructureObject.ROLE_FLOOR.equals(role)) return top(s);
        if (StructureObject.ROLE_CEILING.equals(role)) {
            return bottom(s) - WALL_HEIGHT;
        }
        return bottom(s);
    }

    /**
     * Para teto, usa o pavimento real mais alto abaixo da placa. Isso mantém
     * tetos de 4 m associados ao mesmo andar, em vez de inventar base Y=1.
     */
    public static float baseOf(StructureObject s, List<Float> levels) {
        String role = StructureRoles.roleOf(s);
        if (StructureObject.ROLE_BLOCK.equals(role)
                || (StructureObject.ROLE_FLOOR.equals(role)
                && s.role == null)) {
            return ownerBelow(bottom(s), levels);
        }
        if (!StructureObject.ROLE_CEILING.equals(role)) return baseOf(s);
        float bottom = bottom(s);
        float top = top(s);
        float best = -Float.MAX_VALUE;
        for (float level : levels) {
            if (level <= bottom + EPSILON && !near(level, top)
                    && level > best) best = level;
        }
        return best == -Float.MAX_VALUE ? baseOf(s) : best;
    }

    /** Pavimento existente que melhor contém a peça. */
    public static float baseOf(PrefabInstance p, List<Float> levels) {
        if (levels.isEmpty()) return 0f;
        if ("prop.lamp.ceiling".equals(p.prefabId)) {
            float wanted = p.transform.y - WALL_HEIGHT;
            float best = levels.get(0);
            float distance = Math.abs(best - wanted);
            for (float level : levels) {
                float candidate = Math.abs(level - wanted);
                if (candidate < distance) {
                    distance = candidate;
                    best = level;
                }
            }
            return best;
        }
        return ownerBelow(p.transform.y, levels);
    }

    public static float bottom(StructureObject s) {
        return s.transform.y - s.half[1];
    }

    public static float top(StructureObject s) {
        return s.transform.y + s.half[1];
    }

    /** Arredonda a elevação para evitar níveis 3,2999997 na interface. */
    public static float normalize(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.round(value * 100f) / 100f;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private static void addUnique(List<Float> values, float raw) {
        if (!Float.isFinite(raw)) return;
        float value = normalize(raw);
        for (float existing : values) {
            if (near(existing, value)) return;
        }
        values.add(value);
    }

    private static float ownerBelow(float y, List<Float> levels) {
        if (levels == null || levels.isEmpty()) return 0f;
        float best = levels.get(0);
        boolean found = false;
        for (float level : levels) {
            if (level <= y + EPSILON && (!found || level > best)) {
                best = level;
                found = true;
            }
        }
        return found ? best : levels.get(0);
    }
}
