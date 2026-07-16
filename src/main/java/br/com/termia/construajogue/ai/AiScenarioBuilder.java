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
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, AiGeometry.groundMaterial(plan),
                0f, -0.15f, 0f, half, 0.15f, half, AiGeometry.groundColor(plan));
        perimeter(doc, half, AiGeometry.wallMaterial(plan), AiGeometry.wallColor(plan));

        buildTopology(doc, plan, profile, random, sector);

        if (plan.hasFeature("terminal_gate")) {
            terminalGate(doc, isFocalLayout(plan)
                    ? Math.min(half, AiFocalRecipes.houseHalfX(plan, profile) - 0.3f)
                    : half);
        }
        if (plan.hasFeature("streetlights")
                && !AiGeometry.isUnderground(plan)) {
            streetLights(doc, half, profile.rows() * 2);
        }
        // Em campanha os perigos alternam entre setores em vez de clonar.
        if (plan.hasFeature("water")
                && (totalSectors == 1 || sector % 2 == 0)) {
            AiGeometry.hazard(doc, "water", AiGeometry.isUnderground(plan)
                    ? -2.25f : -half + 3.2f, -half * 0.25f);
        }
        if (plan.hasFeature("lava")
                && (totalSectors == 1 || sector % 2 == 1)) {
            AiGeometry.hazard(doc, "lava", AiGeometry.isUnderground(plan)
                    ? 2.25f : half - 3.2f, -half * 0.35f);
        }
        if (plan.hasFeature("second_floor")
                && !AiGeometry.isUnderground(plan) && !isFocalLayout(plan)) {
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
        if (AiGeometry.isUnderground(plan)) {
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





    private static void perimeter(MapDocument doc, float half,
                                  String material, float[] color) {
        AiGeometry.wall(doc, 0f, 1.5f, -half, half, 0.15f, material, color, false);
        AiGeometry.wall(doc, 0f, 1.5f, half, half, 0.15f, material, color, false);
        AiGeometry.wall(doc, -half, 1.5f, 0f, 0.15f, half, material, color, false);
        AiGeometry.wall(doc, half, 1.5f, 0f, 0.15f, half, material, color, false);
    }

    /** A implantação vem do plano; o tema passa a definir só acabamento. */
    private static void buildTopology(MapDocument doc, AiScenarioPlan plan,
                                      AiScenarioProfile profile,
                                      Random random, int sector) {
        AiScenarioPlan.Zone zone = plan.zoneAt(sector);
        if (AiGeometry.isUnderground(plan) || "tunnel".equals(zone.kind)) {
            AiFocalRecipes.buildTunnel(doc, plan, profile, random);
            return;
        }
        String layout = layoutForZone(plan.layout, zone);
        String theme = plan.setting;
        if (sector > 0) {
            // Cada setor é um cenário: o kind da zone comanda a planta e o
            // tema do distrito; só o 1º setor segue o layout global à risca.
            String district = kindLayout(zone.kind);
            if (district != null) layout = district;
            theme = themeForKind(zone.kind, plan.setting);
            boolean repeated = layout.equals(
                    layoutForZone(plan.layout, plan.zoneAt(0)))
                    && theme.equals(plan.setting);
            if (repeated) {
                layout = rotatedLayout(layout, sector);
            }
        }
        if (sector >= plan.zones.size()) {
            layout = rotatedLayout(layout, sector / plan.zones.size());
        }
        if ("single_building".equals(layout)
                || "vertical".equals(layout)) {
            AiFocalRecipes.buildFocalBuilding(doc, plan, profile, zone);
        } else if ("courtyard".equals(layout)) {
            AiPlaceRecipes.buildCourtyard(doc, plan, profile, random);
        } else if ("campus".equals(layout)
                || "scattered".equals(layout)) {
            AiPlaceRecipes.buildCampus(doc, plan, profile, random,
                    "scattered".equals(layout));
        } else if ("maze".equals(layout)) {
            AiPlaceRecipes.buildMaze(doc, plan, profile, random);
        } else if ("hub".equals(layout)) {
            AiPlaceRecipes.buildHub(doc, plan, profile, random);
        } else if ("linear".equals(layout)) {
            AiPlaceRecipes.buildLinear(doc, plan, profile, random);
        } else {
            AiCityRecipes.buildThemedStreet(doc, plan, profile, random,
                    theme);
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

    /** Planta natural de cada kind de zone; null = segue o layout global. */
    private static String kindLayout(String kind) {
        if ("house".equals(kind) || "apartment".equals(kind)
                || "tower".equals(kind)) return "single_building";
        if ("courtyard".equals(kind) || "park".equals(kind)) {
            return "courtyard";
        }
        if ("plaza".equals(kind)) return "hub";
        if ("shop".equals(kind) || "station".equals(kind)
                || "warehouse".equals(kind) || "laboratory".equals(kind)
                || "fortress".equals(kind) || "ruins".equals(kind)) {
            return "street";
        }
        return null;
    }

    /** Tema do distrito: galpão vira indústria mesmo em campanha de cidade. */
    private static String themeForKind(String kind, String setting) {
        if ("warehouse".equals(kind)) return "industrial";
        if ("laboratory".equals(kind)) return "laboratory";
        if ("fortress".equals(kind)) return "fortress";
        if ("ruins".equals(kind)) return "ruins";
        if ("shop".equals(kind) || "station".equals(kind)) return "city";
        return setting;
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
















    /** Desenha no piso a lógica de percurso sem entregar coordenadas à IA. */
    private static void routeAccent(MapDocument doc, AiScenarioPlan plan,
                                    float half) {
        float reach = Math.max(3.5f, half - 5.2f);
        float[] color = new float[]{0.28f, 0.31f, 0.36f};
        if ("loop".equals(plan.route)) {
            float span = Math.min(half * 0.38f, 9f);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    -span, 0.015f, 0f, 0.75f, 0.015f, span, color);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    span, 0.015f, 0f, 0.75f, 0.015f, span, color);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, -span, span, 0.015f, 0.75f, color);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, span, span, 0.015f, 0.75f, color);
        } else if ("branching".equals(plan.route)) {
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, 0f, 0.85f, 0.015f, reach, color);
            float z = -Math.min(half * 0.26f, 7f);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, z, Math.min(half * 0.48f, 11f),
                    0.015f, 0.72f, color);
            z = Math.min(half * 0.20f, 5f);
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, z, Math.min(half * 0.36f, 8f),
                    0.015f, 0.72f, color);
        } else if ("direct".equals(plan.route)) {
            AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "checker",
                    0f, 0.015f, 0f, 0.9f, 0.015f, reach, color);
        }
    }

    private static void diagonalLandmarks(MapDocument doc,
                                          AiScenarioPlan plan, float half) {
        float reach = Math.min(half * 0.38f, 9f);
        AiGeometry.diagonalWall(doc, -reach, -reach * 0.55f,
                -reach * 0.28f, -reach, AiGeometry.wallMaterial(plan), AiGeometry.wallColor(plan));
        AiGeometry.diagonalWall(doc, reach, reach * 0.55f,
                reach * 0.28f, reach, AiGeometry.wallMaterial(plan), AiGeometry.wallColor(plan));
    }

    private static void bridge(MapDocument doc, AiScenarioPlan plan,
                               float half) {
        float z = -Math.min(half * 0.24f, 7f);
        AiGeometry.block(doc, StructureObject.ROLE_FLOOR, "metal",
                0f, 0.85f, z, 2.1f, 0.15f, 3.2f,
                new float[]{0.36f, 0.40f, 0.46f});
        PrefabInstance front = AiGeometry.prefab(doc, "ramp.short", 0f, 0f, z + 4.3f);
        front.transform.yaw = 180f;
        AiGeometry.prefab(doc, "ramp.short", 0f, 0f, z - 4.3f);
    }

    private static boolean isFocalLayout(AiScenarioPlan plan) {
        return "single_building".equals(plan.layout)
                || "vertical".equals(plan.layout);
    }

    private static boolean isVerticalMap(AiScenarioPlan plan) {
        return isFocalLayout(plan) && AiFocalRecipes.effectiveFloors(plan) > 1;
    }






















    private static void terminalGate(MapDocument doc, float half) {
        float gap = 2.1f;
        float segmentHalf = (half - gap) * 0.5f;
        AiGeometry.wall(doc, -(half + gap) * 0.5f, 1.5f, 0f,
                segmentHalf, 0.18f, "metal", AiGeometry.DARK, false);
        AiGeometry.wall(doc, (half + gap) * 0.5f, 1.5f, 0f,
                segmentHalf, 0.18f, "metal", AiGeometry.DARK, false);
        PrefabInstance terminal = AiGeometry.prefab(doc, "terminal.wall",
                -2.5f, 1.4f, 3.2f);
        terminal.properties.put("order", 1f);
        PrefabInstance gate = AiGeometry.prefab(doc, "door.gate", 0f, 1.4f, 0f);
        gate.properties.put("halfX", 2.0f);
        gate.properties.put("halfY", 1.4f);
        gate.properties.put("halfZ", 0.18f);
        gate.properties.put("controllerId", terminal.id);
    }

    private static void sectorPortal(MapDocument doc, float half) {
        float z = -half + 3.8f;
        StructureObject frame = AiGeometry.wall(doc, 0f, 1.5f, z,
                5.2f, 0.16f, "metal", AiGeometry.DARK, true);
        frame.openings.add(AiGeometry.opening(0f, 2.35f, 2.25f));
        PrefabInstance door = AiGeometry.prefab(doc, "door.auto", 0f, 1.05f, z);
        door.properties.put("halfX", 1.10f);
        door.properties.put("halfY", 1.05f);
        door.properties.put("halfZ", 0.08f);
    }

    private static void streetLights(MapDocument doc, float half, int count) {
        for (int i = 0; i < count; i++) {
            float z = -half + 4f + (half * 2f - 8f)
                    * i / Math.max(1f, count - 1f);
            float x = (i & 1) == 0 ? -2.45f : 2.45f;
            PrefabInstance lamp = AiGeometry.prefab(doc, "prop.lamp.street", x, 0f, z);
            lamp.transform.yaw = x < 0f ? 270f : 90f;
            lamp.properties.put("lightR", 1f);
            lamp.properties.put("lightG", 0.72f);
            lamp.properties.put("lightB", 0.38f);
            lamp.properties.put("lightRadius", 8f);
            lamp.properties.put("lightOffsetY", 3.35f);
        }
    }


    private static void observationDeck(MapDocument doc, float half,
                                        AiScenarioPlan plan) {
        float x = half - 5f;
        float z = half - 8f;
        AiGeometry.block(doc, StructureObject.ROLE_CEILING, "metal",
                x, 3.15f, z, 3f, 0.15f, 3f,
                new float[]{0.38f, 0.42f, 0.48f});
        String accessId = plan.hasFeature("ramps")
                && !plan.hasFeature("stairs") ? "ramp.floor" : "stairs.floor";
        PrefabInstance stairs = AiGeometry.prefab(doc, accessId,
                x, 0f, z - 4.8f);
        stairs.transform.yaw = 0f;
        AiGeometry.prefab(doc, "pickup.special", x, 3.8f, z);
        AiGeometry.block(doc, StructureObject.ROLE_BLOCK, "metal",
                x + 2.85f, 3.75f, z, 0.12f, 0.45f, 3f, AiGeometry.DARK);
        AiGeometry.block(doc, StructureObject.ROLE_BLOCK, "metal",
                x - 2.85f, 3.75f, z, 0.12f, 0.45f, 3f, AiGeometry.DARK);
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
                ? Math.min(half - 3f, AiFocalRecipes.houseHalfZ(plan, profile) + 2.4f)
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
                int floors = AiFocalRecipes.effectiveFloors(plan);
                boolean rooftop = plan.hasFeature("rooftop");
                exit.x = rooftop ? AiFocalRecipes.houseHalfX(plan, profile) * 0.42f : 0f;
                exit.y = rooftop ? floors * 3.3f : (floors - 1) * 3.3f;
                exit.z = rooftop ? -AiFocalRecipes.houseHalfZ(plan, profile) * 0.42f
                        : -AiFocalRecipes.houseHalfZ(plan, profile) + 2.2f;
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
                ? Math.min(half - 3.8f, AiFocalRecipes.houseHalfZ(plan, profile) + 1.35f)
                : half - 5.4f;
        PrefabInstance npc = AiGeometry.prefab(doc, "npc.human",
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
                story = i % AiFocalRecipes.effectiveFloors(plan);
                float hz = AiFocalRecipes.houseHalfZ(plan, profile);
                z = -hz + 2.1f + (hz * 2f - 4.2f)
                        * ((i / AiFocalRecipes.effectiveFloors(plan)) % 4) / 3f;
                float hx = AiFocalRecipes.houseHalfX(plan, profile);
                x = ((i & 1) == 0 ? -1f : 1f)
                        * Math.min(2.8f, hx * 0.48f);
            } else {
                z = -half + 5f + (half * 2f - 13f)
                        * (i + 0.5f) / Math.max(1f, count);
                x = -2.1f + random.nextFloat() * 4.2f;
            }
            y += story * 3.3f;
            PrefabInstance enemy = AiGeometry.prefab(doc, id, x, y, z);
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
        for (int i = 0; i < plan.weapons.size(); i++) {
            // Armas extras ficam pelo caminho, afastadas do spawn.
            float z = -half * 0.35f + i * half * 0.45f;
            AiGeometry.prefab(doc, "pickup.weapon." + plan.weapons.get(i),
                    (i & 1) == 0 ? 2.2f : -2.2f, 0.5f, z);
        }
        if (ObjectiveSpec.COLLECT.equals(doc.objective.type)) {
            int count = doc.objective.target;
            for (int i = 0; i < count; i++) {
                if (isVerticalMap(plan)) {
                    int story = i % AiFocalRecipes.effectiveFloors(plan);
                    float hx = AiFocalRecipes.houseHalfX(plan, profile);
                    float hz = AiFocalRecipes.houseHalfZ(plan, profile);
                    float x = ((i & 1) == 0 ? -1f : 1f)
                            * Math.min(3f, hx * 0.50f);
                    float z = ((i / 2) % 2 == 0 ? -1f : 1f)
                            * Math.min(3.1f, hz * 0.48f);
                    AiGeometry.prefab(doc, "pickup.token", x,
                            story * 3.3f + 0.5f, z);
                } else {
                    float z = -half + 3.5f + (half * 2f - 7f)
                            * i / Math.max(1f, count - 1f);
                    AiGeometry.prefab(doc, "pickup.token",
                            (i & 1) == 0 ? -1.2f : 1.2f, 0.5f, z);
                }
            }
        }
        if (plan.hasFeature("supplies")) {
            float front = isFocalLayout(plan)
                    ? AiFocalRecipes.houseHalfZ(plan, profile) - 1.4f : half - 6.5f;
            AiGeometry.prefab(doc, "pickup.health", -1.3f, 0.5f, front);
            AiGeometry.prefab(doc, "pickup.ammo", 1.3f, 0.5f,
                    isFocalLayout(plan) ? -front : half - 8f);
        }
    }







}
