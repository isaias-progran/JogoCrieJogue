package br.com.termia.construajogue;

import br.com.termia.construajogue.editor.tools.GroupSelection;
import br.com.termia.construajogue.editor.tools.OpeningTool;
import br.com.termia.construajogue.editor.tools.PaintTool;
import br.com.termia.construajogue.editor.tools.PrefabPlacementTool;
import br.com.termia.construajogue.editor.tools.StoryLevels;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.prefab.PrefabDefinition;

import java.util.List;

/** Regressões do editor de vários pavimentos. */
public final class StoryLevelsTest {
    private static int checks;

    public static void main(String[] args) {
        MapDocument doc = new MapDocument();
        StructureObject floor = block("piso", StructureObject.ROLE_FLOOR,
                -0.15f, 0.15f);
        StructureObject groundWall = block("parede-0",
                StructureObject.ROLE_WALL, 1.5f, 1.5f);
        StructureObject ceiling = block("teto",
                StructureObject.ROLE_CEILING, 3.15f, 0.15f);
        StructureObject upperWall = block("parede-1",
                StructureObject.ROLE_WALL, 4.8f, 1.5f);
        doc.structures.add(floor);
        doc.structures.add(groundWall);
        doc.structures.add(ceiling);
        doc.structures.add(upperWall);

        LogicMarker groundMarker = marker("inicio", 0f);
        LogicMarker upperMarker = marker("saida", 3.3f);
        doc.markers.add(groundMarker);
        doc.markers.add(upperMarker);

        PrefabDefinition crate = new PrefabDefinition();
        crate.id = "prop.crate";
        crate.behavior = PrefabDefinition.BEHAVIOR_STATIC;
        PrefabInstance upperCrate = PrefabPlacementTool.create(
                crate, 0f, 0f, 3.3f);
        doc.prefabs.add(upperCrate);

        List<Float> levels = StoryLevels.discover(doc);
        check(levels.size() == 2, "descobre térreo e primeiro andar");
        check(near(levels.get(0), 0f) && near(levels.get(1), 3.3f),
                "elevações corretas");
        check(StoryLevels.belongs(ceiling, 0f)
                        && StoryLevels.belongs(ceiling, 3.3f),
                "laje compartilhada aparece nos dois lados");
        check(!StoryLevels.belongs(upperWall, 0f)
                        && StoryLevels.belongs(upperWall, 3.3f),
                "parede pertence só ao andar superior");
        check(StoryLevels.belongs(upperCrate, 3.3f)
                        && !StoryLevels.belongs(upperCrate, 0f),
                "peça recebe elevação do andar");
        check(near(StoryLevels.baseOf(ceiling), 0f),
                "lista abre teto no pavimento que ele cobre");
        StructureObject tallCeiling = block("teto-alto",
                StructureObject.ROLE_CEILING, 4.15f, 0.15f);
        List<Float> tallLevels = new java.util.ArrayList<>();
        tallLevels.add(0f);
        tallLevels.add(4.3f);
        check(StoryLevels.belongs(tallCeiling, 0f, tallLevels),
                "teto personalizado continua no pavimento de origem");
        StructureObject legacyStep = block("degrau-legado", null,
                0.15f, 0.15f);
        check(StoryLevels.belongs(legacyStep, 0f, levels),
                "degrau legado continua visível no térreo");
        check(near(StoryLevels.baseOf(legacyStep, levels), 0f),
                "lista não transforma degrau legado em novo andar");

        GroupSelection group = new GroupSelection();
        group.select(doc, -3f, -3f, 3f, 3f, 0f);
        check(group.size() == 4,
                "seleção térrea não captura objetos de cima");
        group.select(doc, -3f, -3f, 3f, 3f, 3.3f);
        check(group.size() == 4,
                "seleção superior não captura objetos de baixo");

        check(OpeningTool.wallAt(doc, 0f, 0f, 0.4f, 0f) == groundWall,
                "vão encontra parede térrea sobreposta");
        check(OpeningTool.wallAt(doc, 0f, 0f, 0.4f, 3.3f) == upperWall,
                "vão encontra parede superior sobreposta");

        PaintTool.apply(doc, groundWall, 0f, 1f,
                new float[]{1f, 0f, 0f}, true);
        check(groundWall.color2 != null || groundWall.color3 != null,
                "balde pinta o andar alvo");
        check(upperWall.color2 == null && upperWall.color3 == null,
                "balde não atravessa a laje");

        PrefabDefinition lamp = new PrefabDefinition();
        lamp.id = "prop.lamp.ceiling";
        lamp.behavior = PrefabDefinition.BEHAVIOR_STATIC;
        PrefabInstance upperLamp = PrefabPlacementTool.create(
                lamp, 0f, 0f, 3.3f);
        check(near(upperLamp.transform.y, 6.3f)
                        && StoryLevels.belongs(upperLamp, 3.3f),
                "luminária fica no teto do andar ativo");

        PrefabDefinition terminalDef = new PrefabDefinition();
        terminalDef.id = "terminal.wall";
        terminalDef.behavior = PrefabDefinition.BEHAVIOR_TERMINAL;
        PrefabDefinition doorDef = new PrefabDefinition();
        doorDef.id = "door.gate";
        doorDef.behavior = PrefabDefinition.BEHAVIOR_DOOR;
        PrefabInstance lowerTerminal = PrefabPlacementTool.create(
                terminalDef, 0f, 0f, 0f);
        PrefabInstance upperTerminal = PrefabPlacementTool.create(
                terminalDef, 4f, 0f, 3.3f);
        PrefabInstance upperDoor = PrefabPlacementTool.create(
                doorDef, 0f, 0f, 3.3f);
        doc.prefabs.add(lowerTerminal);
        doc.prefabs.add(upperTerminal);
        doc.prefabs.add(upperDoor);
        PrefabPlacementTool.autoLinkDoors(doc);
        check(upperTerminal.id.equals(
                        upperDoor.stringProperty("controllerId")),
                "porta não se liga ao terminal do andar de baixo");

        System.out.println("OK StoryLevelsTest: " + checks
                + " verificações");
    }

    private static StructureObject block(String id, String role,
                                         float y, float halfY) {
        StructureObject value = new StructureObject(id,
                StructureObject.KIND_BLOCK);
        value.role = role;
        value.transform.y = y;
        value.half = new float[]{2f, halfY, 2f};
        value.color = new float[]{0.4f, 0.4f, 0.4f};
        return value;
    }

    private static LogicMarker marker(String id, float y) {
        LogicMarker value = new LogicMarker(id,
                y == 0f ? LogicMarker.PLAYER_SPAWN : LogicMarker.EXIT);
        value.y = y;
        value.radius = 1.2f;
        return value;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
