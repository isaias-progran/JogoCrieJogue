package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Raycast;

/** Torreta fixa: mira por linha de visão, avisa e dispara em rajadas. */
public final class Turret implements Enemy {
    private static final float HALF_X = 0.42f;
    private static final float HALF_Y = 0.55f;
    private static final float HALF_Z = 0.42f;
    private static final float RANGE = 16f;
    private static final float INTERVAL = 1.8f;
    private static final float TELEGRAPH = 0.55f;

    private final float x;
    private final float y;
    private final float z;
    private int health = 5;
    private float fireTimer = 0.8f;
    private float flash;
    private boolean wreck;

    public Turret(float[] spawn) {
        x = spawn[0];
        y = spawn[1];
        z = spawn[2];
    }

    @Override public void wake() {}

    @Override
    public int update(float dt, float time, float[][] boxes,
                      float px, float py, float pz, boolean playerAlive) {
        if (flash > 0f) flash -= dt;
        if (wreck || !playerAlive) return EV_NONE;
        float dx = px - x;
        float dy = py - y;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean sees = dist < RANGE && lineOfSight(px, py, pz, dist, boxes);
        if (!sees) {
            fireTimer = Math.min(INTERVAL, fireTimer + dt * 0.5f);
            return EV_NONE;
        }
        fireTimer -= dt;
        if (fireTimer <= 0f) {
            fireTimer = INTERVAL;
            flash = 0.1f;
            return EV_ATTACK_HIT;
        }
        return EV_NONE;
    }

    private boolean lineOfSight(float px, float py, float pz, float dist,
                                float[][] boxes) {
        float dx = px - x;
        float dy = py - (y + 0.2f);
        float dz = pz - z;
        float rayLength = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (rayLength < 0.01f) return true;
        float t = Raycast.hitBoxes(x, y + 0.2f, z,
                dx / rayLength, dy / rayLength, dz / rayLength, boxes);
        return t >= rayLength - 0.1f;
    }

    @Override
    public boolean hit(float[][] sceneBoxes) {
        if (!targetable()) return false;
        flash = 0.12f;
        health--;
        wreck = health <= 0;
        return wreck;
    }

    @Override public boolean targetable() { return !wreck; }
    @Override public boolean dormant() { return false; }
    @Override public boolean wreck() { return wreck; }
    @Override public boolean flashing() { return flash > 0f; }
    @Override public boolean telegraphing() {
        return !wreck && fireTimer < TELEGRAPH;
    }
    @Override public void boundsInto(float[] out) {
        out[0] = x - HALF_X; out[1] = y - HALF_Y; out[2] = z - HALF_Z;
        out[3] = x + HALF_X; out[4] = y + HALF_Y; out[5] = z + HALF_Z;
    }
    @Override public int type() { return TYPE_TURRET; }
    @Override public int damage() { return 12; }
    @Override public float x() { return x; }
    @Override public float y() { return y; }
    @Override public float z() { return z; }
}
