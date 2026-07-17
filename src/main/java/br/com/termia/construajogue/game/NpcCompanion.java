package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.engine.Raycast;
import br.com.termia.construajogue.runtime.RuntimeNpc;

/** Seguimento e combate determinísticos locais; nunca chama a IA. */
public final class NpcCompanion {

    public static final int EV_NONE = 0;
    public static final int EV_SHOT = 1;
    public static final int EV_KILL = 2;
    public static final int EV_SPEECH = 4;
    public static final int EV_RECOVERED = 8;

    public static final int MAX_HEALTH = 60;
    public static final float COMBAT_RANGE = 14f;
    public static final float FIRE_INTERVAL = 1.20f;
    public static final float RETURN_DELAY = 1.5f;
    public static final float RECOVERY_DELAY = 8f;

    private static final float DANGER_RANGE = 18f;
    private static final float SPEED = 3.0f;
    private static final float STOP_DISTANCE = 1.65f;
    private static final float MAX_STORY_DELTA = 1.2f;
    private static final float RADIUS = 0.28f;
    private static final float HEIGHT = 1.7f;
    private static final float STEP = 0.28f;
    private static final float EYE_HEIGHT = 1.38f;
    private static final float SAME_STORY_DELTA = 2.2f;
    private static final float SPEECH_INTERVAL = 11f;

    private final RuntimeNpc npc;
    private final float startX;
    private final float startY;
    private final float startZ;
    private final float[] position = new float[3];
    private final float[] blockingBox = new float[6];
    private boolean following;
    private int health;
    private float fireTimer;
    private float clearTimer;
    private float recoveryTimer;
    private float speechTimer;
    private float aggroTimer;
    private int lineIndex;
    private String pendingSpeech;

    public NpcCompanion(RuntimeNpc npc) {
        this.npc = npc;
        startX = npc.x;
        startY = npc.y;
        startZ = npc.z;
        reset();
    }

    public void startFollowing() {
        following = true;
        fireTimer = Math.min(fireTimer, 0.35f);
    }

    public boolean following() {
        return following;
    }

    public boolean combatant() {
        return npc.combatant;
    }

    public boolean canBeTargeted() {
        return npc.combatant && following && !npc.downed;
    }

    public boolean recentlyFired() {
        return aggroTimer > 0f;
    }

    public int health() {
        return health;
    }

    public RuntimeNpc npc() {
        return npc;
    }

    public void reset() {
        following = false;
        health = MAX_HEALTH;
        fireTimer = 0.35f;
        clearTimer = RETURN_DELAY;
        recoveryTimer = 0f;
        speechTimer = 0f;
        aggroTimer = 0f;
        lineIndex = 0;
        pendingSpeech = null;
        position[0] = startX;
        position[1] = startY;
        position[2] = startZ;
        npc.x = startX;
        npc.y = startY;
        npc.z = startZ;
        npc.moving = false;
        npc.downed = false;
        npc.firing = false;
        npc.tracerTtl = 0f;
    }

    /** Compatibilidade: somente seguimento, usada pelos testes antigos. */
    public void update(float dt, float playerX, float playerY, float playerZ,
                       float[][] colliders) {
        tickTimers(dt);
        follow(dt, playerX, playerY, playerZ, colliders);
    }

    /**
     * Atualiza seguimento/combate. O retorno combina EV_*; o GameState cuida
     * de som, objetivo e fala fora desta regra pura.
     */
    public int update(float dt, float playerX, float playerY, float playerZ,
                      float[][] colliders, Enemy[] enemies,
                      NpcCompanion[] allies) {
        tickTimers(dt);
        if (dt <= 0f || !following) {
            npc.moving = false;
            return EV_NONE;
        }
        if (npc.downed) {
            npc.moving = false;
            if (dangerNearby(enemies)) {
                recoveryTimer = 0f;
                return EV_NONE;
            }
            recoveryTimer += dt;
            if (recoveryTimer < RECOVERY_DELAY) return EV_NONE;
            recoveryTimer = 0f;
            health = MAX_HEALTH / 2;
            npc.downed = false;
            clearTimer = RETURN_DELAY;
            return EV_RECOVERED;
        }
        if (!npc.combatant) {
            follow(dt, playerX, playerY, playerZ, colliders);
            return EV_NONE;
        }

        Enemy target = closestVisible(enemies, colliders, playerX, playerY,
                playerZ, allies);
        if (target == null) {
            clearTimer += dt;
            if (clearTimer >= RETURN_DELAY) {
                follow(dt, playerX, playerY, playerZ, colliders);
            } else {
                npc.moving = false;
            }
            return EV_NONE;
        }

        npc.moving = false;
        clearTimer = 0f;
        if (fireTimer > 0f) return EV_NONE;
        fireTimer = FIRE_INTERVAL;
        aggroTimer = 2.5f;
        npc.firing = true;
        npc.tracerX = target.x();
        npc.tracerY = target.y();
        npc.tracerZ = target.z();
        npc.tracerTtl = 0.12f;
        boolean killed = target.hit(colliders);
        int event = EV_SHOT | (killed ? EV_KILL : 0);
        if (speechTimer <= 0f) {
            pendingSpeech = npc.combatLines[lineIndex
                    % npc.combatLines.length];
            lineIndex++;
            speechTimer = SPEECH_INTERVAL;
            event |= EV_SPEECH;
        }
        return event;
    }

    /** true quando este dano fez o aliado desmaiar. */
    public boolean damage(int amount) {
        if (!canBeTargeted() || amount <= 0) return false;
        health = Math.max(0, health - amount);
        if (health > 0) return false;
        npc.downed = true;
        npc.moving = false;
        npc.firing = false;
        npc.tracerTtl = 0f;
        recoveryTimer = 0f;
        return true;
    }

    public String takeCombatSpeech() {
        String value = pendingSpeech;
        pendingSpeech = null;
        return value;
    }

    /**
     * Escolha local de alvo do inimigo: jogador continua sendo a referência,
     * mas um aliado mais próximo/ativo e visível pode atrair o ataque.
     */
    public static NpcCompanion targetForEnemy(Enemy enemy,
                                               float playerX, float playerY,
                                               float playerZ,
                                               NpcCompanion[] allies,
                                               float[][] colliders) {
        if (enemy == null || !enemy.targetable()) return null;
        float pdx = playerX - enemy.x();
        float pdy = playerY - enemy.y();
        float pdz = playerZ - enemy.z();
        float bestScore = pdx * pdx + pdy * pdy + pdz * pdz;
        NpcCompanion best = null;
        if (allies == null) return null;
        for (NpcCompanion companion : allies) {
            if (companion == null || !companion.canBeTargeted()) continue;
            RuntimeNpc candidate = companion.npc;
            float dx = candidate.x - enemy.x();
            float dy = candidate.y + EYE_HEIGHT - enemy.y();
            float dz = candidate.z - enemy.z();
            if (Math.abs(dy) > SAME_STORY_DELTA) continue;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.01f) return companion;
            float wall = Raycast.hitBoxes(enemy.x(), enemy.y(), enemy.z(),
                    dx / distance, dy / distance, dz / distance, colliders);
            if (wall < distance - 0.1f) continue;
            float score = distance * distance
                    * (companion.recentlyFired() ? 0.55f : 1.12f);
            if (score < bestScore) {
                bestScore = score;
                best = companion;
            }
        }
        return best;
    }

    private void tickTimers(float dt) {
        npc.firing = false;
        if (dt <= 0f) return;
        fireTimer = Math.max(0f, fireTimer - dt);
        speechTimer = Math.max(0f, speechTimer - dt);
        aggroTimer = Math.max(0f, aggroTimer - dt);
        npc.tracerTtl = Math.max(0f, npc.tracerTtl - dt);
    }

    private void follow(float dt, float playerX, float playerY, float playerZ,
                        float[][] colliders) {
        npc.moving = false;
        if (!following || npc.downed || dt <= 0f
                || Math.abs(playerY - position[1]) > MAX_STORY_DELTA) {
            return;
        }
        float dx = playerX - position[0];
        float dz = playerZ - position[2];
        float distance = (float) Math.hypot(dx, dz);
        if (distance <= STOP_DISTANCE) return;
        float move = Math.min(SPEED * dt, distance - STOP_DISTANCE);
        float beforeX = position[0];
        float beforeZ = position[2];
        Collision.moveHorizontal(position, 0, dx / distance * move,
                RADIUS, HEIGHT, STEP, colliders);
        Collision.moveHorizontal(position, 2, dz / distance * move,
                RADIUS, HEIGHT, STEP, colliders);
        npc.x = position[0];
        npc.y = position[1];
        npc.z = position[2];
        npc.moving = Math.hypot(position[0] - beforeX,
                position[2] - beforeZ) > 0.001f;
    }

    private Enemy closestVisible(Enemy[] enemies, float[][] boxes,
                                 float playerX, float playerY, float playerZ,
                                 NpcCompanion[] allies) {
        Enemy best = null;
        float bestDistance = COMBAT_RANGE * COMBAT_RANGE;
        float eyeY = npc.y + EYE_HEIGHT;
        for (Enemy enemy : enemies) {
            if (enemy == null || !enemy.targetable()
                    || Math.abs(enemy.y() - eyeY) > SAME_STORY_DELTA) {
                continue;
            }
            float dx = enemy.x() - npc.x;
            float dy = enemy.y() - eyeY;
            float dz = enemy.z() - npc.z;
            float distance2 = dx * dx + dy * dy + dz * dz;
            if (distance2 >= bestDistance || distance2 < 0.01f) continue;
            float distance = (float) Math.sqrt(distance2);
            float rx = dx / distance;
            float ry = dy / distance;
            float rz = dz / distance;
            float wall = Raycast.hitBoxes(npc.x, eyeY, npc.z,
                    rx, ry, rz, boxes);
            if (wall < distance - 0.10f) continue;
            if (blockedByPerson(distance, rx, ry, rz,
                    playerX, playerY, playerZ, allies)) continue;
            best = enemy;
            bestDistance = distance2;
        }
        return best;
    }

    private boolean blockedByPerson(float targetDistance, float dx, float dy,
                                    float dz, float playerX, float playerY,
                                    float playerZ, NpcCompanion[] allies) {
        personBounds(playerX, playerY, playerZ, 0.35f, 1.75f);
        float hit = Raycast.hitBox(npc.x, npc.y + EYE_HEIGHT, npc.z,
                dx, dy, dz, blockingBox);
        if (hit > 0.10f && hit < targetDistance - 0.10f) return true;
        if (allies == null) return false;
        for (NpcCompanion ally : allies) {
            if (ally == null || ally == this || ally.npc.downed) continue;
            personBounds(ally.npc.x, ally.npc.y, ally.npc.z,
                    RADIUS, HEIGHT);
            hit = Raycast.hitBox(npc.x, npc.y + EYE_HEIGHT, npc.z,
                    dx, dy, dz, blockingBox);
            if (hit > 0.10f && hit < targetDistance - 0.10f) return true;
        }
        return false;
    }

    private void personBounds(float x, float y, float z,
                              float radius, float height) {
        blockingBox[0] = x - radius;
        blockingBox[1] = y;
        blockingBox[2] = z - radius;
        blockingBox[3] = x + radius;
        blockingBox[4] = y + height;
        blockingBox[5] = z + radius;
    }

    private boolean dangerNearby(Enemy[] enemies) {
        float limit = DANGER_RANGE * DANGER_RANGE;
        for (Enemy enemy : enemies) {
            if (enemy == null || !enemy.targetable()) continue;
            float dx = enemy.x() - npc.x;
            float dy = enemy.y() - (npc.y + EYE_HEIGHT);
            float dz = enemy.z() - npc.z;
            if (Math.abs(dy) <= SAME_STORY_DELTA
                    && dx * dx + dy * dy + dz * dz <= limit) return true;
        }
        return false;
    }
}
