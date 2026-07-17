package br.com.termia.construajogue.runtime;

import android.content.res.AssetManager;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Campanha embutida nos assets. `.json` = documento validado e compilado
 * (mesmo pipeline dos mapas do usuário); `.txt` = formato legado.
 */
public final class AssetLevelProvider implements LevelProvider {

    private static final String[] LEVEL_PATHS = {
            "maps/arena.json", "levels/labirinto.txt"
    };

    private final AssetManager assets;
    private PrefabCatalog catalog;

    public AssetLevelProvider(AssetManager assets) {
        this.assets = assets;
    }

    @Override
    public int count() {
        return LEVEL_PATHS.length;
    }

    @Override
    public RuntimeLevel load(int index) throws IOException {
        String path = LEVEL_PATHS[index];
        if (!path.endsWith(".json")) {
            return LegacyLevelLoader.load(assets.open(path), path);
        }
        if (catalog == null) {
            catalog = PrefabCatalog.load(
                    assets.open("prefabs/catalog.json"));
        }
        MapDocument doc = MapJson.read(readAsset(path));
        RuntimeLevel level;
        try {
            level = LevelCompiler.compile(doc, catalog);
        } catch (RuntimeException invalid) {
            throw new IOException(path + ": " + invalid.getMessage(),
                    invalid);
        }
        List<ValidationIssue> issues = MapValidator.validate(doc, catalog,
                level);
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                throw new IOException(path + ": " + issue);
            }
        }
        return level;
    }

    private String readAsset(String path) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream input = assets.open(path)) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
