package br.com.termia.construajogue;

import br.com.termia.construajogue.util.Json;

import java.util.List;
import java.util.Map;

public final class JsonTest {

    public static void main(String[] args) {
        Object parsed = Json.parse("{\"a\": [1, 2.5, -3e2], \"b\": "
                + "{\"texto\": \"olá \\\"mundo\\\"\\n\", \"v\": true, "
                + "\"nulo\": null}}");
        Map<?, ?> root = (Map<?, ?>) parsed;
        List<?> a = (List<?>) root.get("a");
        Check.equal(((Json.Num) a.get(0)).intValue(), 1, "int");
        Check.that(((Json.Num) a.get(1)).floatValue() == 2.5f, "float");
        Check.that(((Json.Num) a.get(2)).floatValue() == -300f, "expoente");
        Map<?, ?> b = (Map<?, ?>) root.get("b");
        Check.equal(b.get("texto"), "olá \"mundo\"\n", "escapes");
        Check.equal(b.get("v"), Boolean.TRUE, "booleano");
        Check.that(b.containsKey("nulo") && b.get("nulo") == null, "null");

        // roundtrip: o que escrevemos volta idêntico ao reler
        String written = Json.write(parsed);
        Check.equal(Json.write(Json.parse(written)), written, "roundtrip");

        // token de float preservado sem arredondamento duplo
        float ugly = 0.100000024f;
        String token = new Json.Num(ugly).raw;
        Check.that(Float.parseFloat(token) == ugly, "float exato");
        Check.equal(new Json.Num(3f).raw, "3", "inteiro compacto");

        Check.fails(() -> Json.parse("{\"a\": }"), "valor faltando");
        Check.fails(() -> Json.parse("[1, 2"), "array aberto");
        Check.fails(() -> Json.parse("{} extra"), "lixo no fim");
        Check.done("JsonTest");
    }
}
