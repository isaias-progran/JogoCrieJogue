package br.com.termia.construajogue.persistence;

import android.content.Context;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.util.Ids;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Mapas do usuário em filesDir/maps/<id>.json. Escrita atômica no padrão
 * do editor3d: grava .tmp completo, promove o atual a .bak e troca.
 */
public final class MapStore {

    /** Nome + id de um mapa salvo, para a lista da biblioteca. */
    public static final class Entry {
        public final String id;
        public final String name;

        Entry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final File dir;

    public MapStore(Context context) {
        dir = new File(context.getFilesDir(), "maps");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return entries;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".json")) {
                continue;
            }
            try {
                MapDocument doc = MapJson.read(readText(file));
                entries.add(new Entry(doc.id, doc.name));
            } catch (IOException | RuntimeException broken) {
                entries.add(new Entry(name.substring(0, name.length() - 5),
                        "(mapa danificado)"));
            }
        }
        return entries;
    }

    public MapDocument create(String name) throws IOException {
        MapDocument doc = new MapDocument();
        doc.id = Ids.create();
        doc.name = name;
        save(doc);
        return doc;
    }

    public MapDocument load(String id) throws IOException {
        return MapJson.read(readText(fileOf(id)));
    }

    public void save(MapDocument doc) throws IOException {
        File file = fileOf(doc.id);
        File temp = new File(dir, doc.id + ".tmp");
        File backup = new File(dir, doc.id + ".bak");
        byte[] bytes = MapJson.write(doc).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (file.exists()) {
            backup.delete();
            file.renameTo(backup);
        }
        if (!temp.renameTo(file)) {
            throw new IOException("não consegui trocar " + file.getName());
        }
    }

    public void delete(String id) {
        fileOf(id).delete();
        new File(dir, id + ".bak").delete();
        new File(dir, id + ".tmp").delete();
    }

    public MapDocument duplicate(String id) throws IOException {
        MapDocument copy = load(id);
        copy.id = Ids.create();
        copy.name = copy.name + " (cópia)";
        save(copy);
        return copy;
    }

    private File fileOf(String id) {
        return new File(dir, id + ".json");
    }

    private static String readText(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < bytes.length) {
                int read = in.read(bytes, off, bytes.length - off);
                if (read < 0) {
                    throw new IOException("arquivo truncado");
                }
                off += read;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
