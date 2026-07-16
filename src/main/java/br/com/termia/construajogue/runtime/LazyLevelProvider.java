package br.com.termia.construajogue.runtime;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Campanha que guarda somente IDs. Cada documento é lido, validado e
 * compilado quando seu setor começa; nenhum RuntimeLevel anterior é mantido
 * pelo provedor.
 */
public final class LazyLevelProvider implements LevelProvider {

    public interface Source {
        MapDocument load(String id) throws IOException;
    }

    private final Source source;
    private final PrefabCatalog catalog;
    private final List<String> ids;

    public LazyLevelProvider(Source source, PrefabCatalog catalog,
                             List<String> ids) {
        if (source == null || catalog == null || ids == null
                || ids.isEmpty()) {
            throw new IllegalArgumentException("campanha vazia");
        }
        this.source = source;
        this.catalog = catalog;
        this.ids = new ArrayList<>(ids);
    }

    @Override
    public int count() {
        return ids.size();
    }

    @Override
    public RuntimeLevel load(int index) throws IOException {
        if (index < 0 || index >= ids.size()) {
            throw new IndexOutOfBoundsException("mapa " + index);
        }
        MapDocument document = source.load(ids.get(index));
        List<ValidationIssue> issues = MapValidator.validate(document,
                catalog);
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                throw new IOException(document.name + ": " + issue.message);
            }
        }
        try {
            return LevelCompiler.compile(document, catalog);
        } catch (RuntimeException invalid) {
            throw new IOException(document.name + ": "
                    + invalid.getMessage(), invalid);
        }
    }
}
