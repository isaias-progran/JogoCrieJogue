package br.com.termia.construajogue.persistence;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.util.Json;

import java.util.Map;

/** Migra a árvore JSON em memória, uma versão por vez. */
public final class MapMigration {

    private MapMigration() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toCurrent(Object parsed) {
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("mapa: objeto JSON esperado");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        int schema = schemaOf(root);
        if (schema < 1) {
            throw new IllegalArgumentException("schema " + schema
                    + " não suportado");
        }
        if (schema > MapDocument.SCHEMA) {
            throw new IllegalArgumentException("schema " + schema
                    + " é mais novo que este aplicativo");
        }
        while (schema < MapDocument.SCHEMA) {
            switch (schema) {
                case 1:
                    migrate1To2(root);
                    schema = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "sem migração para schema " + (schema + 1));
            }
        }
        return root;
    }

    private static int schemaOf(Map<String, Object> root) {
        Object value = root.get("schema");
        return value instanceof Json.Num
                ? ((Json.Num) value).intValue() : -1;
    }

    /**
     * Schema 2 acrescenta objetivo, materiais e travas. Todos são
     * opcionais; a ausência preserva exatamente o comportamento antigo.
     */
    private static void migrate1To2(Map<String, Object> root) {
        root.put("schema", new Json.Num(2));
    }
}
