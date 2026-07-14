package br.com.termia.construajogue.game;

import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.engine.FpsCamera;
import br.com.termia.construajogue.engine.Raycast;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.ui.Hud;

/**
 * Orquestra uma fase da campanha: jogador, arma, inimigos, terminal, portão,
 * itens, transição, vitória e derrota. Roda inteiro na thread GL; o
 * GameRenderer só lê os getters para desenhar. Sem alocações em update().
 */
public final class GameState {

    public static final int PLAYING = 0;
    public static final int DEAD = 1;
    public static final int WON = 2;
    public static final int STAGE_CLEAR = 3;

    public static final int MAX_IMPACTS = 6;
    private static final float IMPACT_LIFE = 0.09f;
    private static final float MUZZLE_LIFE = 0.05f;
    private static final float DOOR_TIME = 2.0f;
    private static final float STAGE_TRANSITION_TIME = 1.6f;
    private static final int HEAL_AMOUNT = 40;
    private static final int AMMO_AMOUNT = 24;
    private static final float PICKUP_RANGE = 1.2f;
    private static final float INTERACT_RANGE = 2.4f;
    private static final float HINT_TIME = 7f;

    private final RuntimeLevel level;
    private final FpsCamera camera;
    private final TouchControls controls;
    private final Sounds sounds;
    private final Hud hud;
    private final int stageNumber;
    private final int totalStages;
    private final float priorTime;
    private final Player player = new Player();
    private final Weapon weapon = new Weapon();
    private final Enemy[] enemies;
    private final float[][] items; // {tipo, x, y, z, pego(0/1)}
    private final float[][] impacts = new float[MAX_IMPACTS][4]; // x,y,z,ttl
    private final float[] playerEye = new float[3];
    private final float[] rayOrigin = new float[3];
    private final float[] rayDir = new float[3];
    private final float[] tmpBounds = new float[6];

    private int state = PLAYING;
    private float runTime;
    private float hintLeft = HINT_TIME;
    private boolean terminalActive;
    private float doorProgress;
    private float recoil;
    private float muzzleTtl;
    private float transitionLeft;
    private int nextImpact;
    private boolean advancePending;
    private boolean campaignRestartPending;

    public GameState(RuntimeLevel level, FpsCamera camera, TouchControls controls,
                     Sounds sounds, Hud hud, int stageNumber,
                     int totalStages, float priorTime) {
        this.level = level;
        this.camera = camera;
        this.controls = controls;
        this.sounds = sounds;
        this.hud = hud;
        this.stageNumber = stageNumber;
        this.totalStages = totalStages;
        this.priorTime = priorTime;
        int actives = level.droneSpawns().length;
        enemies = new Enemy[actives + level.waveSpawns().length
                + level.mutantSpawns().length];
        float[][] source = level.items();
        items = new float[source.length][5];
        reset();
    }

    /** Estado inicial da partida (também usado no reinício por toque). */
    public void reset() {
        player.reset(level.spawn(), camera);
        weapon.reset();
        float[][] active = level.droneSpawns();
        float[][] wave = level.waveSpawns();
        for (int i = 0; i < active.length; i++) {
            enemies[i] = new Drone(active[i], i, false);
        }
        for (int i = 0; i < wave.length; i++) {
            enemies[active.length + i] = new Drone(wave[i],
                    active.length + i, true);
        }
        float[][] mutants = level.mutantSpawns();
        int mutantStart = active.length + wave.length;
        for (int i = 0; i < mutants.length; i++) {
            enemies[mutantStart + i] = new Mutant(mutants[i]);
        }
        float[][] source = level.items();
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, items[i], 0, 4);
            items[i][4] = 0f;
        }
        for (float[] impact : impacts) {
            impact[3] = 0f;
        }
        if (level.doorIndex() >= 0) {
            System.arraycopy(level.doorOriginal(), 0,
                    level.colliders()[level.doorIndex()], 0, 6);
        }
        state = PLAYING;
        runTime = 0f;
        hintLeft = HINT_TIME;
        terminalActive = false;
        doorProgress = 0f;
        recoil = 0f;
        muzzleTtl = 0f;
        transitionLeft = 0f;
        advancePending = false;
        campaignRestartPending = false;
        controls.setGameOver(false);
        controls.setInteractVisible(false);
        controls.takeRestart(); // descarta toque que encerrou a tela anterior
        hud.setHealth(player.health());
        hud.setAmmo(weapon.ammo(), weapon.reserve());
        hud.setReloading(false);
        hud.setBig(null);
        hud.setObjective("SETOR " + stageNumber + " — "
                + (level.terminal() == null
                ? "CHEGUE À SAÍDA" : "ATIVE O TERMINAL LARANJA"));
        hud.setHint("esquerda: andar   •   arraste TIRO: mirar   •   PULO: saltar");
    }

    public void update(float dt, float time) {
        if (state == STAGE_CLEAR) {
            // A view GL já para no background; ao voltar, conclui a transição.
            transitionLeft -= dt;
            if (transitionLeft <= 0f) {
                advancePending = true;
            }
            return;
        }
        if (state == WON) {
            if (controls.takeRestart()) {
                campaignRestartPending = true;
            }
            return;
        }
        if (state == DEAD) {
            if (controls.takeRestart()) {
                reset();
            }
            return;
        }
        if (controls.paused()) {
            return;
        }
        runTime += dt;
        if (hintLeft > 0f) {
            hintLeft -= dt;
            if (hintLeft <= 0f) {
                hud.setHint(null);
            }
        }

        player.update(dt, controls, level, camera);
        player.eyeInto(playerEye);
        updateWeapon(dt);
        updateTerminalAndDoor(dt);
        updateEnemies(dt, time);
        updateItems();
        checkExit();

        recoil *= Math.exp(-12f * dt);
        if (muzzleTtl > 0f) {
            muzzleTtl -= dt;
        }
        for (float[] impact : impacts) {
            if (impact[3] > 0f) {
                impact[3] -= dt;
            }
        }
    }

    private void updateWeapon(float dt) {
        if (controls.takeReload() && weapon.startReload()) {
            sounds.reload();
            hud.setReloading(true);
        }
        if (weapon.update(dt)) {
            hud.setReloading(false);
            hud.setAmmo(weapon.ammo(), weapon.reserve());
        }
        if (controls.firing()) {
            if (weapon.tryFire()) {
                sounds.shot();
                recoil += 0.035f;
                camera.rotate(0f, 0.002f); // coice sutil
                muzzleTtl = MUZZLE_LIFE;
                hud.setAmmo(weapon.ammo(), weapon.reserve());
                fireRay();
            } else if (weapon.empty() && weapon.startReload()) {
                sounds.reload(); // recarga automática no pente vazio
                hud.setReloading(true);
            }
        }
    }

    /** Hitscan: raio da câmera contra cenário e inimigos; o menor t vence. */
    private void fireRay() {
        camera.eyeInto(rayOrigin);
        camera.forwardInto(rayDir);
        float best = Raycast.hitBoxes(rayOrigin[0], rayOrigin[1], rayOrigin[2],
                rayDir[0], rayDir[1], rayDir[2], level.colliders());
        Enemy target = null;
        for (Enemy enemy : enemies) {
            if (!enemy.targetable()) {
                continue;
            }
            enemy.boundsInto(tmpBounds);
            float t = Raycast.hitBox(rayOrigin[0], rayOrigin[1], rayOrigin[2],
                    rayDir[0], rayDir[1], rayDir[2], tmpBounds);
            if (t < best) {
                best = t;
                target = enemy;
            }
        }
        if (best == Raycast.MISS) {
            return;
        }
        float[] impact = impacts[nextImpact];
        nextImpact = (nextImpact + 1) % MAX_IMPACTS;
        float back = Math.max(best - 0.03f, 0f);
        impact[0] = rayOrigin[0] + rayDir[0] * back;
        impact[1] = rayOrigin[1] + rayDir[1] * back;
        impact[2] = rayOrigin[2] + rayDir[2] * back;
        impact[3] = IMPACT_LIFE;
        if (target != null) {
            hud.showHitMarker();
            if (target.hit(level.colliders())) {
                sounds.boom();
            } else {
                sounds.hit();
            }
        }
    }

    private void updateTerminalAndDoor(float dt) {
        float[] terminal = level.terminal();
        if (terminal != null && !terminalActive) {
            float dist = (float) Math.hypot(terminal[0] - player.x(),
                    terminal[2] - player.z());
            controls.setInteractVisible(dist < INTERACT_RANGE);
            if (dist < INTERACT_RANGE && controls.takeInteract()) {
                terminalActive = true;
                controls.setInteractVisible(false);
                sounds.chime();
                sounds.door();
                for (Enemy enemy : enemies) {
                    enemy.wake(); // o alarme desperta a onda de drones
                }
                hud.setObjective("PORTÃO ABERTO — FUJA!");
            }
        }
        if (terminalActive && doorProgress < 1f && level.doorIndex() >= 0) {
            doorProgress = Math.min(1f, doorProgress + dt / DOOR_TIME);
            float[] original = level.doorOriginal();
            float[] collider = level.colliders()[level.doorIndex()];
            float drop = doorProgress * (original[4] - original[1]);
            collider[1] = original[1] - drop;
            collider[4] = original[4] - drop;
        }
    }

    private void updateEnemies(float dt, float time) {
        for (Enemy enemy : enemies) {
            int event = enemy.update(dt, time, level.colliders(),
                    playerEye[0], playerEye[1], playerEye[2], player.alive());
            if (event == Enemy.EV_NONE) {
                continue;
            }
            if (enemy.type() == Enemy.TYPE_DRONE) {
                sounds.zap();
            } else {
                sounds.hit();
            }
            if (event == Enemy.EV_ATTACK_HIT && player.alive()) {
                player.damage(enemy.damage());
                hud.setHealth(player.health());
                hud.showDamage();
                if (!player.alive()) {
                    state = DEAD;
                    controls.setGameOver(true);
                    hud.setBig("VOCÊ CAIU\ntoque para tentar de novo");
                    hud.setObjective(null);
                }
            }
        }
    }

    private void updateItems() {
        for (float[] item : items) {
            if (item[4] != 0f) {
                continue;
            }
            float dx = item[1] - player.x();
            float dy = item[2] - (player.y() + 0.9f);
            float dz = item[3] - player.z();
            if (dx * dx + dy * dy + dz * dz
                    > PICKUP_RANGE * PICKUP_RANGE) {
                continue;
            }
            if (item[0] == RuntimeLevel.ITEM_HEALTH) {
                if (player.health() == Player.MAX_HEALTH) {
                    continue; // vida cheia: deixa o kit para depois
                }
                player.heal(HEAL_AMOUNT);
                hud.setHealth(player.health());
            } else {
                weapon.addReserve(AMMO_AMOUNT);
                hud.setAmmo(weapon.ammo(), weapon.reserve());
            }
            item[4] = 1f;
            sounds.pickup();
        }
    }

    private void checkExit() {
        float[] exit = level.exit();
        // mapa sem porta: a saída já nasce liberada
        boolean doorOpen = level.doorIndex() < 0 || doorProgress >= 0.9f;
        if (exit == null || !doorOpen || state != PLAYING) {
            return;
        }
        float dist = (float) Math.hypot(exit[0] - player.x(),
                exit[1] - player.z());
        if (dist < exit[2]) {
            state = stageNumber < totalStages ? STAGE_CLEAR : WON;
            controls.setGameOver(true);
            sounds.chime();
            if (state == STAGE_CLEAR) {
                transitionLeft = STAGE_TRANSITION_TIME;
                hud.setBig("SETOR " + stageNumber
                        + " CONCLUÍDO\ndescendo ao labirinto…");
            } else {
                int totalTime = (int) (priorTime + runTime);
                hud.setBig((totalStages > 1
                        ? "VOCÊ ESCAPOU DOS " + totalStages + " SETORES!"
                        : "MAPA CONCLUÍDO!") + "\ntempo: " + totalTime
                        + "s — toque para jogar de novo");
            }
            hud.setObjective(null);
        }
    }

    // ---- lidos pelo renderer ----

    public Enemy[] enemies() {
        return enemies;
    }

    /** Consumido uma vez pelo renderer para carregar o próximo cenário. */
    public boolean takeAdvanceRequest() {
        boolean value = advancePending;
        advancePending = false;
        return value;
    }

    /** Vitória final: reinicia a campanha no setor 1, não só esta fase. */
    public boolean takeCampaignRestartRequest() {
        boolean value = campaignRestartPending;
        campaignRestartPending = false;
        return value;
    }

    public float elapsedCampaignTime() {
        return priorTime + runTime;
    }

    /** {tipo, x, y, z, pego}. */
    public float[][] items() {
        return items;
    }

    public float[][] impacts() {
        return impacts;
    }

    public float recoil() {
        return recoil;
    }

    public boolean muzzleVisible() {
        return muzzleTtl > 0f;
    }

    public float doorProgress() {
        return doorProgress;
    }

    public boolean terminalActive() {
        return terminalActive;
    }
}
