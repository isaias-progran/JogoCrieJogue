package br.com.termia.construajogue.prefab;

import br.com.termia.construajogue.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo carregado de assets/prefabs/catalog.json. Puro Java: no
 * aparelho recebe o InputStream do AssetManager; nos testes JVM, um
 * FileInputStream.
 */
public final class PrefabCatalog {

    private final Map<String, PrefabDefinition> byId = new LinkedHashMap<>();

    private PrefabCatalog() {
    }

    public static PrefabCatalog load(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = input.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return parse(new String(buffer.toByteArray(),
                StandardCharsets.UTF_8));
    }

    public static PrefabCatalog parse(String text) {
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException(
                    "catálogo: objeto JSON esperado");
        }
        Map<?, ?> root = (Map<?, ?>) parsed;
        Object list = root.get("prefabs");
        if (!(list instanceof List)) {
            throw new IllegalArgumentException(
                    "catálogo: lista 'prefabs' ausente");
        }
        PrefabCatalog catalog = new PrefabCatalog();
        for (Object item : (List<?>) list) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(
                        "catálogo: prefab deve ser objeto");
            }
            Map<?, ?> entry = (Map<?, ?>) item;
            PrefabDefinition def = new PrefabDefinition();
            def.id = string(entry, "id");
            def.name = string(entry, "name");
            def.category = string(entry, "category");
            def.behavior = string(entry, "behavior");
            Object properties = entry.get("properties");
            if (properties instanceof List) {
                for (Object property : (List<?>) properties) {
                    def.properties.add(property.toString());
                }
            }
            if (catalog.byId.put(def.id, def) != null) {
                throw new IllegalArgumentException(
                        "catálogo: id duplicado " + def.id);
            }
        }
        return catalog;
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(
                    "catálogo: campo '" + key + "' obrigatório");
        }
        return (String) value;
    }

    public PrefabDefinition find(String id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }
}
