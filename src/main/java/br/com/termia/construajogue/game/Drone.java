package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.engine.Raycast;

/**
 * Drone com a máquina de estados do PLANO.md seção 10:
 * DORMANT (desperta no alarme do terminal) → WAKING → PATROL ⇄ CHASE ⇄
 * ATTACK ⇄ SEARCH, e FALLING → WRECK ao morrer. A visão usa distância +
 * raio contra o cenário (o portão fechado bloqueia visão dos dois lados).
 * O disparo tem aviso: o drone pulsa laranja durante o TELEGRAPH antes de
 * atirar. Quem aplica o dano ao jogador é o GameState, a partir dos
 * eventos devolvidos por update(). Sem alocações no update.
 */
public final class Drone implements Enemy {

    public static final float HALF_X = 0.45f;
    public static final float HALF_Y = 0.22f;
    public static final float HALF_Z = 0.45f;

    private static final int DORMANT = 0;
    private static final int WAKING = 1;
    private static final int PATROL = 2;
    private static final int CHASE = 3;
    private static final int ATTACK = 4;
    private static final int SEARCH = 5;
    private static final int FALLING = 6;
    private static final int WRECK = 7;

    private static final int MAX_HEALTH = 3;
    private static final float PATROL_SPEED = 1.2f;
    private static final float CHASE_SPEED = 1.9f;
    private static final float SIGHT_RANGE = 12f;
    private static final float ATTACK_RANGE = 5.5f;
    private static final float ATTACK_FAR = 7f;
    private static final float FIRE_INTERVAL = 1.5f;
    private static final float TELEGRAPH = 0.45f;
    private static final float SEARCH_TIME = 2.5f;
    private static final float WAKE_SPEED = 1.6f;
    private static final float PARK_Y = 0.26f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float FLASH_TIME = 0.12f;

    private final float ax;
    private final float az;
    private final float bx;
    private final float bz;
    private final float hoverY;
    private final float phase;
    private final boolean boss;
    private final float sizeScale;
    private final float speedScale;
    private final int damageValue;
    private final float[] feet = new float[3];

    private float x;
    private float y;
    private float z;
    private boolean towardB = true;
    private int state;
    private int health;
    private float flash;
    private float fireTimer;
    private float searchTimer;
    private float lastSeenX;
    private float lastSeenZ;
    private float fallVelocity;
    private float restY;

    /** spawn = {x, y, z, x2, z2}; dormente = parado no chão até o alarme. */
    public Drone(float[] spawn, int index, boolean dormant) {
        this(spawn, index, dormant, false);
    }

    /** Variante chefe: maior, mais resistente, rápida e perigosa. */
    public Drone(float[] spawn, int index, boolean dormant, boolean boss) {
        ax = spawn[0];
        az = spawn[2];
        bx = spawn[3];
        bz = spawn[4];
        hoverY = spawn[1];
        phase = index * 2.1f;
        this.boss = boss;
        sizeScale = boss ? 1.7f : 1f;
        speedScale = boss ? 1.22f : 1f;
        damageValue = boss ? 18 : 8;
        health = boss ? 12 : MAX_HEALTH;
        x = ax;
        z = az;
        state = dormant ? DORMANT : PATROL;
        y = dormant ? PARK_Y : hoverY;
    }

    /** Alarme do terminal: drones dormentes sobem e entram em patrulha. */
    public void wake() {
        if (state == DORMANT) {
            state = WAKING;
        }
    }

    /**
     * Um passo de IA. (px,py,pz) = olho do jogador. Devolve EV_ATTACK_* quando
     * o drone dispara neste quadro (HIT = jogador estava na linha de visão).
     */
    public int update(float dt, float time, float[][] boxes,
                      float px, float py, float pz, boolean playerAlive) {
        if (flash > 0f) {
            flash -= dt;
        }
        switch (state) {
            case DORMANT:
            case WRECK:
                return EV_NONE;
            case WAKING:
                y += WAKE_SPEED * dt;
                if (y >= hoverY) {
                    y = hoverY;
                    state = PATROL;
                }
                return EV_NONE;
            case FALLING:
                fallVelocity -= 20f * dt;
                y += fallVelocity * dt;
                if (y <= restY) {
                    y = restY;
                    state = WRECK;
                }
                return EV_NONE;
            default:
                break;
        }

        y = hoverY + BOB_AMPLITUDE * (float) Math.sin(time * 2.0 + phase);
        float dx = px - x;
        float dy = py - y;
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
                float tx = towardB ? bx : ax;
                float tz = towardB ? bz : az;
                if (moveToward(tx, tz, PATROL_SPEED * speedScale, dt, boxes)) {
                    towardB = !towardB;
                }
                if (sees) {
                    state = CHASE;
                    fireTimer = FIRE_INTERVAL;
                }
                break;
            case CHASE:
                if (!sees) {
                    state = SEARCH;
                    searchTimer = SEARCH_TIME;
                } else if (dist < ATTACK_RANGE) {
                    state = ATTACK;
                } else {
                    moveToward(px, pz, CHASE_SPEED * speedScale, dt, boxes);
                }
                break;
            case ATTACK:
                if (!sees || dist > ATTACK_FAR) {
                    state = sees ? CHASE : SEARCH;
                    searchTimer = SEARCH_TIME;
                    fireTimer = FIRE_INTERVAL;
                    break;
                }
                fireTimer -= dt;
                if (fireTimer <= 0f) {
                    fireTimer = FIRE_INTERVAL;
                    flash = 0.08f; // clarão do próprio disparo
                    return sees ? EV_ATTACK_HIT : EV_ATTACK_MISS;
                }
                break;
            case SEARCH:
                if (sees) {
                    state = CHASE;
                    break;
                }
                boolean arrived = moveToward(lastSeenX, lastSeenZ,
                        CHASE_SPEED * speedScale, dt, boxes);
                if (arrived) {
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

    /** Move no plano XZ com colisão contra o cenário; true = chegou. */
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
        feet[1] = y - HALF_Y * sizeScale;
        feet[2] = z;
        Collision.moveHorizontal(feet, 0, dx * scale, HALF_X * sizeScale,
                2 * HALF_Y * sizeScale, 0f, boxes);
        Collision.moveHorizontal(feet, 2, dz * scale, HALF_X * sizeScale,
                2 * HALF_Y * sizeScale, 0f, boxes);
        x = feet[0];
        z = feet[2];
        return false;
    }

    private boolean lineOfSight(float px, float py, float pz,
                                float[][] boxes) {
        float dx = px - x;
        float dy = py - y;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01f) {
            return true;
        }
        float t = Raycast.hitBoxes(x, y, z, dx / dist, dy / dist, dz / dist,
                boxes);
        return t >= dist - 0.1f;
    }

    /** Aplica 1 de dano; true = morreu agora (começa a cair). */
    public boolean hit(float[][] sceneBoxes) {
        if (!targetable()) {
            return false;
        }
        flash = FLASH_TIME;
        health--;
        if (state == DORMANT) {
            state = WAKING; // tiro acorda o drone
        }
        if (health > 0) {
            return false;
        }
        state = FALLING;
        fallVelocity = 0f;
        // onde este drone repousa: raio p/ baixo acha o piso local
        float t = Raycast.hitBoxes(x, y, z, 0f, -1f, 0f, sceneBoxes);
        restY = t == Raycast.MISS ? HALF_Y * sizeScale
                : y - t + HALF_Y * sizeScale;
        return true;
    }

    public boolean targetable() {
        return state != FALLING && state != WRECK;
    }

    public boolean dormant() {
        return state == DORMANT;
    }

    public boolean wreck() {
        return state == WRECK;
    }

    public boolean flashing() {
        return flash > 0f;
    }

    /** Pulso de aviso antes do disparo (tinta laranja no renderer). */
    public boolean telegraphing() {
        return state == ATTACK && fireTimer < TELEGRAPH;
    }

    @Override
    public int type() {
        return boss ? TYPE_BOSS : TYPE_DRONE;
    }

    @Override
    public int damage() {
        return damageValue;
    }

    /** AABB atual para o hitscan. */
    public void boundsInto(float[] out6) {
        out6[0] = x - HALF_X * sizeScale;
        out6[1] = y - HALF_Y * sizeScale;
        out6[2] = z - HALF_Z * sizeScale;
        out6[3] = x + HALF_X * sizeScale;
        out6[4] = y + HALF_Y * sizeScale;
        out6[5] = z + HALF_Z * sizeScale;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }
}
