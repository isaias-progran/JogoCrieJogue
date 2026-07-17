package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.util.Ids;

/** Contrato puro da revisão: aprovação sempre produz outro documento/ID. */
public final class AiMapRevision {

    private AiMapRevision() {
    }

    public static MapDocument copyForSave(MapDocument revised,
                                          String sourceName) {
        if (revised == null) {
            throw new IllegalArgumentException("revisão ausente");
        }
        MapDocument copy = MapJson.read(MapJson.write(revised));
        copy.id = Ids.create();
        String original = sourceName == null ? "" : sourceName.trim();
        if (copy.name == null || copy.name.trim().isEmpty()) {
            copy.name = original.isEmpty()
                    ? "Mapa melhorado" : original + " (melhorado)";
        } else if (copy.name.trim().equals(original)) {
            copy.name = copy.name.trim() + " (melhorado)";
        }
        return copy;
    }
}
