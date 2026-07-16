package br.com.termia.construajogue.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Fonte editável e persistente de um mapa (ver docs/FORMATO-MAPA.md).
 * Guarda intenção: estruturas desenhadas, instâncias de prefab e
 * marcadores. Malhas, colliders e VBOs são derivados pelo LevelCompiler
 * e nunca salvos.
 */
public final class MapDocument {

    public static final int SCHEMA = 2;

    public String id;
    public String name = "";

    public float ambient = 0.35f;
    public float[] fogColor = {0.04f, 0.05f, 0.07f};
    public float fogFar = 30f;
    /** Céu: "none" (sem skybox), "day", "dusk" ou "night". */
    public String sky = "none";
    /** Áudio ambiente: auto, outdoor, tunnel ou industrial. */
    public String soundscape = "auto";

    /** Regra de vitória. Ausente em mapas antigos = chegar à saída. */
    public ObjectiveSpec objective = new ObjectiveSpec();

    public final List<StructureObject> structures = new ArrayList<>();
    public final List<PrefabInstance> prefabs = new ArrayList<>();
    public final List<LogicMarker> markers = new ArrayList<>();

    public LogicMarker firstMarker(String type) {
        for (LogicMarker marker : markers) {
            if (marker.type.equals(type)) {
                return marker;
            }
        }
        return null;
    }

    public PrefabInstance findInstance(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        for (PrefabInstance instance : prefabs) {
            if (instanceId.equals(instance.id)) {
                return instance;
            }
        }
        return null;
    }
}
