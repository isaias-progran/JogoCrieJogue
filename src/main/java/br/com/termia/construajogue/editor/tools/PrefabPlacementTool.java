package br.com.termia.construajogue.editor.tools;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.util.Ids;

/** Criação, altura inicial e ligação automática das peças. */
public final class PrefabPlacementTool {

    private PrefabPlacementTool() {
    }

    public static PrefabInstance create(PrefabDefinition definition,
                                        float x, float z) {
        return create(definition, x, z, 0f);
    }

    public static PrefabInstance create(PrefabDefinition definition,
                                        float x, float z, float baseY) {
        PrefabInstance value = new PrefabInstance(Ids.create(), definition.id);
        value.transform.x = x;
        value.transform.z = z;
        value.transform.y = baseY + defaultY(definition);
        if (PrefabDefinition.BEHAVIOR_DOOR.equals(definition.behavior)) {
            value.properties.put("halfX", 1.5f);
            value.properties.put("halfY", 1.4f);
            value.properties.put("halfZ", 0.4f);
        } else if (PrefabDefinition.BEHAVIOR_AUTO_DOOR
                .equals(definition.behavior)) {
            value.properties.put("halfX", 0.55f);
            value.properties.put("halfY", 1.05f);
            value.properties.put("halfZ", 0.08f);
        } else if (PrefabDefinition.BEHAVIOR_NPC_HUMAN
                .equals(definition.behavior)) {
            value.properties.put("name", "Morador");
            value.properties.put("role", "habitante");
            value.properties.put("greeting", "Olá, viajante.");
            value.properties.put("background",
                    "Conhece esta região e ajuda quem passa.");
        } else if ("prop.lamp.street".equals(definition.id)) {
            value.properties.put("lightR", 1f);
            value.properties.put("lightG", 0.72f);
            value.properties.put("lightB", 0.38f);
            value.properties.put("lightRadius", 8f);
            value.properties.put("lightOffsetY", 3.35f);
        }
        return value;
    }

    public static void autoLinkDoors(MapDocument doc) {
        for (PrefabInstance door : doc.prefabs) {
            if (!"door.gate".equals(door.prefabId)
                    || doc.findInstance(door.stringProperty("controllerId"))
                    != null) continue;
            PrefabInstance nearest = null;
            float best = Float.MAX_VALUE;
            for (PrefabInstance terminal : doc.prefabs) {
                if (!terminal.prefabId.startsWith("terminal.")) continue;
                // Não liga automaticamente a um terminal coincidente no
                // andar de baixo. Ligações manuais ainda podem cruzar andares.
                float dy = terminal.transform.y - door.transform.y;
                if (Math.abs(dy) > 1f) continue;
                float dx = terminal.transform.x - door.transform.x;
                float dz = terminal.transform.z - door.transform.z;
                float distance = (float) Math.sqrt(
                        dx * dx + dy * dy + dz * dz);
                if (distance < best) {
                    best = distance;
                    nearest = terminal;
                }
            }
            if (nearest != null) door.properties.put("controllerId", nearest.id);
        }
    }

    public static float defaultY(PrefabDefinition definition) {
        switch (definition.behavior) {
            case PrefabDefinition.BEHAVIOR_DRONE:
            case PrefabDefinition.BEHAVIOR_DRONE_DORMANT:
            case PrefabDefinition.BEHAVIOR_KAMIKAZE:
            case PrefabDefinition.BEHAVIOR_BOSS:
                return 1.8f;
            case PrefabDefinition.BEHAVIOR_MUTANT: return 0.85f;
            case PrefabDefinition.BEHAVIOR_TURRET: return 0.55f;
            case PrefabDefinition.BEHAVIOR_NPC_HUMAN: return 0f;
            case PrefabDefinition.BEHAVIOR_TERMINAL:
            case PrefabDefinition.BEHAVIOR_DOOR: return 1.4f;
            case PrefabDefinition.BEHAVIOR_AUTO_DOOR: return 1.05f;
            case PrefabDefinition.BEHAVIOR_STATIC:
                if ("prop.lamp.ceiling".equals(definition.id)) return 3f;
                if ("prop.tv".equals(definition.id)) return 1.4f;
                if ("prop.mirror.round".equals(definition.id)) return 1.5f;
                return 0f;
            default: return 0.5f;
        }
    }
}
