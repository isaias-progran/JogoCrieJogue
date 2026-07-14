package br.com.termia.construajogue.game;

/**
 * Pistola: pente + reserva, cadência e recarga por tempo.
 * Toda a lógica roda na thread GL dentro do laço de jogo; quem toca em
 * som/HUD é o GameRenderer, a partir dos retornos daqui.
 */
public final class Weapon {

    public static final int MAG_SIZE = 12;
    private static final float FIRE_COOLDOWN = 0.32f;
    private static final float RELOAD_TIME = 1.1f;

    private int ammo = MAG_SIZE;
    private int reserve = 48;
    private float cooldown;
    private float reloadLeft;
    private boolean reloading;

    public void reset() {
        ammo = MAG_SIZE;
        reserve = 48;
        cooldown = 0f;
        reloadLeft = 0f;
        reloading = false;
    }

    public void addReserve(int rounds) {
        reserve += rounds;
    }

    /** Devolve true quando a recarga TERMINOU neste quadro. */
    public boolean update(float dt) {
        if (cooldown > 0f) {
            cooldown -= dt;
        }
        if (reloading) {
            reloadLeft -= dt;
            if (reloadLeft <= 0f) {
                int wanted = MAG_SIZE - ammo;
                int taken = Math.min(wanted, reserve);
                ammo += taken;
                reserve -= taken;
                reloading = false;
                return true;
            }
        }
        return false;
    }

    /** Tenta disparar; true = bala saiu (consome munição e arma cadência). */
    public boolean tryFire() {
        if (reloading || cooldown > 0f || ammo == 0) {
            return false;
        }
        ammo--;
        cooldown = FIRE_COOLDOWN;
        return true;
    }

    /** Tenta iniciar recarga; true = recarga começou. */
    public boolean startReload() {
        if (reloading || ammo == MAG_SIZE || reserve == 0) {
            return false;
        }
        reloading = true;
        reloadLeft = RELOAD_TIME;
        return true;
    }

    public boolean empty() {
        return ammo == 0;
    }

    public boolean reloading() {
        return reloading;
    }

    public int ammo() {
        return ammo;
    }

    public int reserve() {
        return reserve;
    }
}
