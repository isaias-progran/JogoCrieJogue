package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.engine.Raycast;

/**
 * Inimigo terrestre da Fase 2: patrulha devagar, persegue quando enxerga e
 * ataca de perto após um pulso de aviso. Tem mais vida que o drone, mas não
 * possui ataque à distância. Sem alocações dentro de update().
 */
public final class Mutant implements Enemy {

    private static final int PATROL = 0;
    private static final int CHASE = 1;
    private static final int ATTACK = 2;
    private static final int SEARCH = 3;
    private static final int FALLING = 4;
    private static final int WRECK = 5;

    private static final float HALF_X = 0.32f;
    private static final float HALF_Y = 0.85f;
    private static final float HALF_Z = 0.32f;
    private static final int MAX_HEALTH = 4;
    private static final float PATROL_SPEED = 0.8f;
    private static final float CHASE_SPEED = 1.55f;
    private static final float SIGHT_RANGE = 14f;
    private static final float ATTACK_RANGE = 1.25f;
    private static final float ATTACK_FAR = 1.75f;
    private static final float ATTACK_INTERVAL = 1.15f;
    private static final float FIRST_ATTACK = 0.55f;
    private static final float TELEGRAPH = 0.32f;
    private static final float SEARCH_TIME = 3.2f;
    private static final float FLASH_TIME = 0.12f;

    private final float ax;
    private final float az;
    private final float bx;
    private final float bz;
    private final float standingY;
    private final float[] feet = new float[3];

    private float x;
    private float y;
    private float z;
    private boolean towardB = true;
    private int state = PATROL;
    private int health = MAX_HEALTH;
    private float flash;
    private float attackTimer;
    private float searchTimer;
    private float lastSeenX;
    private float lastSeenZ;
    private float fallVelocity;
    private float restY;

    /** spawn = {x, y, z, x2, z2}; y é o centro do corpo. */
    public Mutant(float[] spawn) {
        ax = spawn[0];
        az = spawn[2];
        bx = spawn[3];
        bz = spawn[4];
        standingY = spawn[1];
        x = ax;
        y = standingY;
        z = az;
    }

    @Override
    public void wake() {
        // Mutantes do labirinto já estão ativos desde a entrada do setor.
    }

    @Override
    public int update(float dt, float time, float[][] boxes,
                      float px, float py, float pz, boolean playerAlive) {
        if (flash > 0f) {
            flash -= dt;
        }
        if (state == WRECK) {
            return EV_NONE;
        }
        if (state == FALLING) {
            fallVelocity -= 15f * dt;
            y += fallVelocity * dt;
            if (y <= restY) {
                y = restY;
                state = WRECK;
            }
            return EV_NONE;
        }

        y = standingY;
        float dx = px - x;
        float dy = py - (y + 0.55f);
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean sees = playerAlive && dist < SIGHT_RANGE
                && lineOfSight(px, py, pz, boxes);
        if (sees) {
            lastSeenX = px;
            lastSeenZ = pz;
        }

        switch (state) {
            case PATROL:
                if (moveToward(towardB ? bx : ax, towardB ? bz : az,
                        PATROL_SPEED, dt, boxes)) {
                    towardB = !towardB;
                }
                if (sees) {
                    state = CHASE;
                }
                break;
            case CHASE:
                if (!sees) {
                    state = SEARCH;
                    searchTimer = SEARCH_TIME;
                } else if (dist < ATTACK_RANGE) {
                    state = ATTACK;
                    attackTimer = FIRST_ATTACK;
                } else {
                    moveToward(px, pz, CHASE_SPEED, dt, boxes);
                }
                break;
            case ATTACK:
                if (!sees || dist > ATTACK_FAR) {
                    state = sees ? CHASE : SEARCH;
                    searchTimer = SEARCH_TIME;
                    break;
                }
                attackTimer -= dt;
                if (attackTimer <= 0f) {
                    attackTimer = ATTACK_INTERVAL;
                    flash = 0.08f;
                    return sees && dist < ATTACK_FAR
                            ? EV_ATTACK_HIT : EV_ATTACK_MISS;
                }
                break;
            case SEARCH:
                if (sees) {
                    state = CHASE;
                    break;
                }
                if (moveToward(lastSeenX, lastSeenZ,
                        CHASE_SPEED, dt, boxes)) {
                    searchTimer -= dt;
                    if (searchTimer <= 0f) {
                        state = PATROL;
                    }
                }
                break;
            default:
                break;
        }
        return EV_NONE;
    }

    private boolean moveToward(float tx, float tz, float speed, float dt,
                               float[][] boxes) {
        float dx = tx - x;
        float dz = tz - z;
        float dist = (float) Math.hypot(dx, dz);
        if (dist < 0.15f) {
            return true;
        }
        float scale = speed * dt / dist;
        feet[0] = x;
        feet[1] = y - HALF_Y;
        feet[2] = z;
        Collision.moveHorizontal(feet, 0, dx * scale, HALF_X, 2 * HALF_Y,
                0f, boxes);
        Collision.moveHorizontal(feet, 2, dz * scale, HALF_X, 2 * HALF_Y,
                0f, boxes);
        x = feet[0];
        z = feet[2];
        return false;
    }

    private boolean lineOfSight(float px, float py, float pz,
                                float[][] boxes) {
        float eyeY = y + 0.55f;
        float dx = px - x;
        float dy = py - eyeY;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01f) {
            return true;
        }
        float t = Raycast.hitBoxes(x, eyeY, z, dx / dist, dy / dist,
                dz / dist, boxes);
        return t >= dist - 0.1f;
    }

    @Override
    public boolean hit(float[][] sceneBoxes) {
        if (!targetable()) {
            return false;
        }
        flash = FLASH_TIME;
        health--;
        if (health > 0) {
            state = CHASE;
            return false;
        }
        state = FALLING;
        fallVelocity = 0f;
        float t = Raycast.hitBoxes(x, y, z, 0f, -1f, 0f, sceneBoxes);
        restY = t == Raycast.MISS ? 0.18f : y - t + 0.18f;
        return true;
    }

    @Override
    public boolean targetable() {
        return state != FALLING && state != WRECK;
    }

    @Override
    public boolean dormant() {
        return false;
    }

    @Override
    public boolean wreck() {
        return state == WRECK;
    }

    @Override
    public boolean flashing() {
        return flash > 0f;
    }

    @Override
    public boolean telegraphing() {
        return state == ATTACK && attackTimer < TELEGRAPH;
    }

    @Override
    public void boundsInto(float[] out6) {
        out6[0] = x - HALF_X;
        out6[1] = y - HALF_Y;
        out6[2] = z - HALF_Z;
        out6[3] = x + HALF_X;
        out6[4] = y + HALF_Y;
        out6[5] = z + HALF_Z;
    }

    @Override
    public int type() {
        return TYPE_MUTANT;
    }

    @Override
    public int damage() {
        return 14;
    }

    @Override
    public float x() {
        return x;
    }

    @Override
    public float y() {
        return y;
    }

    @Override
    public float z() {
        return z;
    }
}
