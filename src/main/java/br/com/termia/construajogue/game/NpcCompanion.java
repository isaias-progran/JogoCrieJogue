package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.runtime.RuntimeNpc;

/** Seguimento determinístico local; não chama IA e não executa comandos. */
public final class NpcCompanion {

    private static final float SPEED = 3.0f;
    private static final float STOP_DISTANCE = 1.65f;
    private static final float MAX_STORY_DELTA = 1.2f;
    private static final float RADIUS = 0.28f;
    private static final float HEIGHT = 1.7f;
    private static final float STEP = 0.28f;

    private final RuntimeNpc npc;
    private final float startX;
    private final float startY;
    private final float startZ;
    private final float[] position = new float[3];
    private boolean following;

    public NpcCompanion(RuntimeNpc npc) {
        this.npc = npc;
        startX = npc.x;
        startY = npc.y;
        startZ = npc.z;
        reset();
    }

    public void startFollowing() {
        following = true;
    }

    public boolean following() {
        return following;
    }

    public void reset() {
        following = false;
        position[0] = startX;
        position[1] = startY;
        position[2] = startZ;
        npc.x = startX;
        npc.y = startY;
        npc.z = startZ;
        npc.moving = false;
    }

    public void update(float dt, float playerX, float playerY, float playerZ,
                       float[][] colliders) {
        npc.moving = false;
        if (!following || dt <= 0f
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
}
