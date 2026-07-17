package br.com.termia.construajogue.runtime;

/** Pessoa amigável compilada do mapa, sem acesso a código ou ao sistema. */
public final class RuntimeNpc {

    public final String id;
    public final String name;
    public final String role;
    public final String greeting;
    public final String background;
    public final boolean combatant;
    public final String[] combatLines;
    public float x;
    public float y;
    public float z;
    public final float yaw;
    public boolean moving;
    public boolean downed;
    public boolean firing;
    /** Ponto final e duração do traçador do último tiro. */
    public float tracerX;
    public float tracerY;
    public float tracerZ;
    public float tracerTtl;

    public RuntimeNpc(String id, String name, String role, String greeting,
                      String background, float x, float y, float z,
                      float yaw) {
        this(id, name, role, greeting, background, false, null,
                x, y, z, yaw);
    }

    public RuntimeNpc(String id, String name, String role, String greeting,
                      String background, boolean combatant,
                      String[] combatLines, float x, float y, float z,
                      float yaw) {
        this.id = id;
        this.name = safe(name, "Morador", 48);
        this.role = safe(role, "habitante", 80);
        this.greeting = safe(greeting, "E aí, beleza? Bora nessa.", 240);
        this.background = safe(background,
                "Conhece esta região e tenta ajudar quem passa.", 600);
        this.combatant = combatant;
        this.combatLines = lines(combatLines);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    private static String[] lines(String[] values) {
        String[] defaults = {"Cobre a esquerda!", "Alvo à frente!",
                "Tô contigo!"};
        String[] result = new String[3];
        for (int i = 0; i < result.length; i++) {
            String value = values != null && i < values.length
                    ? values[i] : null;
            result[i] = safe(value, defaults[i], 120);
        }
        return result;
    }

    private static String safe(String value, String fallback, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) text = fallback;
        text = text.replace('\u0000', ' ');
        return text.length() <= max ? text : text.substring(0, max);
    }
}
