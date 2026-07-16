package br.com.termia.construajogue.game;

/**
 * Ficha imutável de cada arma. A pistola é a arma inicial; as demais
 * entram no mapa como pickups (catálogo/IA) e substituem a atual.
 * Balance: dano 3 derruba drone (3 pv) num tiro; dano 4, o mutante (4 pv).
 */
public final class WeaponSpec {

    public static final WeaponSpec PISTOL = new WeaponSpec(
            "pistol", "PISTOLA", 12, 48, 0.32f, 1.1f, 1);
    public static final WeaponSpec SMG = new WeaponSpec(
            "smg", "METRALHADORA", 30, 60, 0.11f, 1.6f, 1);
    public static final WeaponSpec SHOTGUN = new WeaponSpec(
            "shotgun", "ESCOPETA", 6, 18, 0.85f, 1.9f, 3);
    public static final WeaponSpec RIFLE = new WeaponSpec(
            "rifle", "RIFLE", 5, 15, 1.05f, 1.4f, 4);

    public final String id;
    public final String label;
    public final int magSize;
    public final int startReserve;
    public final float cooldown;
    public final float reloadTime;
    public final int damage;

    private WeaponSpec(String id, String label, int magSize,
                       int startReserve, float cooldown, float reloadTime,
                       int damage) {
        this.id = id;
        this.label = label;
        this.magSize = magSize;
        this.startReserve = startReserve;
        this.cooldown = cooldown;
        this.reloadTime = reloadTime;
        this.damage = damage;
    }

    /** Nulo quando o id não é uma arma conhecida. */
    public static WeaponSpec byId(String id) {
        if (PISTOL.id.equals(id)) return PISTOL;
        if (SMG.id.equals(id)) return SMG;
        if (SHOTGUN.id.equals(id)) return SHOTGUN;
        if (RIFLE.id.equals(id)) return RIFLE;
        return null;
    }
}
