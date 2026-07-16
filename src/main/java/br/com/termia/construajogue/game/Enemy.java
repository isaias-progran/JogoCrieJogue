package br.com.termia.construajogue.game;

/** Contrato mínimo compartilhado por drones voadores e mutantes terrestres. */
public interface Enemy {

    int TYPE_DRONE = 0;
    int TYPE_MUTANT = 1;
    int TYPE_TURRET = 2;
    int TYPE_KAMIKAZE = 3;
    int TYPE_BOSS = 4;

    int EV_NONE = 0;
    int EV_ATTACK_HIT = 1;
    int EV_ATTACK_MISS = 2;

    void wake();

    int update(float dt, float time, float[][] boxes,
               float px, float py, float pz, boolean playerAlive);

    boolean hit(float[][] sceneBoxes);

    boolean targetable();

    boolean dormant();

    boolean wreck();

    boolean flashing();

    boolean telegraphing();

    void boundsInto(float[] out6);

    int type();

    int damage();

    float x();

    float y();

    float z();
}
