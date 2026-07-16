package br.com.termia.construajogue.game;

/**
 * Arma equipada: pente + reserva, cadência e recarga vêm da WeaponSpec.
 * Trocar de arma zera pente/reserva para os valores da nova; a munição
 * especial é independente e soma +2 de dano ao tiro que a consome.
 * Toda a lógica roda na thread GL dentro do laço de jogo; quem toca em
 * som/HUD é o GameRenderer, a partir dos retornos daqui.
 */
public final class Weapon {

    private WeaponSpec spec = WeaponSpec.PISTOL;
    private int ammo = spec.magSize;
    private int reserve = spec.startReserve;
    private int special;
    private boolean lastShotSpecial;
    private float cooldown;
    private float reloadLeft;
    private boolean reloading;

    public void reset() {
        equip(WeaponSpec.PISTOL);
        special = 0;
        lastShotSpecial = false;
    }

    /** Troca a arma na hora: pente cheio da nova, sem estado pendurado. */
    public void equip(WeaponSpec next) {
        spec = next;
        ammo = next.magSize;
        reserve = next.startReserve;
        cooldown = 0f;
        reloadLeft = 0f;
        reloading = false;
    }

    public WeaponSpec spec() {
        return spec;
    }

    public void addReserve(int rounds) {
        reserve += rounds;
    }

    public void addSpecial(int rounds) {
        special += rounds;
    }

    /** Devolve true quando a recarga TERMINOU neste quadro. */
    public boolean update(float dt) {
        if (cooldown > 0f) {
            cooldown -= dt;
        }
        if (reloading) {
            reloadLeft -= dt;
            if (reloadLeft <= 0f) {
                int wanted = spec.magSize - ammo;
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
        lastShotSpecial = special > 0;
        if (lastShotSpecial) {
            special--;
        }
        cooldown = spec.cooldown;
        return true;
    }

    /** Dano do último tiro: base da arma, +2 quando saiu bala especial. */
    public int shotDamage() {
        return spec.damage + (lastShotSpecial ? 2 : 0);
    }

    /** Tenta iniciar recarga; true = recarga começou. */
    public boolean startReload() {
        if (reloading || ammo == spec.magSize || reserve == 0) {
            return false;
        }
        reloading = true;
        reloadLeft = spec.reloadTime;
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

    public int special() { return special; }

    public boolean lastShotSpecial() { return lastShotSpecial; }
}
