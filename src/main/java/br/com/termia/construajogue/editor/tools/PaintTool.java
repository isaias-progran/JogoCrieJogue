package br.com.termia.construajogue.editor.tools;

import br.com.termia.construajogue.editor.StructureRoles;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;

import java.util.ArrayList;
import java.util.List;

/** Regras da tinta simples/lados/balde, independentes da View. */
public final class PaintTool {

    private PaintTool() {
    }

    public static void apply(MapDocument doc, StructureObject target,
                             float wx, float wz, float[] color,
                             boolean bucket) {
        boolean wall = StructureObject.ROLE_WALL
                .equals(StructureRoles.roleOf(target));
        if (!wall) {
            target.color = color.clone();
            target.color2 = null;
            target.color3 = null;
        } else if (bucket) {
            for (StructureObject connected : connectedWalls(doc, target)) {
                paintWallSide(connected, wx, wz, color, false);
            }
        } else {
            paintWallSide(target, wx, wz, color, true);
        }
    }

    public static void paintWallSide(StructureObject wall,
                                     float wx, float wz, float[] color,
                                     boolean allowBoth) {
        if (WallGeometry.diagonal(wall)) {
            float[] center = WallGeometry.pointAt(wall, 0f);
            float[] normal = WallGeometry.positiveNormal(wall);
            float d = (wx - center[0]) * normal[0]
                    + (wz - center[1]) * normal[1];
            if (allowBoth && Math.abs(d)
                    <= WallGeometry.thickness(wall) * 0.17f) {
                setWhole(wall, color);
            } else if (d > 0f) {
                wall.color2 = color.clone();
            } else {
                wall.color3 = color.clone();
            }
            return;
        }
        boolean thinX = wall.half[0] < wall.half[2];
        float d = thinX ? wx - wall.transform.x : wz - wall.transform.z;
        float half = thinX ? wall.half[0] : wall.half[2];
        if (allowBoth && Math.abs(d) <= half * 0.34f) {
            setWhole(wall, color);
        } else if (d > 0f) {
            wall.color2 = color.clone();
        } else {
            wall.color3 = color.clone();
        }
    }

    private static void setWhole(StructureObject value, float[] color) {
        value.color = color.clone();
        value.color2 = null;
        value.color3 = null;
    }

    private static List<StructureObject> connectedWalls(MapDocument doc,
                                                         StructureObject start) {
        List<StructureObject> found = new ArrayList<>();
        found.add(start);
        for (int i = 0; i < found.size(); i++) {
            StructureObject a = found.get(i);
            for (StructureObject s : doc.structures) {
                if (s.locked || found.contains(s) || !StructureObject.ROLE_WALL
                        .equals(StructureRoles.roleOf(s))) continue;
                // Paredes sobrepostas de andares distintos não pertencem ao
                // mesmo cômodo, embora coincidam na planta X/Z.
                if (Math.abs(StoryLevels.bottom(a)
                        - StoryLevels.bottom(s)) > 0.08f) continue;
                if (Math.abs(a.transform.x - s.transform.x)
                        <= a.half[0] + s.half[0] + 0.05f
                        && Math.abs(a.transform.z - s.transform.z)
                        <= a.half[2] + s.half[2] + 0.05f) {
                    found.add(s);
                }
            }
        }
        return found;
    }
}
