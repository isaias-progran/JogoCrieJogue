package br.com.termia.construajogue;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.sharing.MapShareCodec;
import br.com.termia.construajogue.sharing.QrCode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class MapShareQrTest {

    private static int checks;

    public static void main(String[] args) throws Exception {
        MapDocument source = new MapDocument();
        source.id = "share-test";
        source.name = "Casa compartilhada ç";
        StructureObject floor = new StructureObject("floor", "block");
        floor.role = StructureObject.ROLE_FLOOR;
        floor.half = new float[]{3f, 0.15f, 2f};
        floor.color = new float[]{0.2f, 0.3f, 0.4f};
        source.structures.add(floor);

        String code = MapShareCodec.encode(source);
        check(code.startsWith("CJ2:"), "prefixo");
        MapDocument decoded = MapShareCodec.decode(code);
        check(MapJson.write(source).equals(MapJson.write(decoded)),
                "ida e volta sem perda");
        check(MapShareCodec.decode(MapJson.write(source)).name.equals(
                source.name), "aceita JSON cru");

        QrCode qr = QrCode.encodeText(code);
        check(qr.size >= 21 && (qr.size - 17) % 4 == 0, "tamanho QR");
        check(qr.module(0, 0), "finder superior esquerdo");
        writePbm(qr, new File("build/qr-test.pbm"));
        System.out.println("OK MapShareQrTest: " + checks
                + " verificações; QR=" + qr.size + " módulos");
    }

    private static void writePbm(QrCode qr, File target) throws Exception {
        int quiet = 4;
        int scale = 5;
        int side = (qr.size + quiet * 2) * scale;
        StringBuilder out = new StringBuilder(side * side * 2 + 32);
        out.append("P1\n").append(side).append(' ').append(side).append('\n');
        for (int y = -quiet * scale; y < (qr.size + quiet) * scale; y++) {
            for (int x = -quiet * scale; x < (qr.size + quiet) * scale; x++) {
                boolean dark = qr.module(Math.floorDiv(x, scale),
                        Math.floorDiv(y, scale));
                out.append(dark ? '1' : '0').append(' ');
            }
            out.append('\n');
        }
        try (FileOutputStream stream = new FileOutputStream(target)) {
            stream.write(out.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
