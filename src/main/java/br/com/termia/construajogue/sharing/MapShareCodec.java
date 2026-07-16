package br.com.termia.construajogue.sharing;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Código compacto, copiável e adequado a QR para um mapa JSON. */
public final class MapShareCodec {

    public static final String PREFIX = "CJ2:";
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;

    private MapShareCodec() {
    }

    public static String encode(MapDocument doc) {
        byte[] json = MapJson.write(doc).getBytes(StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(json);
            }
            return PREFIX + Base64Url.encode(bytes.toByteArray());
        } catch (IOException impossibleInMemory) {
            throw new IllegalStateException(impossibleInMemory);
        }
    }

    public static MapDocument decode(String input) {
        String clean = input == null ? "" : input.trim();
        if (clean.startsWith("{")) {
            return MapJson.read(clean);
        }
        if (!clean.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new IllegalArgumentException(
                    "código não começa com " + PREFIX);
        }
        try {
            byte[] packed = Base64Url.decode(
                    clean.substring(PREFIX.length()));
            ByteArrayOutputStream json = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(
                    new ByteArrayInputStream(packed))) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = gzip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    if (json.size() + read > MAX_JSON_BYTES) {
                        throw new IllegalArgumentException("mapa grande demais");
                    }
                    json.write(buffer, 0, read);
                }
            }
            return MapJson.read(new String(json.toByteArray(),
                    StandardCharsets.UTF_8));
        } catch (IllegalArgumentException badCode) {
            throw badCode;
        } catch (IOException | RuntimeException broken) {
            throw new IllegalArgumentException("código de mapa danificado",
                    broken);
        }
    }
}
