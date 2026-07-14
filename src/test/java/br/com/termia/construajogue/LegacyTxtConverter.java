package br.com.termia.construajogue;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.persistence.MapJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Ferramenta de desenvolvimento (não vai no APK): converte um nível
 * legado .txt em MapDocument JSON schema 1. Lê os floats direto do texto
 * para o documento reproduzir o nível bit a bit após compilar. IDs
 * determinísticos para diffs estáveis no git.
 */
public final class LegacyTxtConverter {

    private LegacyTxtConverter() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println(
                    "uso: LegacyTxtConverter <in.txt> <out.json> <nome>");
            System.exit(2);
        }
        List<String> lines = Files.readAllLines(Paths.get(args[0]),
                StandardCharsets.UTF_8);
        MapDocument doc = convert(lines, args[2]);
        Files.write(Paths.get(args[1]),
                MapJson.write(doc).getBytes(StandardCharsets.UTF_8));
        System.out.println(args[1] + ": " + doc.structures.size()
                + " estruturas, " + doc.prefabs.size() + " peças, "
                + doc.markers.size() + " marcadores");
    }

    public static MapDocument convert(List<String> lines, String name) {
        MapDocument doc = new MapDocument();
        doc.id = "map-" + name;
        doc.name = name;
        int blocks = 0;
        int drones = 0;
        int waves = 0;
        int mutants = 0;
        int items = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "box": {
                    StructureObject block = new StructureObject(
                            String.format("block-%03d", ++blocks),
                            StructureObject.KIND_BLOCK);
                    block.transform.x = f(p[1]);
                    block.transform.y = f(p[2]);
                    block.transform.z = f(p[3]);
                    block.half = new float[]{f(p[4]), f(p[5]), f(p[6])};
                    block.color = new float[]{f(p[7]), f(p[8]), f(p[9])};
                    doc.structures.add(block);
                    break;
                }
                case "drone":
                    doc.prefabs.add(enemy("drone-" + ++drones,
                            "enemy.drone", p));
                    break;
                case "wave":
                    doc.prefabs.add(enemy("wave-" + ++waves,
                            "enemy.drone.wave", p));
                    break;
                case "mutant":
                    doc.prefabs.add(enemy("mutant-" + ++mutants,
                            "enemy.mutant", p));
                    break;
                case "item": {
                    PrefabInstance item = new PrefabInstance(
                            "item-" + ++items, "health".equals(p[1])
                            ? "pickup.health" : "pickup.ammo");
                    item.transform.x = f(p[2]);
                    item.transform.y = f(p[3]);
                    item.transform.z = f(p[4]);
                    doc.prefabs.add(item);
                    break;
                }
                case "terminal": {
                    PrefabInstance terminal = new PrefabInstance(
                            "terminal", "terminal.wall");
                    terminal.transform.x = f(p[1]);
                    terminal.transform.y = f(p[2]);
                    terminal.transform.z = f(p[3]);
                    doc.prefabs.add(terminal);
                    break;
                }
                case "door": {
                    PrefabInstance door = new PrefabInstance(
                            "door", "door.gate");
                    door.transform.x = f(p[1]);
                    door.transform.y = f(p[2]);
                    door.transform.z = f(p[3]);
                    door.properties.put("halfX", f(p[4]));
                    door.properties.put("halfY", f(p[5]));
                    door.properties.put("halfZ", f(p[6]));
                    door.properties.put("controllerId", "terminal");
                    doc.prefabs.add(door);
                    break;
                }
                case "spawn": {
                    LogicMarker spawn = new LogicMarker("spawn",
                            LogicMarker.PLAYER_SPAWN);
                    spawn.x = f(p[1]);
                    spawn.y = f(p[2]);
                    spawn.z = f(p[3]);
                    spawn.yaw = f(p[4]);
                    doc.markers.add(spawn);
                    break;
                }
                case "exit": {
                    LogicMarker exit = new LogicMarker("exit",
                            LogicMarker.EXIT);
                    exit.x = f(p[1]);
                    exit.z = f(p[2]);
                    exit.radius = f(p[3]);
                    doc.markers.add(exit);
                    break;
                }
                case "ambient":
                    doc.ambient = f(p[1]);
                    break;
                case "fog":
                    doc.fogColor = new float[]{f(p[1]), f(p[2]), f(p[3])};
                    doc.fogFar = f(p[4]);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "comando desconhecido: " + line);
            }
        }
        return doc;
    }

    private static PrefabInstance enemy(String id, String prefabId,
                                        String[] p) {
        PrefabInstance instance = new PrefabInstance(id, prefabId);
        instance.transform.x = f(p[1]);
        instance.transform.y = f(p[2]);
        instance.transform.z = f(p[3]);
        instance.properties.put("patrolX", f(p[4]));
        instance.properties.put("patrolZ", f(p[5]));
        return instance;
    }

    private static float f(String token) {
        return Float.parseFloat(token);
    }
}
