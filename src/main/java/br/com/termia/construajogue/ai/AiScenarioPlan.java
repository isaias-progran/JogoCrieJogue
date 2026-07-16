package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.util.Json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Plano seguro que a IA pode preencher. Não contém coordenadas, código,
 * URLs ou nomes de classes: o construtor local é o único que cria o mapa.
 */
public final class AiScenarioPlan {

    private static final List<String> SETTINGS = Arrays.asList(
            "city", "industrial", "laboratory", "fortress", "ruins",
            "tunnel");
    private static final List<String> SIZES = Arrays.asList(
            "compact", "medium", "large", "huge");
    private static final List<String> SKIES = Arrays.asList(
            "day", "dusk", "night");
    private static final List<String> OBJECTIVES = Arrays.asList(
            "reach_exit", "eliminate_all", "collect", "survive");
    private static final List<String> LAYOUTS = Arrays.asList(
            "single_building", "street", "courtyard", "campus", "maze",
            "hub", "scattered", "linear", "vertical", "underground");
    private static final List<String> ROUTES = Arrays.asList(
            "direct", "loop", "branching", "maze", "vertical");
    private static final List<String> WEAPONS = Arrays.asList(
            "smg", "shotgun", "rifle");
    private static final List<String> ROOM_PATTERNS = Arrays.asList(
            "open_plan", "corridor_rooms", "central_hall", "split_rooms",
            "mixed");
    private static final List<String> ROOF_STYLES = Arrays.asList(
            "flat", "slab", "partial", "open");
    private static final List<String> FEATURES = Arrays.asList(
            "streetlights", "water", "lava", "terminal_gate",
            "automatic_doors", "second_floor", "supplies", "ambush",
            "stairs", "ramps", "windows", "furniture", "indoor_lights",
            "diagonal_walls", "rooftop", "bridge");
    private static final List<String> ENEMIES = Arrays.asList(
            "drone", "mutant", "turret", "kamikaze", "boss");
    private static final List<String> ZONE_KINDS = Arrays.asList(
            "house", "apartment", "tower", "warehouse", "laboratory",
            "shop", "station", "courtyard", "plaza", "park", "ruins",
            "tunnel", "fortress");
    private static final List<String> ZONE_SIZES = Arrays.asList(
            "small", "medium", "large");
    private static final List<String> ZONE_PURPOSES = Arrays.asList(
            "start", "exploration", "combat", "puzzle", "reward", "exit");

    public String title;
    public String summary;
    public String setting;
    public String size;
    public String sky;
    public String objective;
    public String layout;
    public String route;
    public String roomPattern;
    public String roofStyle;
    public int difficulty;
    public int seed;
    public int buildingCount;
    public int floors;
    public int roomCount;
    public final List<String> features = new ArrayList<>();
    public final List<String> enemies = new ArrayList<>();
    public final List<Zone> zones = new ArrayList<>();
    public final List<String> weapons = new ArrayList<>();
    public String npcName;
    public String npcRole;
    public String npcGreeting;
    public String npcBackground;

    private AiScenarioPlan() {
    }

    /** Zona sem coordenadas: descreve intenção, nunca geometria arbitrária. */
    public static final class Zone {
        public String kind;
        public String size;
        public int floors;
        public String purpose;

        private Zone() {
        }
    }

    public static AiScenarioPlan parse(String text) {
        Object value = Json.parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("plano da IA não é um objeto");
        }
        Map<?, ?> root = (Map<?, ?>) value;
        exactKeys(root, "title", "summary", "setting", "size", "sky",
                "objective", "layout", "route", "roomPattern",
                "roofStyle", "difficulty", "seed", "buildingCount",
                "floors", "roomCount", "features", "enemies", "weapons",
                "zones", "npc");
        AiScenarioPlan plan = new AiScenarioPlan();
        plan.title = string(root, "title", 3, 48);
        plan.summary = string(root, "summary", 1, 240);
        plan.setting = choice(root, "setting", SETTINGS);
        plan.size = choice(root, "size", SIZES);
        plan.sky = choice(root, "sky", SKIES);
        plan.objective = choice(root, "objective", OBJECTIVES);
        plan.layout = choice(root, "layout", LAYOUTS);
        plan.route = choice(root, "route", ROUTES);
        plan.roomPattern = choice(root, "roomPattern", ROOM_PATTERNS);
        plan.roofStyle = choice(root, "roofStyle", ROOF_STYLES);
        plan.difficulty = integer(root, "difficulty", 1, 3);
        plan.seed = integer(root, "seed", 1, Integer.MAX_VALUE);
        plan.buildingCount = integer(root, "buildingCount", 1, 8);
        plan.floors = integer(root, "floors", 1, 3);
        plan.roomCount = integer(root, "roomCount", 1, 12);
        strings(root, "features", FEATURES, 1, 16, plan.features);
        strings(root, "enemies", ENEMIES, 1, 5, plan.enemies);
        strings(root, "weapons", WEAPONS, 0, 3, plan.weapons);
        zones(root, plan.zones);
        Object npcValue = root.get("npc");
        if (!(npcValue instanceof Map)) {
            throw new IllegalArgumentException("plano da IA sem NPC");
        }
        Map<?, ?> npc = (Map<?, ?>) npcValue;
        exactKeys(npc, "name", "role", "greeting", "background");
        plan.npcName = string(npc, "name", 1, 48);
        plan.npcRole = string(npc, "role", 1, 80);
        plan.npcGreeting = string(npc, "greeting", 1, 240);
        plan.npcBackground = string(npc, "background", 1, 600);
        return plan;
    }

    /** JSON Schema estrito enviado no text.format da Responses API. */
    public static Object schema() {
        Map<String, Object> root = objectSchema();
        Map<String, Object> props = properties(root);
        props.put("title", stringSchema(3, 48));
        props.put("summary", stringSchema(1, 240));
        props.put("setting", enumSchema(SETTINGS));
        props.put("size", enumSchema(SIZES));
        props.put("sky", enumSchema(SKIES));
        props.put("objective", enumSchema(OBJECTIVES));
        props.put("layout", enumSchema(LAYOUTS));
        props.put("route", enumSchema(ROUTES));
        props.put("roomPattern", enumSchema(ROOM_PATTERNS));
        props.put("roofStyle", enumSchema(ROOF_STYLES));
        props.put("difficulty", integerSchema(1, 3));
        props.put("seed", integerSchema(1, Integer.MAX_VALUE));
        props.put("buildingCount", integerSchema(1, 8));
        props.put("floors", integerSchema(1, 3));
        props.put("roomCount", integerSchema(1, 12));
        props.put("features", arraySchema(FEATURES, 1, 16));
        props.put("enemies", arraySchema(ENEMIES, 1, 5));
        props.put("weapons", arraySchema(WEAPONS, 0, 3));

        Map<String, Object> zone = objectSchema();
        Map<String, Object> zoneProps = properties(zone);
        zoneProps.put("kind", enumSchema(ZONE_KINDS));
        zoneProps.put("size", enumSchema(ZONE_SIZES));
        zoneProps.put("floors", integerSchema(1, 3));
        zoneProps.put("purpose", enumSchema(ZONE_PURPOSES));
        require(zone, "kind", "size", "floors", "purpose");
        props.put("zones", objectArraySchema(zone, 1, 6));

        Map<String, Object> npc = objectSchema();
        Map<String, Object> npcProps = properties(npc);
        npcProps.put("name", stringSchema(1, 48));
        npcProps.put("role", stringSchema(1, 80));
        npcProps.put("greeting", stringSchema(1, 240));
        npcProps.put("background", stringSchema(1, 600));
        require(npc, "name", "role", "greeting", "background");
        props.put("npc", npc);
        require(root, "title", "summary", "setting", "size", "sky",
                "objective", "layout", "route", "roomPattern",
                "roofStyle", "difficulty", "seed", "buildingCount",
                "floors", "roomCount", "features", "enemies", "weapons",
                "zones", "npc");
        return root;
    }

    public boolean hasFeature(String value) {
        return features.contains(value);
    }

    public Zone primaryZone() {
        return zones.get(0);
    }

    public Zone zoneAt(int index) {
        return zones.get(Math.floorMod(index, zones.size()));
    }

    public String description() {
        int describedFloors = Math.max(floors, primaryZone().floors);
        if (hasFeature("second_floor")) {
            describedFloors = Math.max(2, describedFloors);
        }
        return summary + "\nPlanta: " + layoutLabel(layout)
                + " · rota " + routeLabel(route)
                + " · " + buildingCount + " prédio(s)"
                + " · " + roomCount + " cômodo(s)"
                + " · " + describedFloors + " andar(es)"
                + "\nTema " + setting + " · tamanho " + size + " · céu "
                + sky + " · dificuldade " + difficulty;
    }

    private static String layoutLabel(String value) {
        if ("single_building".equals(value)) return "edifício único";
        if ("street".equals(value)) return "rua com fachadas";
        if ("courtyard".equals(value)) return "pátio cercado";
        if ("campus".equals(value)) return "campus";
        if ("maze".equals(value)) return "labirinto";
        if ("hub".equals(value)) return "praça com alas";
        if ("scattered".equals(value)) return "prédios espalhados";
        if ("linear".equals(value)) return "câmaras em sequência";
        if ("vertical".equals(value)) return "exploração vertical";
        return "subterrâneo";
    }

    private static String routeLabel(String value) {
        if ("direct".equals(value)) return "direta";
        if ("loop".equals(value)) return "circular";
        if ("branching".equals(value)) return "ramificada";
        if ("maze".equals(value)) return "labiríntica";
        return "vertical";
    }

    private static String choice(Map<?, ?> map, String key,
                                 List<String> allowed) {
        String value = string(map, key, 1, 40);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(
                    "plano da IA: valor não permitido em " + key);
        }
        return value;
    }

    private static String string(Map<?, ?> map, String key,
                                 int min, int max) {
        Object value = map.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    "plano da IA: texto obrigatório em " + key);
        }
        String text = ((String) value).trim().replace('\u0000', ' ');
        if (text.length() < min || text.length() > max) {
            throw new IllegalArgumentException(
                    "plano da IA: tamanho inválido em " + key);
        }
        return text;
    }

    private static int integer(Map<?, ?> map, String key,
                               int min, int max) {
        Object value = map.get(key);
        if (!(value instanceof Json.Num)) {
            throw new IllegalArgumentException(
                    "plano da IA: inteiro obrigatório em " + key);
        }
        float raw = ((Json.Num) value).floatValue();
        int number = (int) raw;
        if (number != raw || number < min || number > max) {
            throw new IllegalArgumentException(
                    "plano da IA: inteiro fora do limite em " + key);
        }
        return number;
    }

    private static void strings(Map<?, ?> map, String key,
                                List<String> allowed, int min, int max,
                                List<String> out) {
        Object value = map.get(key);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(
                    "plano da IA: lista obrigatória em " + key);
        }
        List<?> values = (List<?>) value;
        if (values.size() < min || values.size() > max) {
            throw new IllegalArgumentException(
                    "plano da IA: quantidade inválida em " + key);
        }
        Set<String> unique = new HashSet<>();
        for (Object item : values) {
            if (!(item instanceof String) || !allowed.contains(item)
                    || !unique.add((String) item)) {
                throw new IllegalArgumentException(
                        "plano da IA: item não permitido em " + key);
            }
            out.add((String) item);
        }
    }

    private static void zones(Map<?, ?> map, List<Zone> out) {
        Object value = map.get("zones");
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(
                    "plano da IA: lista obrigatória em zones");
        }
        List<?> values = (List<?>) value;
        if (values.isEmpty() || values.size() > 6) {
            throw new IllegalArgumentException(
                    "plano da IA: quantidade inválida em zones");
        }
        for (Object item : values) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(
                        "plano da IA: zona inválida");
            }
            Map<?, ?> source = (Map<?, ?>) item;
            exactKeys(source, "kind", "size", "floors", "purpose");
            Zone zone = new Zone();
            zone.kind = choice(source, "kind", ZONE_KINDS);
            zone.size = choice(source, "size", ZONE_SIZES);
            zone.floors = integer(source, "floors", 1, 3);
            zone.purpose = choice(source, "purpose", ZONE_PURPOSES);
            out.add(zone);
        }
    }

    private static Map<String, Object> objectSchema() {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "object");
        map.put("properties", new TreeMap<String, Object>());
        map.put("additionalProperties", false);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> map) {
        return (Map<String, Object>) map.get("properties");
    }

    private static Map<String, Object> stringSchema(int min, int max) {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "string");
        // Structured Outputs estrito aceita só um subconjunto de JSON Schema.
        // O texto orienta o modelo; parse() aplica estes limites de verdade.
        map.put("description", "Texto com " + min + " a " + max
                + " caracteres.");
        return map;
    }

    private static Map<String, Object> enumSchema(List<String> values) {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "string");
        map.put("enum", new ArrayList<Object>(values));
        return map;
    }

    private static Map<String, Object> integerSchema(int min, int max) {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "integer");
        map.put("description", "Inteiro entre " + min + " e " + max
                + ". O aplicativo validará a faixa.");
        return map;
    }

    private static Map<String, Object> arraySchema(List<String> values,
                                                   int min, int max) {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "array");
        map.put("items", enumSchema(values));
        map.put("description", "Escolha de " + min + " a " + max
                + " itens distintos. O aplicativo validará a quantidade.");
        return map;
    }

    private static Map<String, Object> objectArraySchema(
            Map<String, Object> item, int min, int max) {
        Map<String, Object> map = new TreeMap<>();
        map.put("type", "array");
        map.put("items", item);
        map.put("description", "Lista com " + min + " a " + max
                + " zonas. O aplicativo validará a quantidade.");
        return map;
    }

    private static void require(Map<String, Object> map, String... names) {
        map.put("required", new ArrayList<Object>(Arrays.asList(names)));
    }

    /** Espelha additionalProperties:false mesmo se a API for simulada. */
    private static void exactKeys(Map<?, ?> map, String... names) {
        Set<String> expected = new HashSet<>(Arrays.asList(names));
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !expected.contains(key)) {
                throw new IllegalArgumentException(
                        "plano da IA contém campo não permitido");
            }
        }
        if (map.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "plano da IA está incompleto");
        }
    }
}
