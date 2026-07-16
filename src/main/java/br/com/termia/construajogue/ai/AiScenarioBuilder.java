package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Converte o plano seguro da IA em operações normais do editor. */
public final class AiScenarioBuilder {

    private static final float[] CONCRETE = {0.46f, 0.49f, 0.54f};
    private static final float[] DARK = {0.23f, 0.26f, 0.31f};
    private static final float[] LIGHT = {0.68f, 0.70f, 0.73f};

    private AiScenarioBuilder() {
    }

    /** Compatibilidade: um plano vira um único mapa editável. */
    public static MapDocument build(AiScenarioPlan plan) {
        return buildSeries(plan, AiScenarioProfile.single(plan.size)).get(0);
    }

    /**
     * Cenários enormes viram setores. Só o último conserva o objetivo pedido;
     * os anteriores terminam ao cruzar a porta de ligação.
     */
    public static List<MapDocument> buildSeries(AiScenarioPlan plan,
                                                AiScenarioProfile profile) {
        if (plan == null) throw new IllegalArgumentException("plano vazio");
        if (profile == null) throw new IllegalArgumentException("perfil vazio");
        List<MapDocument> documents = new ArrayList<>();
        for (int sector = 0; sector < profile.sectorCount(); sector++) {
            documents.add(buildSector(plan, profile, sector,
                    profile.sectorCount()));
        }
        return documents;
    }

    private static MapDocument buildSector(AiScenarioPlan plan,
                                           AiScenarioProfile profile,
                                           int sector, int totalSectors) {
        long mixedSeed = ((long) plan.seed * 0x9E3779B9L)
                ^ ((long) (sector + 1) * 0x632BE5ABL);
        Random random = new Random(mixedSeed);
        MapDocument doc = new MapDocument();
        doc.id = Ids.create();
        doc.name = totalSectors > 1
                ? plan.title + " — setor " + (sector + 1) + "/" + totalSectors
                : plan.title;
        configureEnvironment(doc, plan, profile);

        float half = profile.halfSize();
        block(doc, StructureObject.ROLE_FLOOR, groundMaterial(plan),
                0f, -0.15f, 0f, half, 0.15f, half, groundColor(plan));
        perimeter(doc, half, wallMaterial(plan), wallColor(plan));

        buildTopology(doc, plan, profile, random, sector);

        if (plan.hasFeature("terminal_gate")) {
            terminalGate(doc, isFocalLayout(plan)
                    ? Math.min(half, houseHalfX(plan, profile) - 0.3f)
                    : half);
        }
        if (plan.hasFeature("streetlights")
                && !isUnderground(plan)) {
            streetLights(doc, half, profile.rows() * 2);
        }
        // Em campanha os perigos alternam entre setores em vez de clonar.
        if (plan.hasFeature("water")
                && (totalSectors == 1 || sector % 2 == 0)) {
            hazard(doc, "water", isUnderground(plan)
                    ? -2.25f : -half + 3.2f, -half * 0.25f);
        }
        if (plan.hasFeature("lava")
                && (totalSectors == 1 || sector % 2 == 1)) {
            hazard(doc, "lava", isUnderground(plan)
                    ? 2.25f : half - 3.2f, -half * 0.35f);
        }
        if (plan.hasFeature("second_floor")
                && !isUnderground(plan) && !isFocalLayout(plan)) {
            observationDeck(doc, half, plan);
        }

        String objective = sector + 1 < totalSectors
                ? ObjectiveSpec.REACH_EXIT : plan.objective;
        addSpawnAndObjective(doc, plan, profile, objective);
        if (ObjectiveSpec.REACH_EXIT.equals(objective)
                && !isVerticalMap(plan)) {
            sectorPortal(doc, half);
        }
        if (sector == 0 || totalSectors == 1) {
            addHuman(doc, plan, profile);
        }
        addEnemies(doc, plan, profile, random);
        addPickups(doc, plan, profile);
        return doc;
    }

    private static void configureEnvironment(MapDocument doc,
                                             AiScenarioPlan plan,
                                             AiScenarioProfile profile) {
        if (isUnderground(plan)) {
            doc.sky = "none";
            doc.soundscape = "tunnel";
            doc.ambient = 0.13f;
            doc.fogColor = new float[]{0.025f, 0.035f, 0.045f};
            doc.fogFar = Math.min(64f, profile.halfSize() * 1.45f);
            return;
        }
        doc.sky = plan.sky;
        doc.soundscape = ("industrial".equals(plan.setting)
                || "laboratory".equals(plan.setting))
                ? "industrial" : "outdoor";
        if ("day".equals(plan.sky)) {
            doc.ambient = 0.48f;
            doc.fogColor = new float[]{0.46f, 0.62f, 0.76f};
        } else if ("dusk".equals(plan.sky)) {
            doc.ambient = 0.25f;
            doc.fogColor = new float[]{0.18f, 0.16f, 0.25f};
        } else {
            doc.ambient = 0.16f;
            doc.fogColor = new float[]{0.035f, 0.05f, 0.085f};
        }
        doc.fogFar = "huge".equals(profile.sectorSize()) ? 105f
                : "large".equals(profile.sectorSize()) ? 78f
                : "medium".equals(profile.sectorSize()) ? 56f : 42f;
    }

    private static String groundMaterial(AiScenarioPlan plan) {
        if (isUnderground(plan)) return "metal";
        if ("city".equals(plan.setting)) return "asphalt";
        if ("laboratory".equals(plan.setting)) return "checker";
        if ("industrial".equals(plan.setting)
                || "tunnel".equals(plan.setting)) return "metal";
        return "plain";
    }

    private static float[] groundColor(AiScenarioPlan plan) {
        if (isUnderground(plan)) {
            return new float[]{0.20f, 0.23f, 0.25f};
        }
        if ("city".equals(plan.setting)) {
            return new float[]{0.20f, 0.22f, 0.25f};
        }
        if ("laboratory".equals(plan.setting)) {
            return new float[]{0.42f, 0.46f, 0.52f};
        }
        if ("tunnel".equals(plan.setting)) {
            return new float[]{0.20f, 0.23f, 0.25f};
        }
        return new float[]{0.31f, 0.34f, 0.38f};
    }

    private static String wallMaterial(AiScenarioPlan plan) {
        if (isUnderground(plan)) return "metal";
        if ("industrial".equals(plan.setting)
                || "laboratory".equals(plan.setting)
                || "tunnel".equals(plan.setting)) return "metal";
        if ("city".equals(plan.setting)
                || "ruins".equals(plan.setting)) return "brick";
        return "plain";
    }

    private static float[] wallColor(AiScenarioPlan plan) {
        if (isUnderground(plan)) {
            return new float[]{0.32f, 0.35f, 0.38f};
        }
        if ("laboratory".equals(plan.setting)) return LIGHT;
        if ("fortress".equals(plan.setting)) {
            return new float[]{0.36f, 0.38f, 0.42f};
        }
        if ("ruins".equals(plan.setting)) {
            return new float[]{0.45f, 0.38f, 0.31f};
        }
        if ("tunnel".equals(plan.setting)) {
            return new float[]{0.32f, 0.35f, 0.38f};
        }
        return CONCRETE;
    }

    private static void perimeter(MapDocument doc, float half,
                                  String material, float[] color) {
        wall(doc, 0f, 1.5f, -half, half, 0.15f, material, color, false);
        wall(doc, 0f, 1.5f, half, half, 0.15f, material, color, false);
        wall(doc, -half, 1.5f, 0f, 0.15f, half, material, color, false);
        wall(doc, half, 1.5f, 0f, 0.15f, half, material, color, false);
    }

    /** A implantação vem do plano; o tema passa a definir só acabamento. */
    private static void buildTopology(MapDocument doc, AiScenarioPlan plan,
                                      AiScenarioProfile profile,
                                      Random random, int sector) {
        AiScenarioPlan.Zone zone = plan.zoneAt(sector);
        if (isUnderground(plan) || "tunnel".equals(zone.kind)) {
            buildTunnel(doc, plan, profile, random);
            return;
        }
        String layout = layoutForZone(plan.layout, zone);
        if (sector >= plan.zones.size()) {
            layout = rotatedLayout(layout, sector / plan.zones.size());
        }
        if ("single_building".equals(layout)
                || "vertical".equals(layout)) {
            buildFocalBuilding(doc, plan, profile, zone);
        } else if ("courtyard".equals(layout)) {
            buildCourtyard(doc, plan, profile, random);
        } else if ("campus".equals(layout)
                || "scattered".equals(layout)) {
            buildCampus(doc, plan, profile, random,
                    "scattered".equals(layout));
        } else if ("maze".equals(layout)) {
            buildMaze(doc, plan, profile, random);
        } else if ("hub".equals(layout)) {
            buildHub(doc, plan, profile, random);
        } else if ("linear".equals(layout)) {
            buildLinear(doc, plan, profile, random);
        } else {
            buildThemedStreet(doc, plan, profile, random);
        }
        if (!isFocalLayout(plan)) {
            routeAccent(doc, plan, profile.halfSize());
        }
        if (plan.hasFeature("diagonal_walls")) {
            diagonalLandmarks(doc, plan, profile.halfSize());
        }
        if (plan.hasFeature("bridge")) {
            bridge(doc, plan, profile.halfSize());
        }
    }

    private static String layoutForZone(String requested,
                                        AiScenarioPlan.Zone zone) {
        if ("tunnel".equals(zone.kind)) return "underground";
        return requested;
    }

    /**
     * Setor além das zones descritas pela IA não repete a planta: gira
     * entre as plantas abertas para a campanha ter cenários distintos.
     */
    private static String rotatedLayout(String layout, int wrap) {
        String[] open = {"street", "courtyard", "campus", "hub",
                "maze", "linear"};
        for (int i = 0; i < open.length; i++) {
            if (open[i].equals(layout)) {
                return open[(i + wrap) % open.length];
            }
        }
        return open[Math.floorMod(wrap - 1, open.length)];
    }

    private static void buildThemedStreet(MapDocument doc,
                                          AiScenarioPlan plan,
                                          AiScenarioProfile profile,
                                          Random random) {
        if ("industrial".equals(plan.setting)) {
            buildIndustrial(doc, plan, profile, random);
        } else if ("laboratory".equals(plan.setting)) {
            buildLaboratory(doc, plan, profile, random);
        } else if ("fortress".equals(plan.setting)) {
            buildFortress(doc, plan, profile, random);
        } else if ("ruins".equals(plan.setting)) {
            buildRuins(doc, plan, profile, random);
        } else {
            buildCity(doc, plan, profile, random);
        }
    }

    /** Casa/prédio com 1–3 pavimentos, laje vazada e circulação vertical. */
    private static void buildFocalBuilding(MapDocument doc,
                                           AiScenarioPlan plan,
                                           AiScenarioProfile profile,
                                           AiScenarioPlan.Zone zone) {
        float hx = houseHalfX(plan, profile);
        float hz = houseHalfZ(plan, profile);
        int floors = effectiveFloors(plan);
        String material = buildingMaterial(plan, zone);
        float[] color = buildingColor(plan, zone);
        String floorMaterial = buildingFloorMaterial(zone);
        float[] floorColor = "wood".equals(floorMaterial)
                ? new float[]{0.48f, 0.35f, 0.24f}
                : "checker".equals(floorMaterial)
                ? new float[]{0.48f, 0.52f, 0.58f}
                : new float[]{0.31f, 0.35f, 0.40f};
        String accessId = plan.hasFeature("ramps")
                && !plan.hasFeature("stairs") ? "ramp.floor" : "stairs.floor";
        float accessLength = "ramp.floor".equals(accessId) ? 6f : 3.6f;

        block(doc, StructureObject.ROLE_FLOOR, floorMaterial,
                0f, 0.025f, 0f, hx - 0.18f, 0.025f, hz - 0.18f,
                floorColor);
        for (int level = 0; level < floors; level++) {
            float baseY = level * 3.3f;
            if (level > 0) {
                upperFloor(doc, floorMaterial, floorColor, baseY,
                        hx, hz, accessLength);
            }
            addStoryShell(doc, plan, material, color, baseY, hx, hz,
                    level);
            addStoryPartitions(doc, plan, material, color, baseY, hx, hz,
                    level);
            if (level + 1 < floors) {
                PrefabInstance access = prefab(doc, accessId,
                        0f, baseY, 1f);
                access.transform.yaw = 180f;
            }
            if (plan.hasFeature("furniture")) {
                furnishStory(doc, zone.kind, level, baseY, hx, hz);
            }
            if (plan.hasFeature("indoor_lights")) {
                indoorLights(doc, level, baseY, hx, hz);
            }
        }
        addBuildingRoof(doc, plan, material, color, floorMaterial,
                floorColor, floors, hx, hz, accessId, accessLength);
    }

    private static void addStoryShell(MapDocument doc, AiScenarioPlan plan,
                                      String material, float[] color,
                                      float baseY, float hx, float hz,
                                      int level) {
        float y = baseY + 1.5f;
        StructureObject front = wall(doc, 0f, y, hz, hx, 0.15f,
                material, color, false);
        StructureObject back = wall(doc, 0f, y, -hz, hx, 0.15f,
                material, color, false);
        StructureObject left = wall(doc, -hx, y, 0f, 0.15f, hz,
                material, color, false);
        StructureObject right = wall(doc, hx, y, 0f, 0.15f, hz,
                material, color, false);
        if (level == 0) {
            front.openings.add(opening(0f, 1.5f, 2.2f));
            if (plan.hasFeature("automatic_doors")) {
                PrefabInstance door = prefab(doc, "door.auto",
                        0f, baseY + 1.05f, hz);
                door.properties.put("halfX", 0.72f);
                door.properties.put("halfY", 1.05f);
                door.properties.put("halfZ", 0.08f);
            }
        }
        if (plan.hasFeature("windows")) {
            float frontOffset = hx * 0.56f;
            front.openings.add(windowOpening(-frontOffset, 1.2f));
            front.openings.add(windowOpening(frontOffset, 1.2f));
            float backOffset = hx * 0.43f;
            back.openings.add(windowOpening(-backOffset, 1.35f));
            back.openings.add(windowOpening(backOffset, 1.35f));
            float sideOffset = hz * 0.42f;
            left.openings.add(windowOpening(-sideOffset, 1.25f));
            left.openings.add(windowOpening(sideOffset, 1.25f));
            right.openings.add(windowOpening(-sideOffset, 1.25f));
            right.openings.add(windowOpening(sideOffset, 1.25f));
        }
    }

    private static void addStoryPartitions(MapDocument doc,
                                           AiScenarioPlan plan,
                                           String material, float[] color,
                                           float baseY, float hx, float hz,
                                           int level) {
        String pattern = plan.roomPattern;
        if ("mixed".equals(pattern)) {
            pattern = (level & 1) == 0 ? "corridor_rooms" : "central_hall";
        }
        float y = baseY + 1.5f;
        if ("corridor_rooms".equals(pattern)) {
            for (int side = -1; side <= 1; side += 2) {
                StructureObject partition = wall(doc, side * 1.6f, y, 0f,
                        0.12f, hz - 0.32f, material, color, false);
                float offset = hz * 0.38f;
                partition.openings.add(opening(-offset, 1.15f, 2.15f));
                partition.openings.add(opening(offset, 1.15f, 2.15f));
            }
        } else if ("central_hall".equals(pattern)) {
            float offset = Math.min(hz * 0.34f, 3f);
            for (int side = -1; side <= 1; side += 2) {
                StructureObject partition = wall(doc, 0f, y, side * offset,
                        hx - 0.32f, 0.12f, material, color, true);
                partition.openings.add(opening(0f, 1.55f, 2.15f));
            }
        } else if ("split_rooms".equals(pattern)) {
            StructureObject partition = wall(doc, 0f, y, -hz * 0.28f,
                    hx - 0.32f, 0.12f, material, color, true);
            partition.openings.add(opening(hx * 0.34f, 1.35f, 2.15f));
        } else if (!"open_plan".equals(pattern)) {
            StructureObject partition = wall(doc, -2f, y, 0f,
                    0.12f, hz - 0.32f, material, color, false);
            partition.openings.add(opening(0f, 1.3f, 2.15f));
        }
    }

    private static void upperFloor(MapDocument doc, String material,
                                   float[] color, float baseY,
                                   float hx, float hz, float accessLength) {
        float openingHalfX = 0.78f;
        float sideHalfX = (hx - openingHalfX) * 0.5f;
        float sideX = (hx + openingHalfX) * 0.5f;
        float slabY = baseY - 0.15f;
        block(doc, StructureObject.ROLE_FLOOR, material,
                -sideX, slabY, 0f, sideHalfX, 0.15f, hz - 0.18f, color);
        block(doc, StructureObject.ROLE_FLOOR, material,
                sideX, slabY, 0f, sideHalfX, 0.15f, hz - 0.18f, color);
        float highEdge = 1f - accessLength * 0.5f;
        float rearHalfZ = (highEdge + hz - 0.18f) * 0.5f;
        float rearZ = -hz + 0.18f + rearHalfZ;
        block(doc, StructureObject.ROLE_FLOOR, material,
                0f, slabY, rearZ, openingHalfX, 0.15f, rearHalfZ, color);
    }

    private static void addBuildingRoof(MapDocument doc,
                                        AiScenarioPlan plan,
                                        String wallMaterial, float[] wallColor,
                                        String floorMaterial,
                                        float[] floorColor, int floors,
                                        float hx, float hz, String accessId,
                                        float accessLength) {
        float topBase = (floors - 1) * 3.3f;
        float roofY = topBase + 3.15f;
        if (plan.hasFeature("rooftop")) {
            upperFloor(doc, floorMaterial, floorColor, floors * 3.3f,
                    hx, hz, accessLength);
            PrefabInstance access = prefab(doc, accessId,
                    0f, topBase, 1f);
            access.transform.yaw = 180f;
            float parapetY = floors * 3.3f + 0.38f;
            block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    0f, parapetY, hz, hx, 0.38f, 0.12f, wallColor);
            block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    0f, parapetY, -hz, hx, 0.38f, 0.12f, wallColor);
            block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    -hx, parapetY, 0f, 0.12f, 0.38f, hz, wallColor);
            block(doc, StructureObject.ROLE_BLOCK, wallMaterial,
                    hx, parapetY, 0f, 0.12f, 0.38f, hz, wallColor);
            prefab(doc, "pickup.special", hx * 0.45f,
                    floors * 3.3f + 0.5f, -hz * 0.45f);
        } else if ("partial".equals(plan.roofStyle)) {
            block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    -hx * 0.55f, roofY, 0f, hx * 0.45f, 0.15f, hz,
                    wallColor);
            block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    hx * 0.55f, roofY, 0f, hx * 0.45f, 0.15f, hz,
                    wallColor);
        } else if (!"open".equals(plan.roofStyle)) {
            block(doc, StructureObject.ROLE_CEILING, wallMaterial,
                    0f, roofY, 0f, hx, 0.15f, hz, wallColor);
        }
    }

    private static void furnishStory(MapDocument doc, String kind,
                                     int level, float baseY,
                                     float hx, float hz) {
        float x = Math.min(3.1f, hx * 0.56f);
        float z = Math.min(3.2f, hz * 0.48f);
        if ("laboratory".equals(kind) || "warehouse".equals(kind)
                || "station".equals(kind)) {
            prefab(doc, "furniture.workbench", -x, baseY, -z);
            prefab(doc, "furniture.shelf", x, baseY, -z);
            prefab(doc, level == 0 ? "obstacle.barrel"
                    : "furniture.cabinet", x, baseY, z);
        } else if (level == 0) {
            PrefabInstance sofa = prefab(doc, "furniture.sofa",
                    -x, baseY, -z);
            sofa.transform.yaw = 90f;
            prefab(doc, "furniture.table", x, baseY, -z);
            prefab(doc, "furniture.sink.kitchen", -x, baseY, z);
            prefab(doc, "prop.plant.tall", x, baseY, z);
        } else if (level == 1) {
            prefab(doc, "furniture.bed", -x, baseY, -z);
            prefab(doc, "furniture.wardrobe", x, baseY, -z);
            prefab(doc, "furniture.toilet", x, baseY, z);
            prefab(doc, "furniture.sink.bath", -x, baseY, z);
        } else {
            prefab(doc, "furniture.workbench", -x, baseY, -z);
            prefab(doc, "furniture.shelf", x, baseY, -z);
            prefab(doc, "furniture.chair", -x, baseY, z);
            prefab(doc, "prop.plant.small", x, baseY, z);
        }
    }

    private static void indoorLights(MapDocument doc, int level,
                                     float baseY, float hx, float hz) {
        float x = Math.min(2.6f, hx * 0.42f);
        for (int side = -1; side <= 1; side += 2) {
            PrefabInstance lamp = prefab(doc, "prop.lamp.ceiling",
                    side * x, baseY + 3f, -hz * 0.12f);
            lamp.properties.put("lightR", level == 0 ? 1f : 0.78f);
            lamp.properties.put("lightG", level == 0 ? 0.78f : 0.86f);
            lamp.properties.put("lightB", level == 0 ? 0.56f : 1f);
            lamp.properties.put("lightRadius", 6f);
        }
    }

    private static void buildCourtyard(MapDocument doc, AiScenarioPlan plan,
                                       AiScenarioProfile profile,
                                       Random random) {
        float half = profile.halfSize();
        float span = Math.min(half * 0.46f, half - 5.2f);
        float room = Math.min(4.1f, half * 0.24f);
        block(doc, StructureObject.ROLE_FLOOR, "checker",
                0f, 0.025f, 0f, Math.min(5f, half * 0.3f), 0.025f,
                Math.min(5f, half * 0.3f),
                new float[]{0.48f, 0.46f, 0.42f});
        int count = Math.min(4, Math.max(2, plan.buildingCount));
        // branching desalinha as alas; loop cerca o jardim com diagonais.
        float stagger = "branching".equals(plan.route) ? span * 0.30f : 0f;
        float[] xs = {-span, span, -span, span};
        float[] zs = {-span, -span, span, span};
        for (int i = 0; i < count; i++) {
            float cz = zs[i] + (i % 2 == 0 ? stagger : -stagger);
            addDetachedBuilding(doc, plan, plan.zoneAt(i), xs[i], cz,
                    room, room * (0.78f + random.nextFloat() * 0.12f), i);
        }
        if ("loop".equals(plan.route)) {
            plazaChamfers(doc, plan, Math.min(4.6f, half * 0.28f));
        }
        prefab(doc, "prop.plant.tall", -1.8f, 0f, 0f);
        prefab(doc, "prop.plant.tall", 1.8f, 0f, 0f);
    }

    private static void buildCampus(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile, Random random,
                                    boolean scattered) {
        float half = profile.halfSize();
        int count = Math.min(6, Math.max(2, plan.buildingCount));
        int rows = (count + 1) / 2;
        float hx = Math.min(4.2f, half * 0.22f);
        float hz = Math.min(3.4f, half * 0.18f);
        for (int i = 0; i < count; i++) {
            int side = (i & 1) == 0 ? -1 : 1;
            int row = i / 2;
            float x = side * half * (scattered
                    ? 0.34f + random.nextFloat() * 0.20f : 0.46f);
            float z = rowPosition(row, rows, half, 5.2f, 7.5f);
            if (scattered) z += (random.nextFloat() - 0.5f) * 4f;
            addDetachedBuilding(doc, plan, plan.zoneAt(i), x, z,
                    hx * (0.82f + random.nextFloat() * 0.18f),
                    hz * (0.82f + random.nextFloat() * 0.18f), i);
        }
    }

    /** A rota muda a praça: alas cardeais, seis alas ou anel diagonal. */
    private static void buildHub(MapDocument doc, AiScenarioPlan plan,
                                 AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        float span = Math.min(half - 5.5f, half * 0.56f);
        block(doc, StructureObject.ROLE_FLOOR, "checker",
                0f, 0.025f, 0f, 5.2f, 0.025f, 5.2f,
                new float[]{0.40f, 0.43f, 0.48f});
        block(doc, StructureObject.ROLE_BLOCK, "metal",
                0f, 0.65f, 0f, 0.8f, 0.65f, 0.8f, DARK);
        boolean diagonalWings = "loop".equals(plan.route);
        int wings = "branching".equals(plan.route) ? 6 : 4;
        if (!diagonalWings) {
            plazaChamfers(doc, plan, Math.min(6.6f, span - 5.4f));
        }
        for (int i = 0; i < wings; i++) {
            double angle = i * (Math.PI * 2.0 / wings)
                    + (diagonalWings ? Math.PI / 4.0 : 0.0);
            float cx = (float) Math.cos(angle) * span;
            float cz = (float) Math.sin(angle) * span;
            addDetachedBuilding(doc, plan, plan.zoneAt(i), cx, cz,
                    i % 2 == 0 ? 3.4f : 4.4f,
                    i % 2 == 0 ? 4.4f : 3.4f, i);
        }
        prefab(doc, "pickup.special", 0f, 1.8f, 0f);
    }

    /** Cantos chanfrados da praça em paredes diagonais (KIND_POLY). */
    private static void plazaChamfers(MapDocument doc, AiScenarioPlan plan,
                                      float radius) {
        if (radius < 2.4f) return;
        String material = wallMaterial(plan);
        float[] color = wallColor(plan);
        float near = radius * 0.42f;
        diagonalWall(doc, near, radius, radius, near, material, color);
        diagonalWall(doc, -near, radius, -radius, near, material, color);
        diagonalWall(doc, near, -radius, radius, -near, material, color);
        diagonalWall(doc, -near, -radius, -radius, -near, material, color);
    }

    private static void buildMaze(MapDocument doc, AiScenarioPlan plan,
                                  AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        int rows = Math.min(7, Math.max(4, plan.roomCount / 2 + 2));
        float length = half - 2.2f;
        for (int i = 0; i < rows; i++) {
            float z = rowPosition(i, rows, half, 4.5f, 6.8f);
            StructureObject cross = wall(doc, 0f, 1.5f, z,
                    length, 0.13f, wallMaterial(plan), wallColor(plan), true);
            float offset = (i & 1) == 0 ? -length * 0.62f
                    : length * 0.62f;
            cross.openings.add(opening(offset, 3f, 2.35f));
            if (i + 1 < rows && i % 2 == 0) {
                float branchX = (i % 4 == 0 ? -1f : 1f) * half * 0.28f;
                wall(doc, branchX, 1.5f,
                        z + (half * 1.4f / rows),
                        0.13f, half * 0.18f, wallMaterial(plan),
                        wallColor(plan), false);
            }
        }
    }

    private static void buildLinear(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile,
                                    Random random) {
        float half = profile.halfSize();
        int chambers = Math.min(6, Math.max(3, plan.roomCount / 2));
        float length = half - 2.3f;
        for (int i = 0; i < chambers; i++) {
            float z = rowPosition(i, chambers, half, 5.4f, 7.4f);
            StructureObject cross = wall(doc, 0f, 1.5f, z,
                    length, 0.15f, wallMaterial(plan), wallColor(plan), true);
            float doorX = (i % 3 - 1) * Math.min(3.2f, half * 0.18f);
            cross.openings.add(opening(doorX, 2.4f, 2.3f));
            prefab(doc, (i & 1) == 0 ? "obstacle.crate.small"
                    : "obstacle.barrel", -doorX, 0f,
                    z + Math.min(3.3f, half * 0.15f));
        }
        if ("laboratory".equals(plan.setting)
                || "industrial".equals(plan.setting)) {
            block(doc, StructureObject.ROLE_CEILING, "metal",
                    0f, 3.15f, 0f, half - 0.4f, 0.15f, half - 0.4f, DARK);
        }
    }

    private static void addDetachedBuilding(MapDocument doc,
                                            AiScenarioPlan plan,
                                            AiScenarioPlan.Zone zone,
                                            float cx, float cz,
                                            float hx, float hz, int index) {
        String material = buildingMaterial(plan, zone);
        float[] color = buildingColor(plan, zone);
        String floorMaterial = buildingFloorMaterial(zone);
        float frontZ = cz >= 0f ? cz - hz : cz + hz;
        float backZ = cz >= 0f ? cz + hz : cz - hz;
        StructureObject front = wall(doc, cx, 1.5f, frontZ,
                hx, 0.14f, material, color, true);
        front.openings.add(opening(0f, 1.35f, 2.2f));
        wall(doc, cx, 1.5f, backZ, hx, 0.14f,
                material, color, false);
        wall(doc, cx - hx, 1.5f, cz, 0.14f, hz,
                material, color, false);
        wall(doc, cx + hx, 1.5f, cz, 0.14f, hz,
                material, color, false);
        block(doc, StructureObject.ROLE_FLOOR, floorMaterial,
                cx, 0.025f, cz, hx - 0.14f, 0.025f, hz - 0.14f,
                new float[]{0.43f, 0.39f, 0.34f});
        if (!"open".equals(plan.roofStyle)) {
            block(doc, StructureObject.ROLE_CEILING, material,
                    cx, 3.15f, cz, hx, 0.15f, hz, DARK);
        }
        if (plan.hasFeature("automatic_doors") && (index & 1) == 0) {
            PrefabInstance door = prefab(doc, "door.auto",
                    cx, 1.05f, frontZ);
            door.properties.put("halfX", 0.64f);
            door.properties.put("halfY", 1.05f);
            door.properties.put("halfZ", 0.08f);
        }
        if (plan.hasFeature("furniture")) {
            prefab(doc, "house".equals(zone.kind) ? "furniture.table"
                    : "furniture.workbench", cx, 0f, cz);
        }
        if (plan.hasFeature("indoor_lights")) {
            PrefabInstance lamp = prefab(doc, "prop.lamp.ceiling",
                    cx, 3f, cz);
            lamp.properties.put("lightR", 0.90f);
            lamp.properties.put("lightG", 0.78f);
            lamp.properties.put("lightB", 0.62f);
            lamp.properties.put("lightRadius", 5.8f);
        }
    }

    /** Desenha no piso a lógica de percurso sem entregar coordenadas à IA. */
    private static void routeAccent(MapDocument doc, AiScenarioPlan plan,
                                    float half) {
        float reach = Math.max(3.5f, half - 5.2f);
        float[] color = new float[]{0.28f, 0.31f, 0.36f};
        if ("loop".equals(plan.route)) {
            float span = Math.min(half * 0.38f, 9f);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    -span, 0.015f, 0f, 0.75f, 0.015f, span, color);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    span, 0.015f, 0f, 0.75f, 0.015f, span, color);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, -span, span, 0.015f, 0.75f, color);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, span, span, 0.015f, 0.75f, color);
        } else if ("branching".equals(plan.route)) {
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, 0f, 0.85f, 0.015f, reach, color);
            float z = -Math.min(half * 0.26f, 7f);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, z, Math.min(half * 0.48f, 11f),
                    0.015f, 0.72f, color);
            z = Math.min(half * 0.20f, 5f);
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, z, Math.min(half * 0.36f, 8f),
                    0.015f, 0.72f, color);
        } else if ("direct".equals(plan.route)) {
            block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, 0f, 0.9f, 0.015f, reach, color);
        }
    }

    private static void diagonalLandmarks(MapDocument doc,
                                          AiScenarioPlan plan, float half) {
        float reach = Math.min(half * 0.38f, 9f);
        diagonalWall(doc, -reach, -reach * 0.55f,
                -reach * 0.28f, -reach, wallMaterial(plan), wallColor(plan));
        diagonalWall(doc, reach, reach * 0.55f,
                reach * 0.28f, reach, wallMaterial(plan), wallColor(plan));
    }

    private static void bridge(MapDocument doc, AiScenarioPlan plan,
                               float half) {
        float z = -Math.min(half * 0.24f, 7f);
        block(doc, StructureObject.ROLE_FLOOR, "metal",
                0f, 0.85f, z, 2.1f, 0.15f, 3.2f,
                new float[]{0.36f, 0.40f, 0.46f});
        PrefabInstance front = prefab(doc, "ramp.short", 0f, 0f, z + 4.3f);
        front.transform.yaw = 180f;
        prefab(doc, "ramp.short", 0f, 0f, z - 4.3f);
    }

    private static boolean isUnderground(AiScenarioPlan plan) {
        return "tunnel".equals(plan.setting)
                || "underground".equals(plan.layout);
    }

    private static boolean isFocalLayout(AiScenarioPlan plan) {
        return "single_building".equals(plan.layout)
                || "vertical".equals(plan.layout);
    }

    private static boolean isVerticalMap(AiScenarioPlan plan) {
        return isFocalLayout(plan) && effectiveFloors(plan) > 1;
    }

    private static int effectiveFloors(AiScenarioPlan plan) {
        int floors = Math.max(plan.floors, plan.primaryZone().floors);
        if (plan.hasFeature("second_floor")) floors = Math.max(2, floors);
        return Math.min(3, Math.max(1, floors));
    }

    private static float houseHalfX(AiScenarioPlan plan,
                                    AiScenarioProfile profile) {
        float base = "huge".equals(profile.sectorSize()) ? 9f
                : "large".equals(profile.sectorSize()) ? 7.8f
                : "medium".equals(profile.sectorSize()) ? 6.5f : 5.3f;
        if ("large".equals(plan.primaryZone().size)) base += 0.8f;
        if ("small".equals(plan.primaryZone().size)) base -= 0.5f;
        return Math.min(profile.halfSize() - 3.2f, base);
    }

    private static float houseHalfZ(AiScenarioPlan plan,
                                    AiScenarioProfile profile) {
        float base = "huge".equals(profile.sectorSize()) ? 10.5f
                : "large".equals(profile.sectorSize()) ? 9f
                : "medium".equals(profile.sectorSize()) ? 7.8f : 6.3f;
        if ("large".equals(plan.primaryZone().size)) base += 0.8f;
        if ("small".equals(plan.primaryZone().size)) base -= 0.4f;
        return Math.min(profile.halfSize() - 3.5f, base);
    }

    private static String buildingMaterial(AiScenarioPlan plan,
                                           AiScenarioPlan.Zone zone) {
        if ("laboratory".equals(zone.kind) || "warehouse".equals(zone.kind)
                || "station".equals(zone.kind)) return "metal";
        if ("house".equals(zone.kind) || "apartment".equals(zone.kind)
                || "shop".equals(zone.kind) || "ruins".equals(zone.kind)) {
            return "brick";
        }
        return wallMaterial(plan);
    }

    private static float[] buildingColor(AiScenarioPlan plan,
                                         AiScenarioPlan.Zone zone) {
        if ("house".equals(zone.kind) || "shop".equals(zone.kind)) {
            return new float[]{0.64f, 0.52f, 0.40f};
        }
        if ("laboratory".equals(zone.kind)) return LIGHT;
        if ("warehouse".equals(zone.kind) || "station".equals(zone.kind)) {
            return new float[]{0.39f, 0.43f, 0.48f};
        }
        return wallColor(plan);
    }

    private static String buildingFloorMaterial(AiScenarioPlan.Zone zone) {
        if ("house".equals(zone.kind) || "apartment".equals(zone.kind)
                || "shop".equals(zone.kind)) return "wood";
        if ("laboratory".equals(zone.kind)) return "checker";
        return "metal";
    }

    /** A rota escolhe a malha viária: reta, avenidas gêmeas ou cruzamentos. */
    private static void buildCity(MapDocument doc, AiScenarioPlan plan,
                                  AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        float roomX = "huge".equals(profile.sectorSize()) ? 7.2f
                : "large".equals(profile.sectorSize()) ? 6.5f
                : "medium".equals(profile.sectorSize()) ? 5.2f : 4.0f;
        float roomZ = "huge".equals(profile.sectorSize()) ? 3.65f
                : "large".equals(profile.sectorSize()) ? 3.2f : 2.6f;
        if ("loop".equals(plan.route) && half >= 24f) {
            buildTwinAvenues(doc, plan, profile, random,
                    half, roomX, roomZ);
        } else if ("maze".equals(plan.route) && half >= 20f) {
            buildCrossQuarters(doc, plan, profile, random,
                    half, roomX, roomZ);
        } else {
            buildAvenue(doc, plan, profile, random, half, roomX, roomZ,
                    "branching".equals(plan.route));
        }
        if ("city".equals(plan.setting)) {
            roadCenterLine(doc, half);
            if ("huge".equals(profile.sectorSize())) {
                skylineVolumes(doc, half, random);
            }
        }
    }

    /**
     * maze na cidade: avenida em cruz com quadras nos quatro quadrantes,
     * o desenho da Cidade Aurora — referência de complexidade do app.
     */
    private static void buildCrossQuarters(MapDocument doc,
                                           AiScenarioPlan plan,
                                           AiScenarioProfile profile,
                                           Random random, float half,
                                           float roomX, float roomZ) {
        int buildings = Math.min(profile.rows() * 2,
                Math.max(4, plan.buildingCount));
        float roadHalf = 3.2f;
        for (int index = 0; index < buildings; index++) {
            int quadrant = index % 4;
            int ring = index / 4;
            float qx = quadrant % 2 == 0 ? -1f : 1f;
            float qz = quadrant < 2 ? -1f : 1f;
            float hx = roomX * (0.78f + random.nextFloat() * 0.12f);
            float hz = roomZ * (0.88f + random.nextFloat() * 0.18f);
            float cx = qx * (roadHalf + hx + 0.4f);
            float cz = qz * (roadHalf + hz + 0.8f
                    + ring * (hz * 2f + 2.4f));
            if (Math.abs(cz) + hz > half - 1.2f) continue;
            addRoom(doc, cx, cz, hx, hz, (int) -qx, plan, index, random);
        }
        if ("city".equals(plan.setting)) {
            crosswalks(doc, roadHalf);
        }
    }

    /** Faixas de pedestres nas bocas do cruzamento, como na Aurora. */
    private static void crosswalks(MapDocument doc, float roadHalf) {
        float[] white = {0.88f, 0.88f, 0.86f};
        for (int approach = 0; approach < 2; approach++) {
            float z = (approach == 0 ? -1f : 1f) * (roadHalf + 1.2f);
            for (int stripe = -1; stripe <= 1; stripe++) {
                block(doc, StructureObject.ROLE_FLOOR, "plain",
                        stripe * 1.1f, 0.02f, z, 0.38f, 0.02f, 0.55f,
                        white);
            }
        }
    }

    /** Volumes simples de skyline nos cantos: cidade grande tem horizonte. */
    private static void skylineVolumes(MapDocument doc, float half,
                                       Random random) {
        float edge = half - 3.4f;
        for (int corner = 0; corner < 4; corner++) {
            float sx = corner % 2 == 0 ? -edge : edge;
            float sz = corner < 2 ? -edge : edge;
            float height = 6.5f + random.nextFloat() * 3.5f;
            block(doc, StructureObject.ROLE_BLOCK, "metal",
                    sx, height / 2f, sz, 2.4f, height / 2f, 2.4f,
                    new float[]{0.16f, 0.19f, 0.24f});
        }
    }

    private static void buildAvenue(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile, Random random,
                                    float half, float roomX, float roomZ,
                                    boolean crossings) {
        int buildings = Math.min(profile.rows() * 2,
                Math.max(1, plan.buildingCount));
        int rows = (buildings + 1) / 2;
        float roadHalf = 3.2f;
        float crossA = -Math.min(half * 0.26f, 7f);
        float crossB = Math.min(half * 0.20f, 5f);
        for (int row = 0; row < rows; row++) {
            float z = rowPosition(row, rows, half, 6.2f, 7.2f);
            if (row > 0 && row + 1 < rows) {
                z += (random.nextFloat() - 0.5f) * 1.4f;
            }
            if (crossings) {
                // Transversais do branching viram ruas de verdade: o
                // quarteirão sai da faixa em vez de bloquear o cruzamento.
                float clear = roomZ + 1.6f;
                if (Math.abs(z - crossA) < clear) {
                    z = crossA + (z >= crossA ? clear : -clear);
                }
                if (Math.abs(z - crossB) < clear) {
                    z = crossB + (z >= crossB ? clear : -clear);
                }
            }
            float leftX = roomX * (0.90f + random.nextFloat() * 0.10f);
            float rightX = roomX * (0.90f + random.nextFloat() * 0.10f);
            float leftZ = roomZ * (0.88f + random.nextFloat() * 0.18f);
            float rightZ = roomZ * (0.88f + random.nextFloat() * 0.18f);
            int leftIndex = row * 2;
            if (leftIndex < buildings) {
                addRoom(doc, -(roadHalf + leftX), z, leftX, leftZ,
                        -1, plan, leftIndex, random);
            }
            if (leftIndex + 1 < buildings) {
                addRoom(doc, roadHalf + rightX, z, rightX, rightZ,
                        1, plan, leftIndex + 1, random);
            }
        }
    }

    /** loop: duas avenidas paralelas com quadra central — dá para circular. */
    private static void buildTwinAvenues(MapDocument doc, AiScenarioPlan plan,
                                         AiScenarioProfile profile,
                                         Random random, float half,
                                         float roomX, float roomZ) {
        int buildings = Math.min(profile.rows() * 3,
                Math.max(3, plan.buildingCount));
        int rows = (buildings + 2) / 3;
        float roadHalf = 3.2f;
        float span = Math.min(half * 0.45f, roomX * 2f + roadHalf * 2.4f);
        for (int row = 0; row < rows; row++) {
            float z = rowPosition(row, rows, half, 6.2f, 7.2f);
            if (row > 0 && row + 1 < rows) {
                z += (random.nextFloat() - 0.5f) * 1.4f;
            }
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                if (index >= buildings) break;
                float hx = roomX * (0.72f + random.nextFloat() * 0.10f);
                float hz = roomZ * (0.88f + random.nextFloat() * 0.18f);
                float cx = col == 0 ? -(span + roadHalf + hx)
                        : col == 2 ? span + roadHalf + hx : 0f;
                int side = col == 0 ? -1
                        : col == 2 ? 1 : (row % 2 == 0 ? -1 : 1);
                if (col == 1) {
                    hx = Math.min(hx, span - roadHalf - 0.6f);
                    if (hx < 1.6f) continue;
                }
                addRoom(doc, cx, z, hx, hz, side, plan, index, random);
            }
        }
    }

    /** Faixa central tracejada da avenida, como na Cidade Aurora. */
    private static void roadCenterLine(MapDocument doc, float half) {
        float reach = half - 6.5f;
        int dashes = Math.max(4, (int) (reach / 4f));
        for (int i = 0; i < dashes; i++) {
            float z = -reach + (2f * reach) * i / (dashes - 1f);
            block(doc, StructureObject.ROLE_FLOOR, "plain",
                    0f, 0.02f, z, 0.12f, 0.02f, 1.1f,
                    new float[]{0.92f, 0.90f, 0.78f});
        }
    }

    private static void buildIndustrial(MapDocument doc, AiScenarioPlan plan,
                                        AiScenarioProfile profile,
                                        Random random) {
        int bays = Math.max(2, profile.rows() - 1);
        float half = profile.halfSize();
        float hx = "huge".equals(profile.sectorSize()) ? 8.2f
                : "large".equals(profile.sectorSize()) ? 7.0f
                : "medium".equals(profile.sectorSize()) ? 5.4f : 4.1f;
        float hz = "compact".equals(profile.sectorSize()) ? 3.0f : 4.2f;
        for (int i = 0; i < bays; i++) {
            float z = rowPosition(i, bays, half, 7f, 8f);
            addIndustrialBay(doc, -(3.8f + hx), z, hx, hz, -1,
                    i * 2, random);
            addIndustrialBay(doc, 3.8f + hx, z, hx, hz, 1,
                    i * 2 + 1, random);
        }
        // Obstáculos centrais alternados mudam o ritmo sem fechar a rota.
        for (int i = 0; i < profile.rows(); i++) {
            float z = rowPosition(i, profile.rows(), half, 5f, 7f);
            PrefabInstance crate = prefab(doc,
                    (i & 1) == 0 ? "obstacle.crate.large"
                            : "obstacle.crate.small",
                    (i & 1) == 0 ? -2.35f : 2.35f, 0f, z);
            crate.transform.yaw = (i & 1) * 90f;
        }
    }

    private static void buildLaboratory(MapDocument doc, AiScenarioPlan plan,
                                        AiScenarioProfile profile,
                                        Random random) {
        buildCity(doc, plan, profile, random);
        int checkpoints = Math.max(1, profile.rows() - 1);
        float half = profile.halfSize();
        for (int i = 0; i < checkpoints; i++) {
            float z = rowPosition(i, checkpoints, half, 10f, 11f);
            StructureObject partition = wall(doc, 0f, 1.5f, z,
                    3.25f, 0.12f, "metal", LIGHT, true);
            partition.openings.add(opening(0f, 2.35f, 2.25f));
            if (plan.hasFeature("automatic_doors")) {
                PrefabInstance door = prefab(doc, "door.auto", 0f, 1.05f, z);
                door.properties.put("halfX", 1.10f);
                door.properties.put("halfY", 1.05f);
                door.properties.put("halfZ", 0.08f);
            }
        }
    }

    private static void buildFortress(MapDocument doc, AiScenarioPlan plan,
                                      AiScenarioProfile profile,
                                      Random random) {
        float half = profile.halfSize();
        float innerX = Math.min(half - 4.5f, half * 0.46f);
        float length = half - 3.2f;
        StructureObject left = wall(doc, -innerX, 1.5f, 0f,
                0.18f, length, "plain", wallColor(plan), false);
        StructureObject right = wall(doc, innerX, 1.5f, 0f,
                0.18f, length, "plain", wallColor(plan), false);
        int sideDoors = Math.max(2, profile.rows() - 1);
        for (int i = 0; i < sideDoors; i++) {
            float offset = rowPosition(i, sideDoors, half, 7f, 8f);
            left.openings.add(opening(offset, 2.1f, 2.25f));
            right.openings.add(opening(offset, 2.1f, 2.25f));
        }
        for (int side = -1; side <= 1; side += 2) {
            float z = side * half * 0.43f;
            StructureObject cross = wall(doc, 0f, 1.5f, z,
                    innerX, 0.18f, "plain", wallColor(plan), true);
            cross.openings.add(opening(0f, 3f, 2.4f));
        }
        for (int ix = -1; ix <= 1; ix += 2) {
            for (int iz = -1; iz <= 1; iz += 2) {
                float x = ix * (innerX - 2.1f);
                float z = iz * half * 0.62f;
                block(doc, StructureObject.ROLE_BLOCK, "plain",
                        x, 1.8f, z, 1.8f, 1.8f, 1.8f,
                        new float[]{0.31f, 0.33f, 0.36f});
                block(doc, StructureObject.ROLE_CEILING, "plain",
                        x, 3.75f, z, 2.05f, 0.15f, 2.05f, DARK);
            }
        }
    }

    private static void buildRuins(MapDocument doc, AiScenarioPlan plan,
                                   AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        int pieces = profile.rows() * 4 + 2;
        for (int i = 0; i < pieces; i++) {
            int side = (i & 1) == 0 ? -1 : 1;
            float z = -half + 4.5f + (half * 2f - 9f)
                    * i / Math.max(1f, pieces - 1f);
            z += (random.nextFloat() - 0.5f) * 1.8f;
            float x = side * (4.5f + random.nextFloat()
                    * Math.max(1f, half * 0.34f));
            if (i % 3 == 0) {
                wall(doc, x, 1.5f, z, 2.2f, 0.15f,
                        "brick", wallColor(plan), false);
            } else {
                wall(doc, x, 1.5f, z, 0.15f, 1.7f,
                        "brick", wallColor(plan), false);
            }
            if (i % 4 == 1) {
                block(doc, StructureObject.ROLE_CEILING, "plain",
                        x, 3.15f, z, 1.4f, 0.15f, 1.2f, DARK);
            }
        }
    }

    /** Teto único cobre toda a área jogável, inclusive entradas e salas. */
    private static void buildTunnel(MapDocument doc, AiScenarioPlan plan,
                                    AiScenarioProfile profile,
                                    Random random) {
        float half = profile.halfSize();
        float corridor = Math.min(5.2f, half * 0.42f);
        block(doc, StructureObject.ROLE_CEILING, "metal",
                0f, 3.15f, 0f, half, 0.15f, half,
                new float[]{0.25f, 0.28f, 0.30f});

        StructureObject left = wall(doc, -corridor, 1.5f, 0f,
                0.15f, half - 1f, "metal", wallColor(plan), false);
        StructureObject right = wall(doc, corridor, 1.5f, 0f,
                0.15f, half - 1f, "metal", wallColor(plan), false);
        for (int i = 0; i < profile.rows(); i++) {
            float offset = rowPosition(i, profile.rows(), half, 5.5f, 6.5f);
            if ((i & 1) == 0) left.openings.add(
                    opening(offset, 2.2f, 2.25f));
            else right.openings.add(opening(offset, 2.2f, 2.25f));
        }

        int bulkheads = Math.max(1, profile.rows() - 1);
        for (int i = 0; i < bulkheads; i++) {
            float z = rowPosition(i, bulkheads, half, 9f, 10f);
            StructureObject rib = wall(doc, 0f, 1.5f, z,
                    corridor, 0.16f, "metal", DARK, true);
            rib.openings.add(opening(0f, 3.1f, 2.4f));
        }

        int lights = profile.rows() * 2 + 2;
        for (int i = 0; i < lights; i++) {
            float z = -half + 3.2f + (half * 2f - 6.4f)
                    * i / Math.max(1f, lights - 1f);
            PrefabInstance lamp = prefab(doc, "prop.lamp.ceiling",
                    (i & 1) == 0 ? -1.65f : 1.65f, 2.92f, z);
            lamp.properties.put("lightR", 0.62f);
            lamp.properties.put("lightG", 0.78f
                    + random.nextFloat() * 0.12f);
            lamp.properties.put("lightB", 0.90f);
            lamp.properties.put("lightRadius", 6.4f);
        }
        for (int i = 0; i < profile.rows(); i++) {
            float z = rowPosition(i, profile.rows(), half, 6f, 7f);
            prefab(doc, (i & 1) == 0 ? "obstacle.crate.small"
                            : "obstacle.crate.large",
                    (i & 1) == 0 ? -corridor - 2f : corridor + 2f,
                    0f, z);
        }
    }

    /** side -1 = sala à esquerda; +1 = à direita da via. */
    private static void addRoom(MapDocument doc, float cx, float cz,
                                float hx, float hz, int side,
                                AiScenarioPlan plan, int index,
                                Random random) {
        String material = wallMaterial(plan);
        float[] color = wallColor(plan);
        float frontX = cx - side * hx;
        float backX = cx + side * hx;
        StructureObject front = wall(doc, frontX, 1.5f, cz,
                0.15f, hz, material, color, true);
        front.openings.add(opening(0f, 1.25f, 2.15f));
        wall(doc, backX, 1.5f, cz, 0.15f, hz,
                material, color, false);
        wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                material, color, false);
        wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                material, color, false);
        block(doc, StructureObject.ROLE_FLOOR,
                "laboratory".equals(plan.setting) ? "checker" : "wood",
                cx, 0.025f, cz, hx - 0.15f, 0.025f, hz - 0.15f,
                new float[]{0.42f, 0.36f, 0.30f});
        block(doc, StructureObject.ROLE_CEILING, "plain",
                cx, 3.15f, cz, hx, 0.15f, hz, DARK);

        if (plan.hasFeature("automatic_doors") && (index & 1) == 0) {
            PrefabInstance door = prefab(doc, "door.auto", frontX,
                    1.05f, cz);
            door.properties.put("halfX", 0.08f);
            door.properties.put("halfY", 1.05f);
            door.properties.put("halfZ", 0.60f);
        }
        if ((index & 1) == 0) {
            PrefabInstance lamp = prefab(doc, "prop.lamp.ceiling",
                    cx, 3f, cz);
            lamp.properties.put("lightR", 0.82f
                    + random.nextFloat() * 0.18f);
            lamp.properties.put("lightG", 0.72f);
            lamp.properties.put("lightB", 0.52f);
            lamp.properties.put("lightRadius", 5.5f);
        } else {
            prefab(doc, "obstacle.crate.large", cx, 0f, cz);
        }
    }

    private static void addIndustrialBay(MapDocument doc, float cx, float cz,
                                         float hx, float hz, int side,
                                         int index, Random random) {
        float backX = cx + side * hx;
        wall(doc, backX, 1.5f, cz, 0.15f, hz,
                "metal", CONCRETE, false);
        wall(doc, cx, 1.5f, cz - hz, hx, 0.15f,
                "metal", CONCRETE, false);
        wall(doc, cx, 1.5f, cz + hz, hx, 0.15f,
                "metal", CONCRETE, false);
        block(doc, StructureObject.ROLE_FLOOR, "metal",
                cx, 0.025f, cz, hx, 0.025f, hz,
                new float[]{0.30f, 0.33f, 0.36f});
        block(doc, StructureObject.ROLE_CEILING, "metal",
                cx, 3.15f, cz, hx, 0.15f, hz, DARK);
        prefab(doc, (index & 1) == 0 ? "obstacle.crate.large"
                        : "obstacle.crate.small",
                cx, 0f, cz + (random.nextFloat() - 0.5f) * hz);
        PrefabInstance lamp = prefab(doc, "prop.lamp.ceiling",
                cx, 3f, cz);
        lamp.properties.put("lightR", 0.72f);
        lamp.properties.put("lightG", 0.80f);
        lamp.properties.put("lightB", 0.86f);
        lamp.properties.put("lightRadius", 6.2f);
    }

    private static void terminalGate(MapDocument doc, float half) {
        float gap = 2.1f;
        float segmentHalf = (half - gap) * 0.5f;
        wall(doc, -(half + gap) * 0.5f, 1.5f, 0f,
                segmentHalf, 0.18f, "metal", DARK, false);
        wall(doc, (half + gap) * 0.5f, 1.5f, 0f,
                segmentHalf, 0.18f, "metal", DARK, false);
        PrefabInstance terminal = prefab(doc, "terminal.wall",
                -2.5f, 1.4f, 3.2f);
        terminal.properties.put("order", 1f);
        PrefabInstance gate = prefab(doc, "door.gate", 0f, 1.4f, 0f);
        gate.properties.put("halfX", 2.0f);
        gate.properties.put("halfY", 1.4f);
        gate.properties.put("halfZ", 0.18f);
        gate.properties.put("controllerId", terminal.id);
    }

    private static void sectorPortal(MapDocument doc, float half) {
        float z = -half + 3.8f;
        StructureObject frame = wall(doc, 0f, 1.5f, z,
                5.2f, 0.16f, "metal", DARK, true);
        frame.openings.add(opening(0f, 2.35f, 2.25f));
        PrefabInstance door = prefab(doc, "door.auto", 0f, 1.05f, z);
        door.properties.put("halfX", 1.10f);
        door.properties.put("halfY", 1.05f);
        door.properties.put("halfZ", 0.08f);
    }

    private static void streetLights(MapDocument doc, float half, int count) {
        for (int i = 0; i < count; i++) {
            float z = -half + 4f + (half * 2f - 8f)
                    * i / Math.max(1f, count - 1f);
            float x = (i & 1) == 0 ? -2.45f : 2.45f;
            PrefabInstance lamp = prefab(doc, "prop.lamp.street", x, 0f, z);
            lamp.transform.yaw = x < 0f ? 270f : 90f;
            lamp.properties.put("lightR", 1f);
            lamp.properties.put("lightG", 0.72f);
            lamp.properties.put("lightB", 0.38f);
            lamp.properties.put("lightRadius", 8f);
            lamp.properties.put("lightOffsetY", 3.35f);
        }
    }

    private static void hazard(MapDocument doc, String material,
                               float x, float z) {
        block(doc, StructureObject.ROLE_FLOOR, material,
                x, -0.08f, z, 2.4f, 0.08f, 2.4f,
                "lava".equals(material)
                        ? new float[]{0.72f, 0.18f, 0.04f}
                        : new float[]{0.10f, 0.38f, 0.66f});
    }

    private static void observationDeck(MapDocument doc, float half,
                                        AiScenarioPlan plan) {
        float x = half - 5f;
        float z = half - 8f;
        block(doc, StructureObject.ROLE_CEILING, "metal",
                x, 3.15f, z, 3f, 0.15f, 3f,
                new float[]{0.38f, 0.42f, 0.48f});
        String accessId = plan.hasFeature("ramps")
                && !plan.hasFeature("stairs") ? "ramp.floor" : "stairs.floor";
        PrefabInstance stairs = prefab(doc, accessId,
                x, 0f, z - 4.8f);
        stairs.transform.yaw = 0f;
        prefab(doc, "pickup.special", x, 3.8f, z);
        block(doc, StructureObject.ROLE_BLOCK, "metal",
                x + 2.85f, 3.75f, z, 0.12f, 0.45f, 3f, DARK);
        block(doc, StructureObject.ROLE_BLOCK, "metal",
                x - 2.85f, 3.75f, z, 0.12f, 0.45f, 3f, DARK);
    }

    private static void addSpawnAndObjective(MapDocument doc,
                                             AiScenarioPlan plan,
                                             AiScenarioProfile profile,
                                             String objectiveType) {
        float half = profile.halfSize();
        LogicMarker spawn = new LogicMarker(Ids.create(),
                LogicMarker.PLAYER_SPAWN);
        spawn.x = 0f;
        spawn.y = 0f;
        spawn.z = isFocalLayout(plan)
                ? Math.min(half - 3f, houseHalfZ(plan, profile) + 2.4f)
                : half - 3f;
        spawn.yaw = 180f;
        doc.markers.add(spawn);

        doc.objective.type = objectiveType;
        float baseTime = "huge".equals(profile.sectorSize()) ? 600f
                : "large".equals(profile.sectorSize()) ? 420f
                : "medium".equals(profile.sectorSize()) ? 300f : 210f;
        if (ObjectiveSpec.REACH_EXIT.equals(objectiveType)) {
            LogicMarker exit = new LogicMarker(Ids.create(), LogicMarker.EXIT);
            if (isVerticalMap(plan)) {
                int floors = effectiveFloors(plan);
                boolean rooftop = plan.hasFeature("rooftop");
                exit.x = rooftop ? houseHalfX(plan, profile) * 0.42f : 0f;
                exit.y = rooftop ? floors * 3.3f : (floors - 1) * 3.3f;
                exit.z = rooftop ? -houseHalfZ(plan, profile) * 0.42f
                        : -houseHalfZ(plan, profile) + 2.2f;
            } else {
                exit.x = 0f;
                exit.y = 0f;
                exit.z = -half + 1.8f;
            }
            exit.radius = 1.35f;
            doc.markers.add(exit);
            doc.objective.timeLimitSeconds = baseTime;
        } else if (ObjectiveSpec.COLLECT.equals(objectiveType)) {
            doc.objective.target = 4 + plan.difficulty * 2;
            doc.objective.timeLimitSeconds = baseTime + 120f;
        } else if (ObjectiveSpec.ELIMINATE_ALL.equals(objectiveType)) {
            doc.objective.timeLimitSeconds = baseTime + 60f;
        } else {
            doc.objective.durationSeconds = 40f + plan.difficulty * 20f;
        }
        if (!ObjectiveSpec.SURVIVE.equals(objectiveType)) {
            float reference = doc.objective.timeLimitSeconds;
            doc.objective.twoStarSeconds = reference * 0.78f;
            doc.objective.threeStarSeconds = reference * 0.58f;
        }
    }

    private static void addHuman(MapDocument doc, AiScenarioPlan plan,
                                 AiScenarioProfile profile) {
        float half = profile.halfSize();
        float z = isFocalLayout(plan)
                ? Math.min(half - 3.8f, houseHalfZ(plan, profile) + 1.35f)
                : half - 5.4f;
        PrefabInstance npc = prefab(doc, "npc.human",
                1.45f, 0f, z);
        npc.transform.yaw = 180f;
        npc.properties.put("name", plan.npcName);
        npc.properties.put("role", plan.npcRole);
        npc.properties.put("greeting", plan.npcGreeting);
        npc.properties.put("background", plan.npcBackground);
    }

    private static void addEnemies(MapDocument doc, AiScenarioPlan plan,
                                   AiScenarioProfile profile, Random random) {
        float half = profile.halfSize();
        int sizeBonus = "huge".equals(profile.sectorSize()) ? 6
                : "large".equals(profile.sectorSize()) ? 4
                : "medium".equals(profile.sectorSize()) ? 2 : 0;
        int count = Math.min(16, plan.difficulty * 2 + sizeBonus);
        boolean bossPlaced = false;
        for (int i = 0; i < count; i++) {
            String type = plan.enemies.get(i % plan.enemies.size());
            if ("boss".equals(type) && bossPlaced) type = "drone";
            String id;
            float y;
            if ("mutant".equals(type)) {
                id = "enemy.mutant";
                y = 0.85f;
            } else if ("turret".equals(type)) {
                id = "enemy.turret";
                y = 0.55f;
            } else if ("kamikaze".equals(type)) {
                id = "enemy.kamikaze";
                y = 1.8f;
            } else if ("boss".equals(type)) {
                id = "enemy.boss";
                y = 2.2f;
                bossPlaced = true;
            } else {
                id = plan.hasFeature("ambush") && (i & 1) == 1
                        ? "enemy.drone.wave" : "enemy.drone";
                y = 1.8f;
            }
            float z;
            float x;
            int story = 0;
            if (isVerticalMap(plan)) {
                story = i % effectiveFloors(plan);
                float hz = houseHalfZ(plan, profile);
                z = -hz + 2.1f + (hz * 2f - 4.2f)
                        * ((i / effectiveFloors(plan)) % 4) / 3f;
                float hx = houseHalfX(plan, profile);
                x = ((i & 1) == 0 ? -1f : 1f)
                        * Math.min(2.8f, hx * 0.48f);
            } else {
                z = -half + 5f + (half * 2f - 13f)
                        * (i + 0.5f) / Math.max(1f, count);
                x = -2.1f + random.nextFloat() * 4.2f;
            }
            y += story * 3.3f;
            PrefabInstance enemy = prefab(doc, id, x, y, z);
            if (!"enemy.turret".equals(id)) {
                enemy.properties.put("patrolX", -x);
                enemy.properties.put("patrolZ", Math.max(-half + 3f,
                        Math.min(half - 6f,
                                z + (i % 2 == 0 ? 3f : -3f))));
            }
        }
    }

    private static void addPickups(MapDocument doc, AiScenarioPlan plan,
                                   AiScenarioProfile profile) {
        float half = profile.halfSize();
        if (ObjectiveSpec.COLLECT.equals(doc.objective.type)) {
            int count = doc.objective.target;
            for (int i = 0; i < count; i++) {
                if (isVerticalMap(plan)) {
                    int story = i % effectiveFloors(plan);
                    float hx = houseHalfX(plan, profile);
                    float hz = houseHalfZ(plan, profile);
                    float x = ((i & 1) == 0 ? -1f : 1f)
                            * Math.min(3f, hx * 0.50f);
                    float z = ((i / 2) % 2 == 0 ? -1f : 1f)
                            * Math.min(3.1f, hz * 0.48f);
                    prefab(doc, "pickup.token", x,
                            story * 3.3f + 0.5f, z);
                } else {
                    float z = -half + 3.5f + (half * 2f - 7f)
                            * i / Math.max(1f, count - 1f);
                    prefab(doc, "pickup.token",
                            (i & 1) == 0 ? -1.2f : 1.2f, 0.5f, z);
                }
            }
        }
        if (plan.hasFeature("supplies")) {
            float front = isFocalLayout(plan)
                    ? houseHalfZ(plan, profile) - 1.4f : half - 6.5f;
            prefab(doc, "pickup.health", -1.3f, 0.5f, front);
            prefab(doc, "pickup.ammo", 1.3f, 0.5f,
                    isFocalLayout(plan) ? -front : half - 8f);
        }
    }

    private static float rowPosition(int index, int count, float half,
                                     float frontMargin, float backMargin) {
        float first = -half + frontMargin;
        float last = half - backMargin;
        return count <= 1 ? 0f
                : first + (last - first) * index / (count - 1f);
    }

    private static StructureObject wall(MapDocument doc, float x, float y,
                                        float z, float hx, float hz,
                                        String material, float[] color,
                                        boolean roadFacing) {
        return block(doc, StructureObject.ROLE_WALL, material,
                x, y, z, hx, 1.5f, hz, color);
    }

    private static StructureObject block(MapDocument doc, String role,
                                         String material, float x, float y,
                                         float z, float hx, float hy,
                                         float hz, float[] color) {
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_BLOCK);
        value.role = role;
        value.material = material;
        value.transform.x = x;
        value.transform.y = y;
        value.transform.z = z;
        value.half = new float[]{hx, hy, hz};
        value.color = color.clone();
        doc.structures.add(value);
        return value;
    }

    private static WallOpening opening(float offset, float width,
                                       float height) {
        WallOpening opening = new WallOpening(Ids.create(), WallOpening.DOOR);
        opening.offset = offset;
        opening.width = width;
        opening.height = height;
        return opening;
    }

    private static WallOpening windowOpening(float offset, float width) {
        WallOpening opening = new WallOpening(Ids.create(),
                WallOpening.WINDOW);
        opening.offset = offset;
        opening.width = width;
        opening.height = 1.05f;
        opening.sill = 1.05f;
        return opening;
    }

    private static StructureObject diagonalWall(MapDocument doc,
                                                float ax, float az,
                                                float bx, float bz,
                                                String material,
                                                float[] color) {
        float length = (float) Math.hypot(bx - ax, bz - az);
        float nx = -(bz - az) / length * 0.15f;
        float nz = (bx - ax) / length * 0.15f;
        StructureObject value = new StructureObject(Ids.create(),
                StructureObject.KIND_POLY);
        value.role = StructureObject.ROLE_WALL;
        value.material = material;
        value.transform.y = 1.5f;
        value.half = new float[]{0f, 1.5f, 0f};
        value.polygon = new float[]{ax + nx, az + nz, bx + nx, bz + nz,
                bx - nx, bz - nz, ax - nx, az - nz};
        value.color = color.clone();
        value.syncPolyBounds();
        doc.structures.add(value);
        return value;
    }

    private static PrefabInstance prefab(MapDocument doc, String prefabId,
                                         float x, float y, float z) {
        PrefabInstance value = new PrefabInstance(Ids.create(), prefabId);
        value.transform.x = x;
        value.transform.y = y;
        value.transform.z = z;
        doc.prefabs.add(value);
        return value;
    }
}
