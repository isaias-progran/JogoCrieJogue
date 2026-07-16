package br.com.termia.construajogue;

import br.com.termia.construajogue.editor.tools.GroupSelection;
import br.com.termia.construajogue.editor.tools.OpeningTool;
import br.com.termia.construajogue.editor.tools.PaintTool;
import br.com.termia.construajogue.editor.tools.PrefabPlacementTool;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabDefinition;

public final class EditorToolsTest {
    private static int checks;

    public static void main(String[] args) {
        MapDocument doc = new MapDocument();
        StructureObject diagonal = diagonalWall();
        doc.structures.add(diagonal);
        WallOpening opening = OpeningTool.create(WallOpening.DOOR,
                diagonal, 1.5f, 1.5f);
        diagonal.openings.add(opening);
        check(Math.abs(opening.offset) < 0.01f, "vão no centro diagonal");
        OpeningTool.move(opening, diagonal, 2.75f, 2.75f, true);
        check(opening.offset > 1.2f, "vão desliza na diagonal");

        PaintTool.paintWallSide(diagonal, 0f, 0.5f,
                new float[]{1f, 0f, 0f}, false);
        check(diagonal.color2 != null || diagonal.color3 != null,
                "lado diagonal pintado");

        PrefabDefinition terminalDef = new PrefabDefinition();
        terminalDef.id = "terminal.wall";
        terminalDef.behavior = PrefabDefinition.BEHAVIOR_TERMINAL;
        PrefabDefinition doorDef = new PrefabDefinition();
        doorDef.id = "door.gate";
        doorDef.behavior = PrefabDefinition.BEHAVIOR_DOOR;
        PrefabInstance terminal = PrefabPlacementTool.create(terminalDef,
                0f, 0f);
        PrefabInstance door = PrefabPlacementTool.create(doorDef, 2f, 0f);
        PrefabDefinition streetDef = new PrefabDefinition();
        streetDef.id = "prop.lamp.street";
        streetDef.behavior = PrefabDefinition.BEHAVIOR_STATIC;
        PrefabInstance street = PrefabPlacementTool.create(
                streetDef, 4f, 0f);
        check(Math.abs(street.floatProperty("lightOffsetY", 0f) - 3.35f)
                        < 0.001f
                        && Math.abs(street.floatProperty(
                        "lightRadius", 0f) - 8f) < 0.001f,
                "poste nasce com luz na cabeça");
        doc.prefabs.add(terminal);
        doc.prefabs.add(door);
        PrefabPlacementTool.autoLinkDoors(doc);
        check(terminal.id.equals(door.stringProperty("controllerId")),
                "porta ligada ao terminal");

        GroupSelection group = new GroupSelection();
        group.select(doc, -2f, -2f, 3f, 3f);
        check(group.size() == 3, "seleção inclui parede e peças");
        group.translate(1f, -1f);
        check(Math.abs(door.transform.x - 3f) < 0.01f,
                "grupo move peça");
        int copies = group.duplicateInto(doc, 0.5f, 0.5f);
        check(copies == 3, "grupo duplicado");
        PrefabInstance copiedDoor = doc.prefabs.get(doc.prefabs.size() - 1);
        PrefabInstance copiedTerminal = doc.prefabs.get(doc.prefabs.size() - 2);
        check(copiedTerminal.id.equals(
                copiedDoor.stringProperty("controllerId")),
                "cópia preserva ligação interna");
        check(WallGeometry.halfLength(diagonal) > 2f,
                "comprimento diagonal");
        WallGeometry.resize(diagonal, 8f, 0.5f);
        check(Math.abs(WallGeometry.halfLength(diagonal) - 4f) < 0.001f,
                "medida altera comprimento diagonal");
        check(Math.abs(WallGeometry.thickness(diagonal) - 0.5f) < 0.001f,
                "medida altera espessura diagonal");
        check(Math.abs(opening.offset) + opening.width * 0.5f
                        <= WallGeometry.halfLength(diagonal) + 0.001f,
                "vão continua dentro da parede redimensionada");
        System.out.println("OK EditorToolsTest: " + checks + " verificações");
    }

    private static StructureObject diagonalWall() {
        StructureObject wall = new StructureObject("diag", "poly");
        wall.role = StructureObject.ROLE_WALL;
        wall.polygon = new float[]{-0.1f, 0.1f, 2.9f, 3.1f,
                3.1f, 2.9f, 0.1f, -0.1f};
        wall.transform.y = 1.5f;
        wall.half = new float[]{0f, 1.5f, 0f};
        wall.color = new float[]{0.4f, 0.4f, 0.4f};
        wall.syncPolyBounds();
        return wall;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
