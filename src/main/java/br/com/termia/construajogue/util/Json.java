package br.com.termia.construajogue.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON mínimo e puro-Java (o org.json do android.jar é stub fora do
 * aparelho, então testes JVM precisam desta classe). Árvore: Map ordenado,
 * List, String, Boolean, Json.Num e null. Números ficam com o token
 * original: floatValue() usa Float.parseFloat direto, sem arredondamento
 * duplo via double — o compilador de mapas depende disso para reproduzir
 * bit a bit os níveis legados.
 */
public final class Json {

    /** Número preservando o token do arquivo (ou gerado de um float). */
    public static final class Num {
        public final String raw;

        public Num(String raw) {
            this.raw = raw;
        }

        public Num(float value) {
            float rounded = Math.round(value);
            this.raw = rounded == value && Math.abs(value) < 1e7f
                    ? Integer.toString((int) rounded) : Float.toString(value);
        }

        public Num(int value) {
            this.raw = Integer.toString(value);
        }

        public float floatValue() {
            return Float.parseFloat(raw);
        }

        public int intValue() {
            return (int) Float.parseFloat(raw);
        }
    }

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        parser.skipSpace();
        Object value = parser.readValue();
        parser.skipSpace();
        if (parser.pos != text.length()) {
            throw parser.fail("conteúdo após o fim do JSON");
        }
        return value;
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw fail("fim inesperado");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new TreeMap<>();
        pos++;
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw fail("chave esperada");
            }
            String key = readString();
            skipSpace();
            if (peek() != ':') {
                throw fail("':' esperado");
            }
            pos++;
            skipSpace();
            map.put(key, readValue());
            skipSpace();
            char sep = peek();
            pos++;
            if (sep == '}') {
                return map;
            }
            if (sep != ',') {
                throw fail("',' ou '}' esperado");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(readValue());
            skipSpace();
            char sep = peek();
            pos++;
            if (sep == ']') {
                return list;
            }
            if (sep != ',') {
                throw fail("',' ou ']' esperado");
            }
        }
    }

    private String readString() {
        StringBuilder out = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= text.length()) {
                throw fail("string sem fechamento");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char esc = text.charAt(pos++);
            switch (esc) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    out.append((char) Integer.parseInt(
                            text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: throw fail("escape inválido \\" + esc);
            }
        }
    }

    private Num readNumber() {
        int start = pos;
        while (pos < text.length()
                && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String raw = text.substring(start, pos);
        try {
            Float.parseFloat(raw);
        } catch (RuntimeException bad) {
            throw fail("número inválido '" + raw + "'");
        }
        return new Num(raw);
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos)) {
            throw fail("'" + word + "' esperado");
        }
        pos += word.length();
    }

    private char peek() {
        if (pos >= text.length()) {
            throw fail("fim inesperado");
        }
        return text.charAt(pos);
    }

    private void skipSpace() {
        while (pos < text.length()
                && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException fail(String reason) {
        int line = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return new IllegalArgumentException(
                "JSON linha " + line + ": " + reason);
    }

    // ---- escrita ----

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        out.append('\n');
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value,
                                   int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Num) {
            out.append(((Num) value).raw);
        } else if (value instanceof Float) {
            out.append(new Num((Float) value).raw);
        } else if (value instanceof Integer) {
            out.append(value.toString());
        } else if (value instanceof Map) {
            writeMap(out, (Map<?, ?>) value, depth);
        } else if (value instanceof List) {
            writeList(out, (List<?>) value, depth);
        } else {
            throw new IllegalArgumentException(
                    "tipo não suportado: " + value.getClass());
        }
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map,
                                 int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(out, depth + 1);
            writeString(out, entry.getKey().toString());
            out.append(": ");
            writeValue(out, entry.getValue(), depth + 1);
            out.append(++index < map.size() ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append('}');
    }

    private static void writeList(StringBuilder out, List<?> list,
                                  int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        boolean scalars = true;
        for (Object item : list) {
            if (item instanceof Map || item instanceof List) {
                scalars = false;
                break;
            }
        }
        if (scalars) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                writeValue(out, list.get(i), depth);
            }
            out.append(']');
            return;
        }
        out.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(out, depth + 1);
            writeValue(out, list.get(i), depth + 1);
            out.append(i + 1 < list.size() ? ",\n" : "\n");
        }
        indent(out, depth);
        out.append(']');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\t': out.append("\\t"); break;
                case '\r': out.append("\\r"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }
}
