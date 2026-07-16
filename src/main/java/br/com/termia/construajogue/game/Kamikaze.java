package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.engine.Raycast;

/** Drone de contato: persegue, pulsa perto do alvo e explode uma vez. */
public final class Kamikaze implements Enemy {
    private static final float HALF = 0.34f;
    private static final float SPEED = 2.65f;
    private static final float RANGE = 15f;
    private final float ax, az, bx, bz, hoverY;
    private final float[] feet = new float[3];
    private float x, y, z, flash;
    private boolean towardB = true;
    private boolean wreck;
    private boolean armed;

    public Kamikaze(float[] spawn) {
        ax = x = spawn[0];
        hoverY = y = spawn[1];
        az = z = spawn[2];
        bx = spawn[3];
        bz = spawn[4];
    }

    @Override public void wake() { armed = true; }

    @Override
    public int update(float dt, float time, float[][] boxes,
                      float px, float py, float pz, boolean playerAlive) {
        if (flash > 0f) flash -= dt;
        if (wreck) return EV_NONE;
        y = hoverY + 0.08f * (float) Math.sin(time * 4f + ax);
        float dx = px - x;
        float dy = py - y;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean sees = playerAlive && dist < RANGE
                && lineOfSight(px, py, pz, boxes);
        armed = armed || sees;
        if (armed && playerAlive) {
            if (dist < 1.05f) {
                wreck = true;
                flash = 0.2f;
                return EV_ATTACK_HIT;
            }
            moveToward(px, pz, SPEED, dt, boxes);
        } else if (moveToward(towardB ? bx : ax, towardB ? bz : az,
                1.15f, dt, boxes)) {
            towardB = !towardB;
        }
        return EV_NONE;
    }

    private void move(float dx, float dz, float[][] boxes) {
        feet[0] = x; feet[1] = y - HALF; feet[2] = z;
        Collision.moveHorizontal(feet, 0, dx, HALF, HALF * 2f, 0f, boxes);
        Collision.moveHorizontal(feet, 2, dz, HALF, HALF * 2f, 0f, boxes);
        x = feet[0]; z = feet[2];
    }

    private boolean moveToward(float tx, float tz, float speed, float dt,
                               float[][] boxes) {
        float dx = tx - x, dz = tz - z;
        float d = (float) Math.hypot(dx, dz);
        if (d < 0.15f) return true;
        move(dx / d * speed * dt, dz / d * speed * dt, boxes);
        return false;
    }

    private boolean lineOfSight(float px, float py, float pz,
                                float[][] boxes) {
        float dx = px - x, dy = py - y, dz = pz - z;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.01f) return true;
        return Raycast.hitBoxes(x, y, z, dx / d, dy / d, dz / d, boxes)
                >= d - 0.1f;
    }

    @Override public boolean hit(float[][] boxes) {
        if (wreck) return false;
        wreck = true;
        flash = 0.2f;
        return true;
    }
    @Override public boolean targetable() { return !wreck; }
    @Override public boolean dormant() { return false; }
    @Override public boolean wreck() { return wreck; }
    @Override public boolean flashing() { return flash > 0f; }
    @Override public boolean telegraphing() { return armed && !wreck; }
    @Override public void boundsInto(float[] o) {
        o[0]=x-HALF; o[1]=y-HALF; o[2]=z-HALF;
        o[3]=x+HALF; o[4]=y+HALF; o[5]=z+HALF;
    }
    @Override public int type() { return TYPE_KAMIKAZE; }
    @Override public int damage() { return 40; }
    @Override public float x() { return x; }
    @Override public float y() { return y; }
    @Override public float z() { return z; }
}
