package br.com.termia.construajogue.ai;

/**
 * Orçamento local aplicado depois que a IA devolve o plano. A IA pede um
 * tamanho; o aparelho e a escolha manual decidem quanto desse pedido cabe em
 * cada setor. Coordenadas maiores quase não custam nada: os limites relevantes
 * são estruturas, peças, inimigos e quantos setores ficam ativos ao mesmo
 * tempo.
 */
public final class AiScenarioProfile {

    public static final int MODE_AUTO = 0;
    public static final int MODE_ECONOMY = 1;
    public static final int MODE_BALANCED = 2;
    public static final int MODE_LARGE = 3;
    public static final int MODE_S23 = 4;

    public static final String[] MODE_LABELS = {
            "Automático (mede o aparelho)",
            "Econômico — 28 × 28 m",
            "Equilibrado — 40 × 40 m",
            "Grande — 60 × 60 m",
            "S23 / forte — 4 setores de 88 × 88 m"
    };

    private static final long GIB = 1024L * 1024L * 1024L;

    private final String sectorSize;
    private final int sectorCount;
    private final String label;

    private AiScenarioProfile(String sectorSize, int sectorCount,
                              String label) {
        this.sectorSize = sectorSize;
        this.sectorCount = sectorCount;
        this.label = label;
    }

    /** Perfil de um único documento, usado pelo editor e pelos testes. */
    public static AiScenarioProfile single(String size) {
        String safe = knownSize(size) ? size : "medium";
        return new AiScenarioProfile(safe, 1,
                sizeLabel(safe) + " em mapa único");
    }

    /**
     * Resolve o perfil sem confiar em fabricante/modelo. totalRamBytes vem de
     * ActivityManager.MemoryInfo e heapMb de getMemoryClass().
     */
    public static AiScenarioProfile resolve(int mode, String requestedSize,
                                            long totalRamBytes, int heapMb,
                                            int processors) {
        switch (mode) {
            case MODE_ECONOMY:
                return new AiScenarioProfile("compact", 1,
                        "Econômico · 1 setor de 28 × 28 m");
            case MODE_BALANCED:
                return new AiScenarioProfile("medium", 1,
                        "Equilibrado · 1 setor de 40 × 40 m");
            case MODE_LARGE:
                return new AiScenarioProfile("large", 1,
                        "Grande · 1 setor de 60 × 60 m");
            case MODE_S23:
                return new AiScenarioProfile("huge", 4,
                        "S23/forte · 4 setores de 88 × 88 m");
            case MODE_AUTO:
            default:
                return adaptive(requestedSize, totalRamBytes, heapMb,
                        processors);
        }
    }

    private static AiScenarioProfile adaptive(String requestedSize,
                                              long totalRamBytes, int heapMb,
                                              int processors) {
        int capacity;
        if ((totalRamBytes > 0 && totalRamBytes < 4L * GIB)
                || heapMb < 192 || processors <= 4) {
            capacity = 0;
        } else if ((totalRamBytes > 0 && totalRamBytes < 6L * GIB)
                || heapMb < 256 || processors <= 6) {
            capacity = 1;
        } else {
            capacity = 2;
        }

        int requested = sizeIndex(requestedSize);
        if (requested >= 3) {
            String sectorSize = capacity == 0 ? "compact"
                    : capacity == 1 ? "medium" : "large";
            int sectors = capacity == 0 ? 2 : capacity == 1 ? 3 : 4;
            return new AiScenarioProfile(sectorSize, sectors,
                    "Automático · " + sectors + " setores "
                            + sizeLabel(sectorSize).toLowerCase());
        }
        int chosen = Math.min(Math.max(0, requested), capacity);
        String size = chosen == 0 ? "compact"
                : chosen == 1 ? "medium" : "large";
        return new AiScenarioProfile(size, 1,
                "Automático · 1 setor " + sizeLabel(size).toLowerCase());
    }

    public String sectorSize() {
        return sectorSize;
    }

    public int sectorCount() {
        return sectorCount;
    }

    public float halfSize() {
        if ("huge".equals(sectorSize)) return 44f;
        if ("large".equals(sectorSize)) return 30f;
        if ("medium".equals(sectorSize)) return 20f;
        return 14f;
    }

    public int rows() {
        if ("huge".equals(sectorSize)) return 5;
        if ("large".equals(sectorSize)) return 4;
        if ("medium".equals(sectorSize)) return 3;
        return 2;
    }

    public String description() {
        return label;
    }

    private static boolean knownSize(String size) {
        return "compact".equals(size) || "medium".equals(size)
                || "large".equals(size) || "huge".equals(size);
    }

    private static int sizeIndex(String size) {
        if ("huge".equals(size)) return 3;
        if ("large".equals(size)) return 2;
        if ("medium".equals(size)) return 1;
        return 0;
    }

    private static String sizeLabel(String size) {
        if ("huge".equals(size)) return "Gigante (88 × 88 m)";
        if ("large".equals(size)) return "Grande (60 × 60 m)";
        if ("medium".equals(size)) return "Médio (40 × 40 m)";
        return "Compacto (28 × 28 m)";
    }
}
