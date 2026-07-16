package br.com.termia.construajogue.map;

/**
 * Vão recortado numa parede (porta, portal ou janela). Pertence à
 * StructureObject da parede e anda junto com ela: `offset` é a posição
 * do centro do vão ao longo do eixo comprido, relativa ao centro da
 * parede. O recorte real acontece no LevelCompiler.
 */
public final class WallOpening {

    public static final String DOOR = "door";
    public static final String PORTAL = "portal";
    public static final String WINDOW = "window";

    public String id;
    public String type;
    public float offset;
    public float width;
    public float height;
    /** Altura do peitoril (0 = vai até o chão). */
    public float sill;
    /** Trava persistente do editor. */
    public boolean locked;

    public WallOpening() {
    }

    public WallOpening(String id, String type) {
        this.id = id;
        this.type = type;
    }
}
