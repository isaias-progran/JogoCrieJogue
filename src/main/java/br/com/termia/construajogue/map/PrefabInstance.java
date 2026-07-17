package br.com.termia.construajogue.map;

import java.util.Map;
import java.util.TreeMap;

/**
 * Peça pronta escolhida no catálogo. Guarda só a referência (`prefabId`),
 * transform, escala e propriedades permitidas — nunca malha ou collider.
 * Propriedades: valores String, Float ou Boolean.
 */
public final class PrefabInstance {

    public String id;
    public String prefabId;
    public Transform transform = new Transform();
    public float scale = 1f;
    /** Trava persistente do editor; não altera o comportamento no jogo. */
    public boolean locked;
    public final Map<String, Object> properties = new TreeMap<>();

    public PrefabInstance() {
    }

    public PrefabInstance(String id, String prefabId) {
        this.id = id;
        this.prefabId = prefabId;
    }

    public float floatProperty(String name, float fallback) {
        Object value = properties.get(name);
        return value instanceof Float ? (Float) value : fallback;
    }

    public String stringProperty(String name) {
        Object value = properties.get(name);
        return value instanceof String ? (String) value : null;
    }

    public boolean booleanProperty(String name, boolean fallback) {
        Object value = properties.get(name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
