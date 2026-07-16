package br.com.termia.construajogue.editor.tools;

import br.com.termia.construajogue.editor.StructureRoles;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.util.Ids;

/** Localização, criação e arraste de vãos retos/diagonais. */
public final class OpeningTool {

    private OpeningTool() {
    }

    public static StructureObject wallAt(MapDocument doc, float wx, float wz,
                                         float slack) {
        return wallAt(doc, wx, wz, slack, Float.NaN);
    }

    /** Procura apenas no andar ativo; NaN preserva chamadas antigas. */
    public static StructureObject wallAt(MapDocument doc, float wx, float wz,
                                         float slack, float activeBaseY) {
        StructureObject best = null;
        float bestDistance = Float.MAX_VALUE;
        for (StructureObject wall : doc.structures) {
            if (!Float.isNaN(activeBaseY)
                    && !StoryLevels.belongs(wall, activeBaseY)) continue;
            if (!StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(wall))) continue;
            if (wall.polygon != null && !WallGeometry.diagonal(wall)) continue;
            float distance = Math.max(0f,
                    WallGeometry.distanceTo(wall, wx, wz)
                            - WallGeometry.thickness(wall) * 0.5f);
            if (distance <= slack && distance < bestDistance) {
                bestDistance = distance;
                best = wall;
            }
        }
        return best;
    }

    public static WallOpening create(String type, StructureObject wall,
                                     float wx, float wz) {
        boolean bath = "window_bath".equals(type);
        boolean window = bath || WallOpening.WINDOW.equals(type);
        boolean portal = WallOpening.PORTAL.equals(type);
        WallOpening opening = new WallOpening(Ids.create(),
                window ? WallOpening.WINDOW : type);
        float wantedWidth = bath ? 0.6f : window ? 1.2f
                : portal ? 1.6f : 1.0f;
        opening.width = Math.min(wantedWidth,
                WallGeometry.halfLength(wall) * 2f);
        float wallHeight = wall.half[1] * 2f;
        float wantedHeight = bath ? 0.6f : window ? 1.2f
                : portal ? wallHeight : 2.1f;
        opening.height = Math.min(wantedHeight, wallHeight);
        float wantedSill = bath ? 1.5f : window ? 0.9f : 0f;
        // Em parede baixa, preserva um vão válido em vez de criar altura
        // negativa; o peitoril desce só o necessário.
        opening.sill = Math.min(wantedSill,
                Math.max(0f, wallHeight - opening.height));
        move(opening, wall, wx, wz, false);
        return opening;
    }

    public static void move(WallOpening opening, StructureObject wall,
                            float wx, float wz, boolean snapQuarter) {
        float along = WallGeometry.offsetAt(wall, wx, wz);
        if (snapQuarter) along = Math.round(along * 4f) / 4f;
        float halfLength = WallGeometry.halfLength(wall);
        float halfWidth = opening.width * 0.5f;
        opening.offset = Math.max(-halfLength + halfWidth,
                Math.min(halfLength - halfWidth, along));
    }
}
