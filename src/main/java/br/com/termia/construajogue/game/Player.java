package br.com.termia.construajogue.game;

import br.com.termia.construajogue.engine.Collision;
import br.com.termia.construajogue.engine.FpsCamera;
import br.com.termia.construajogue.input.TouchControls;

/**
 * Estado físico do jogador: posição dos PÉS, velocidade vertical e chão.
 * Atualizado na thread GL a cada quadro; lê o TouchControls (thread-safe)
 * e alimenta a FpsCamera. Sem alocações no update.
 */
public final class Player {

    private static final float SPEED = 3.6f;
    private static final float GRAVITY = -20f;
    private static final float MAX_FALL = -25f;
    /** 6.6 m/s ≈ 1,09m de pulo: sobe um caixote, não a pilha de dois. */
    private static final float JUMP_SPEED = 6.6f;
    private static final float RADIUS = 0.35f;
    private static final float HEIGHT = 1.75f;
    private static final float EYE_HEIGHT = 1.6f;
    private static final float STEP = 0.35f;

    public static final int MAX_HEALTH = 100;

    private final float[] pos = new float[3];
    private float velocityY;
    private boolean grounded;
    private int health = MAX_HEALTH;

    /** spawn = {x, y, z, yaw em graus}. */
    public void reset(float[] spawn, FpsCamera camera) {
        pos[0] = spawn[0];
        pos[1] = spawn[1];
        pos[2] = spawn[2];
        velocityY = 0f;
        grounded = false;
        health = MAX_HEALTH;
        camera.reset((float) Math.toRadians(spawn[3]));
        camera.setEye(pos[0], pos[1] + EYE_HEIGHT, pos[2]);
    }

    public void update(float dt, TouchControls controls, Level level,
                       FpsCamera camera) {
        camera.rotate(controls.takeLookYaw(), controls.takeLookPitch());

        float jx = controls.moveX();
        float jz = controls.moveZ();
        if (jx != 0f || jz != 0f) {
            float yaw = camera.yaw();
            float sin = (float) Math.sin(yaw);
            float cos = (float) Math.cos(yaw);
            // frente = (sin, 0, -cos); direita = (cos, 0, sin)
            float dx = (sin * jz + cos * jx) * SPEED * dt;
            float dz = (-cos * jz + sin * jx) * SPEED * dt;
            float[][] boxes = level.colliders();
            Collision.moveHorizontal(pos, 0, dx, RADIUS, HEIGHT, STEP, boxes);
            Collision.moveHorizontal(pos, 2, dz, RADIUS, HEIGHT, STEP, boxes);
        }

        if (controls.takeJump() && grounded) {
            velocityY = JUMP_SPEED;
        }
        velocityY += GRAVITY * dt;
        if (velocityY < MAX_FALL) {
            velocityY = MAX_FALL;
        }
        int hit = Collision.moveVertical(pos, velocityY * dt, RADIUS, HEIGHT,
                level.colliders());
        if (hit != 0) {
            velocityY = 0f;
        }
        grounded = hit == 1;

        camera.setEye(pos[0], pos[1] + EYE_HEIGHT, pos[2]);
    }

    public boolean grounded() {
        return grounded;
    }

    /** Posição do olho (alvo da visão e dos tiros dos drones). */
    public void eyeInto(float[] out3) {
        out3[0] = pos[0];
        out3[1] = pos[1] + EYE_HEIGHT;
        out3[2] = pos[2];
    }

    public float x() {
        return pos[0];
    }

    public float y() {
        return pos[1];
    }

    public float z() {
        return pos[2];
    }

    public void damage(int amount) {
        health = Math.max(0, health - amount);
    }

    public void heal(int amount) {
        health = Math.min(MAX_HEALTH, health + amount);
    }

    public int health() {
        return health;
    }

    public boolean alive() {
        return health > 0;
    }
}
