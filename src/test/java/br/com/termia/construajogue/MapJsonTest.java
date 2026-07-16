package br.com.termia.construajogue;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;

public final class MapJsonTest {

    public static void main(String[] args) {
        MapDocument doc = new MapDocument();
        doc.id = "map-teste";
        doc.name = "Sala de teste";
        doc.ambient = 0.4f;
        doc.fogColor = new float[]{0.1f, 0.2f, 0.3f};
        doc.fogFar = 25f;
        doc.sky = "night";
        doc.soundscape = "tunnel";
        doc.objective.type = ObjectiveSpec.COLLECT;
        doc.objective.target = 4;
        doc.objective.timeLimitSeconds = 90f;
        doc.objective.twoStarSeconds = 70f;
        doc.objective.threeStarSeconds = 45f;

        StructureObject block = new StructureObject("b1",
                StructureObject.KIND_BLOCK);
        block.transform.x = 1.5f;
        block.transform.y = 0.25f;
        block.transform.z = -2f;
        block.half = new float[]{0.5f, 0.25f, 3f};
        block.color = new float[]{0.7f, 0.6f, 0.5f};
        block.color2 = new float[]{0.2f, 0.3f, 0.4f};
        block.color3 = new float[]{0.6f, 0.1f, 0.2f};
        block.material = "brick";
        block.locked = true;
        doc.structures.add(block);

        PrefabInstance drone = new PrefabInstance("d1", "enemy.drone");
        drone.transform.x = 2f;
        drone.transform.y = 1.7f;
        drone.properties.put("patrolX", -2f);
        drone.properties.put("patrolZ", 0.5f);
        drone.locked = true;
        doc.prefabs.add(drone);

        PrefabInstance door = new PrefabInstance("porta", "door.gate");
        door.properties.put("controllerId", "term");
        door.properties.put("halfX", 1.2f);
        doc.prefabs.add(door);

        LogicMarker spawn = new LogicMarker("spawn",
                LogicMarker.PLAYER_SPAWN);
        spawn.z = 6f;
        spawn.yaw = 180f;
        doc.markers.add(spawn);
        LogicMarker exit = new LogicMarker("exit", LogicMarker.EXIT);
        exit.x = -3f;
        exit.radius = 1.2f;
        doc.markers.add(exit);

        String written = MapJson.write(doc);
        MapDocument back = MapJson.read(written);

        Check.equal(back.id, doc.id, "id");
        Check.equal(back.name, doc.name, "nome");
        Check.that(back.ambient == doc.ambient, "ambiente");
        Check.sameFloats(back.fogColor, doc.fogColor, "neblina");
        Check.that(back.fogFar == doc.fogFar, "distância da neblina");
        Check.equal(back.sky, "night", "céu");
        Check.equal(back.soundscape, "tunnel", "paisagem sonora");
        Check.equal(back.objective.type, ObjectiveSpec.COLLECT, "objetivo");
        Check.equal(back.objective.target, 4, "alvo do objetivo");
        Check.that(back.objective.timeLimitSeconds == 90f,
                "limite de tempo");

        Check.equal(back.structures.size(), 1, "estruturas");
        StructureObject b = back.structures.get(0);
        Check.equal(b.kind, StructureObject.KIND_BLOCK, "tipo");
        Check.that(b.transform.x == 1.5f && b.transform.y == 0.25f
                && b.transform.z == -2f, "transform do bloco");
        Check.sameFloats(b.half, block.half, "meias dimensões");
        Check.sameFloats(b.color, block.color, "cor");
        Check.sameFloats(b.color2, block.color2, "cor do lado +");
        Check.sameFloats(b.color3, block.color3, "cor do lado -");
        Check.equal(b.material, "brick", "material");
        Check.that(b.locked, "trava da estrutura");

        Check.equal(back.prefabs.size(), 2, "peças");
        PrefabInstance d = back.prefabs.get(0);
        Check.equal(d.prefabId, "enemy.drone", "prefabId");
        Check.that(d.floatProperty("patrolX", 9f) == -2f, "patrolX");
        Check.that(d.floatProperty("patrolZ", 9f) == 0.5f, "patrolZ");
        Check.that(d.locked, "trava da peça");
        PrefabInstance p = back.prefabs.get(1);
        Check.equal(p.stringProperty("controllerId"), "term",
                "controllerId");
        Check.that(p.floatProperty("halfX", 0f) == 1.2f, "halfX");

        Check.equal(back.markers.size(), 2, "marcadores");
        Check.that(back.markers.get(0).yaw == 180f, "yaw do início");
        Check.that(back.markers.get(1).radius == 1.2f, "raio da saída");
        Check.equal(back.findInstance("porta").prefabId, "door.gate",
                "findInstance");
        Check.equal(back.firstMarker(LogicMarker.EXIT).id, "exit",
                "firstMarker");

        // reescrever o que foi lido dá o mesmo arquivo (estável p/ git)
        Check.equal(MapJson.write(back), written, "escrita estável");

        MapDocument migrated = MapJson.read("{\"schema\":1,"
                + "\"id\":\"old\",\"structures\":[],"
                + "\"prefabs\":[],\"markers\":[]}");
        Check.equal(migrated.objective.type, ObjectiveSpec.REACH_EXIT,
                "schema 1 migra com objetivo clássico");
        Check.that(MapJson.write(migrated).contains("\"schema\": 2"),
                "migração escreve schema atual");

        Check.fails(() -> MapJson.read("{\"schema\": 99}"),
                "schema desconhecido");
        Check.fails(() -> MapJson.read("{\"schema\":2,\"id\":\"x\","
                        + "\"structures\":[{\"id\":\"p\",\"kind\":\"poly\","
                        + "\"polygon\":[0,0,1,0,1,1,0]}],"
                        + "\"prefabs\":[],\"markers\":[]}"),
                "contorno com par incompleto");
        Check.done("MapJsonTest");
    }
}
