package br.com.termia.construajogue.sharing;

import android.os.Environment;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/** Exportação direta para /sdcard/TermIa/troca quando o aparelho permite. */
public final class MapExchange {

    private MapExchange() {
    }

    public static File directory() {
        return new File(Environment.getExternalStorageDirectory(),
                "TermIa/troca");
    }

    public static File export(MapDocument doc) throws IOException {
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("sem acesso a " + dir.getAbsolutePath());
        }
        String safe = doc.name == null ? "mapa"
                : doc.name.replaceAll("[^A-Za-z0-9._-]+", "-");
        if (safe.isEmpty()) safe = "mapa";
        File target = new File(dir, safe + "-" + shortId(doc.id) + ".json");
        File temp = new File(dir, target.getName() + ".tmp");
        byte[] bytes = MapJson.write(doc).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("não consegui substituir o arquivo");
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("não consegui concluir o arquivo");
        }
        return target;
    }

    public static File[] list() throws IOException {
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("sem acesso a " + dir.getAbsolutePath());
        }
        File[] files = dir.listFiles((parent, name) ->
                name.toLowerCase().endsWith(".json"));
        if (files == null) throw new IOException("não consegui listar a pasta");
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    public static MapDocument read(File file) throws IOException {
        long length = file.length();
        if (length < 1 || length > 2 * 1024 * 1024) {
            throw new IOException("arquivo vazio ou grande demais");
        }
        byte[] bytes = new byte[(int) length];
        try (FileInputStream input = new FileInputStream(file)) {
            int cursor = 0;
            while (cursor < bytes.length) {
                int read = input.read(bytes, cursor, bytes.length - cursor);
                if (read < 0) throw new IOException("arquivo truncado");
                cursor += read;
            }
        }
        try {
            return MapJson.read(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException broken) {
            throw new IOException("JSON de mapa inválido", broken);
        }
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) return "sem-id";
        return id.substring(0, Math.min(8, id.length()));
    }
}
