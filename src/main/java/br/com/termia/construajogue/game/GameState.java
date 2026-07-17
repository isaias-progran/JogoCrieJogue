package br.com.termia.construajogue.game;

import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.runtime.RuntimeDoor;
import br.com.termia.construajogue.runtime.RuntimeNpc;
import br.com.termia.construajogue.runtime.RuntimeTerminal;
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
    private static final float NPC_INTERACT_RANGE = 3.0f;
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
    private final ObjectiveTracker objective;
    private final float[][] items; // {tipo, x, y, z, pego(0/1)}
    private final float[][] impacts = new float[MAX_IMPACTS][4]; // x,y,z,ttl
    private final float[] playerEye = new float[3];
    private final float[] rayOrigin = new float[3];
    private final float[] rayDir = new float[3];
    private final float[] tmpBounds = new float[6];

    private int state = PLAYING;
    private float runTime;
    private float hintLeft = HINT_TIME;
    private final boolean[] terminalActive;
    private final float[] doorProgress;
    private final boolean[] automaticDoorRequested;
    private final NpcCompanion[] companions;
    private final NpcGreetingTracker npcGreetings;
    private int nextTerminalOrder = 1;
    private float recoil;
    private float muzzleTtl;
    private float transitionLeft;
    private int nextImpact;
    private boolean advancePending;
    private boolean campaignRestartPending;
    private GameResult resultPending;
    private int shots;
    private int hits;
    private int kills;
    private float lavaCooldown;
    private float objectiveHudCooldown;
    private float lastStepX;
    private float lastStepZ;
    private float stepDistance;
    private boolean alternateStep;
    private RuntimeNpc npcInteractionPending;
    private RuntimeNpc npcGreetingPending;
    private RuntimeNpc npcCombatSpeakerPending;
    private String npcCombatLinePending;

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
                + level.mutantSpawns().length
                + level.extraEnemySpawns().length];
        objective = new ObjectiveTracker(level.objective(), enemies.length);
        terminalActive = new boolean[level.terminals().length];
        doorProgress = new float[level.doors().length];
        automaticDoorRequested = new boolean[level.doors().length];
        RuntimeNpc[] npcs = level.npcs();
        companions = new NpcCompanion[npcs.length];
        for (int i = 0; i < npcs.length; i++) {
            companions[i] = new NpcCompanion(npcs[i]);
        }
        npcGreetings = new NpcGreetingTracker(npcs.length);
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
        float[][] extras = level.extraEnemySpawns();
        int extraStart = mutantStart + mutants.length;
        for (int i = 0; i < extras.length; i++) {
            float[] e = extras[i];
            float[] spawn = {e[1], e[2], e[3], e[4], e[5]};
            int type = Math.round(e[0]);
            if (type == RuntimeLevel.ENEMY_TURRET) {
                enemies[extraStart + i] = new Turret(spawn);
            } else if (type == RuntimeLevel.ENEMY_KAMIKAZE) {
                enemies[extraStart + i] = new Kamikaze(spawn);
            } else {
                enemies[extraStart + i] = new Drone(spawn,
                        extraStart + i, false, true);
            }
        }
        float[][] source = level.items();
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, items[i], 0, 4);
            items[i][4] = 0f;
        }
        for (float[] impact : impacts) {
            impact[3] = 0f;
        }
        for (RuntimeDoor door : level.doors()) {
            System.arraycopy(door.original, 0,
                    level.colliders()[door.colliderIndex], 0, 6);
        }
        state = PLAYING;
        runTime = 0f;
        hintLeft = HINT_TIME;
        java.util.Arrays.fill(terminalActive, false);
        java.util.Arrays.fill(doorProgress, 0f);
        java.util.Arrays.fill(automaticDoorRequested, false);
        for (NpcCompanion companion : companions) companion.reset();
        npcGreetings.reset();
        nextTerminalOrder = 1;
        recoil = 0f;
        muzzleTtl = 0f;
        transitionLeft = 0f;
        advancePending = false;
        campaignRestartPending = false;
        resultPending = null;
        shots = 0;
        hits = 0;
        kills = 0;
        lavaCooldown = 0f;
        objectiveHudCooldown = 0f;
        lastStepX = player.x();
        lastStepZ = player.z();
        stepDistance = 0f;
        alternateStep = false;
        objective.reset();
        controls.setGameOver(false);
        controls.setInteractAction(false, "ATIVAR");
        controls.takeRestart(); // descarta toque que encerrou a tela anterior
        hud.setHealth(player.health());
        hud.setAmmo(weapon.ammo(), weapon.reserve());
        hud.setSpecial(weapon.special());
        hud.setReloading(false);
        hud.setBig(null);
        hud.setObjective(objective.hudText());
        hud.setHint("esquerda: andar   •   arraste TIRO: mirar   •   PULO: saltar");
        npcInteractionPending = null;
        npcGreetingPending = null;
        npcCombatSpeakerPending = null;
        npcCombatLinePending = null;
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
        objective.tick(dt);
        objectiveHudCooldown -= dt;
        if (objectiveHudCooldown <= 0f) {
            objectiveHudCooldown = 0.2f;
            hud.setObjective(objective.hudText());
        }
        if (objective.failed()) {
            failTimeLimit();
            return;
        }
        if (hintLeft > 0f) {
            hintLeft -= dt;
            if (hintLeft <= 0f) {
                hud.setHint(null);
            }
        }

        player.update(dt, controls, level, camera);
        updateFootsteps();
        player.eyeInto(playerEye);
        updateHazards(dt);
        if (!player.alive()) {
            return;
        }
        updateWeapon(dt);
        updateTerminalAndDoor(dt);
        updateCompanions(dt);
        updateEnemies(dt, time);
        updateItems();
        checkExit();
        if (objective.complete() && state == PLAYING) {
            completeStage();
        }

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

    private void updateHazards(float dt) {
        if (!level.inHazard(RuntimeLevel.HAZARD_LAVA,
                player.x(), player.y(), player.z())) {
            lavaCooldown = 0f;
            return;
        }
        lavaCooldown -= dt;
        if (lavaCooldown <= 0f) {
            lavaCooldown = 0.7f;
            player.damage(12);
            hud.setHealth(player.health());
            hud.showDamage();
            if (!player.alive()) {
                die("A LAVA VENCEU");
            }
        }
    }

    private void updateFootsteps() {
        float dx = player.x() - lastStepX;
        float dz = player.z() - lastStepZ;
        lastStepX = player.x();
        lastStepZ = player.z();
        if (!player.grounded()) {
            return;
        }
        stepDistance += (float) Math.sqrt(dx * dx + dz * dz);
        if (stepDistance < 1.35f) return;
        stepDistance -= 1.35f;
        boolean wet = level.inHazard(RuntimeLevel.HAZARD_WATER,
                player.x(), player.y(), player.z());
        sounds.footstep(wet, alternateStep);
        alternateStep = !alternateStep;
    }

    private void failTimeLimit() {
        state = DEAD;
        controls.setGameOver(true);
        hud.setBig("TEMPO ESGOTADO\ntoque para tentar de novo");
        hud.setObjective(null);
    }

    private void die(String title) {
        state = DEAD;
        controls.setGameOver(true);
        hud.setBig(title + "\ntoque para tentar de novo");
        hud.setObjective(null);
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
                shots++;
                sounds.shot();
                recoil += 0.035f;
                camera.rotate(0f, 0.002f); // coice sutil
                muzzleTtl = MUZZLE_LIFE;
                hud.setAmmo(weapon.ammo(), weapon.reserve());
                hud.setSpecial(weapon.special());
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
            hits++;
            hud.showHitMarker();
            boolean died = false;
            int damage = weapon.shotDamage();
            for (int i = 0; i < damage && target.targetable(); i++) {
                died |= target.hit(level.colliders());
            }
            if (died) {
                registerKill();
                sounds.boom();
            } else {
                sounds.hit();
            }
            if (weapon.lastShotSpecial()) {
                splash(target);
            }
        }
    }

    private void splash(Enemy center) {
        for (Enemy enemy : enemies) {
            if (enemy == center || !enemy.targetable()) continue;
            float dx = enemy.x() - center.x();
            float dy = enemy.y() - center.y();
            float dz = enemy.z() - center.z();
            if (dx * dx + dy * dy + dz * dz <= 5.0f
                    && enemy.hit(level.colliders())) {
                registerKill();
            }
        }
    }

    private void registerKill() {
        kills++;
        objective.enemyEliminated();
    }

    private void updateTerminalAndDoor(float dt) {
        RuntimeTerminal[] terminals = level.terminals();
        int nearest = -1;
        float nearestDist = INTERACT_RANGE;
        for (int i = 0; i < terminals.length; i++) {
            if (terminalActive[i]) continue;
            float dist = SpatialRules.distance(terminals[i].x,
                    terminals[i].y, terminals[i].z, player.x(),
                    player.y() + 1.4f, player.z());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = i;
            }
        }

        RuntimeNpc nearestNpc = null;
        int nearestNpcIndex = -1;
        float nearestNpcDist = NPC_INTERACT_RANGE;
        RuntimeNpc[] npcs = level.npcs();
        for (int i = 0; i < npcs.length; i++) {
            RuntimeNpc npc = npcs[i];
            float dist = SpatialRules.distance(npc.x, npc.y + 1.35f,
                    npc.z, player.x(), player.y() + 1.4f, player.z());
            if (dist < nearestNpcDist) {
                nearestNpcDist = dist;
                nearestNpc = npc;
                nearestNpcIndex = i;
            }
        }
        boolean talk = nearestNpc != null
                && (nearest < 0 || nearestNpcDist < nearestDist);
        controls.setInteractAction(talk || nearest >= 0,
                talk ? "FALAR" : "ATIVAR");
        if (talk && npcGreetings.firstApproach(nearestNpcIndex)) {
            companions[nearestNpcIndex].startFollowing();
            npcGreetingPending = nearestNpc;
            hintLeft = 6f;
            hud.setHint(nearestNpc.name + ": " + nearestNpc.greeting
                    + "  •  toque FALAR");
        }
        if ((talk || nearest >= 0) && controls.takeInteract()) {
            if (talk) {
                npcInteractionPending = nearestNpc;
                controls.setInteractAction(false, "FALAR");
                hud.setHint(nearestNpc.name + ": " + nearestNpc.greeting);
            } else {
                RuntimeTerminal terminal = terminals[nearest];
                if (terminal.order > 0
                        && terminal.order != nextTerminalOrder) {
                    hud.setHint("sequência: procure o terminal "
                            + nextTerminalOrder);
                } else {
                    terminalActive[nearest] = true;
                    if (terminal.order > 0) nextTerminalOrder++;
                    controls.setInteractAction(false, "ATIVAR");
                    sounds.chime();
                    sounds.door();
                    for (Enemy enemy : enemies) enemy.wake();
                    hud.setHint("terminal ativado");
                }
            }
        }

        RuntimeDoor[] doors = level.doors();
        for (int i = 0; i < doors.length; i++) {
            RuntimeDoor door = doors[i];
            boolean shouldOpen;
            if (door.automatic) {
                float dist = SpatialRules.distance(door.centerX(),
                        door.centerY(), door.centerZ(), player.x(),
                        player.y() + 0.875f, player.z());
                // Histerese: não fica tremendo no limiar e não fecha
                // enquanto o jogador ainda atravessa a folha.
                if (dist < 2.35f && !automaticDoorRequested[i]) {
                    automaticDoorRequested[i] = true;
                    sounds.door();
                } else if (dist > 3.25f) {
                    automaticDoorRequested[i] = false;
                }
                shouldOpen = automaticDoorRequested[i];
            } else {
                shouldOpen = controllerActive(door.controllerId);
            }
            float speed = dt / (door.automatic ? 0.65f : DOOR_TIME);
            doorProgress[i] = Math.max(0f, Math.min(1f,
                    doorProgress[i] + (shouldOpen ? speed : -speed)));
            float[] collider = level.colliders()[door.colliderIndex];
            for (int axis = 0; axis < 3; axis++) {
                float move = axis == 0 ? door.moveX
                        : axis == 1 ? door.moveY : door.moveZ;
                collider[axis] = door.original[axis]
                        + move * doorProgress[i];
                collider[axis + 3] = door.original[axis + 3]
                        + move * doorProgress[i];
            }
        }
    }

    private void updateCompanions(float dt) {
        for (NpcCompanion companion : companions) {
            int event = companion.update(dt, player.x(), player.y(),
                    player.z(), level.colliders(), enemies, companions);
            if ((event & NpcCompanion.EV_SHOT) != 0) {
                sounds.allyShot();
            }
            if ((event & NpcCompanion.EV_KILL) != 0) {
                objective.enemyEliminated();
                hud.setObjective(objective.hudText());
            }
            if ((event & NpcCompanion.EV_SPEECH) != 0
                    && npcCombatLinePending == null) {
                String line = companion.takeCombatSpeech();
                if (line != null) {
                    npcCombatSpeakerPending = companion.npc();
                    npcCombatLinePending = line;
                    hintLeft = 4f;
                    hud.setHint(companion.npc().name + ": " + line);
                }
            }
            if ((event & NpcCompanion.EV_RECOVERED) != 0) {
                hintLeft = 4f;
                hud.setHint(companion.npc().name
                        + " se recuperou e voltou à luta");
            }
        }
    }

    private boolean controllerActive(String id) {
        if (id == null) return false;
        RuntimeTerminal[] terminals = level.terminals();
        for (int i = 0; i < terminals.length; i++) {
            if (id.equals(terminals[i].id)) return terminalActive[i];
        }
        return false;
    }

    private void updateEnemies(float dt, float time) {
        for (Enemy enemy : enemies) {
            NpcCompanion allyTarget = chooseAllyTarget(enemy);
            float targetX = allyTarget == null
                    ? playerEye[0] : allyTarget.npc().x;
            float targetY = allyTarget == null
                    ? playerEye[1] : allyTarget.npc().y + 1.38f;
            float targetZ = allyTarget == null
                    ? playerEye[2] : allyTarget.npc().z;
            int event = enemy.update(dt, time, level.colliders(),
                    targetX, targetY, targetZ,
                    allyTarget == null ? player.alive()
                            : allyTarget.canBeTargeted());
            if (event == Enemy.EV_NONE) {
                continue;
            }
            if (enemy.type() == Enemy.TYPE_DRONE
                    || enemy.type() == Enemy.TYPE_BOSS
                    || enemy.type() == Enemy.TYPE_TURRET) {
                sounds.zap();
            } else {
                sounds.hit();
            }
            if (enemy.type() == Enemy.TYPE_KAMIKAZE && enemy.wreck()) {
                registerKill();
                sounds.boom();
            }
            if (event == Enemy.EV_ATTACK_HIT) {
                if (allyTarget != null && allyTarget.canBeTargeted()) {
                    if (allyTarget.damage(enemy.damage())) {
                        hintLeft = 5f;
                        hud.setHint(allyTarget.npc().name
                                + " desmaiou — proteja a área");
                    }
                } else if (player.alive()) {
                    player.damage(enemy.damage());
                    hud.setHealth(player.health());
                    hud.showDamage();
                    if (!player.alive()) {
                        die("VOCÊ CAIU");
                    }
                }
            }
        }
    }

    /** Inimigos preferem o alvo mais próximo; tiro recente provoca atenção. */
    private NpcCompanion chooseAllyTarget(Enemy enemy) {
        if (!player.alive() || !enemy.targetable()) return null;
        return NpcCompanion.targetForEnemy(enemy, playerEye[0], playerEye[1],
                playerEye[2], companions, level.colliders());
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
            } else if (item[0] == RuntimeLevel.ITEM_AMMO) {
                weapon.addReserve(AMMO_AMOUNT);
                hud.setAmmo(weapon.ammo(), weapon.reserve());
            } else if (item[0] == RuntimeLevel.ITEM_SPECIAL) {
                weapon.addSpecial(6);
                hud.setSpecial(weapon.special());
            } else if (item[0] == RuntimeLevel.ITEM_WEAPON_SMG
                    || item[0] == RuntimeLevel.ITEM_WEAPON_SHOTGUN
                    || item[0] == RuntimeLevel.ITEM_WEAPON_RIFLE) {
                WeaponSpec next =
                        item[0] == RuntimeLevel.ITEM_WEAPON_SMG
                                ? WeaponSpec.SMG
                                : item[0] == RuntimeLevel.ITEM_WEAPON_SHOTGUN
                                ? WeaponSpec.SHOTGUN : WeaponSpec.RIFLE;
                weapon.equip(next);
                hud.setAmmo(weapon.ammo(), weapon.reserve());
                hud.setHint(next.label + " EQUIPADA");
            } else {
                objective.tokenCollected();
                hud.setObjective(objective.hudText());
            }
            item[4] = 1f;
            sounds.pickup();
        }
    }

    private void checkExit() {
        float[] exit = level.exit();
        if (exit == null || state != PLAYING) {
            return;
        }
        if (SpatialRules.insideExit(exit, level.exitElevation(),
                level.exitElevationKnown(), player.x(), player.y(),
                player.z())) {
            objective.enteredExit();
        }
    }

    private void completeStage() {
        state = stageNumber < totalStages ? STAGE_CLEAR : WON;
        controls.setGameOver(true);
        sounds.chime();
        resultPending = new GameResult(level.mapId(), level.mapName(),
                runTime, shots, hits, kills, player.health(),
                level.objective());
        if (state == STAGE_CLEAR) {
            transitionLeft = STAGE_TRANSITION_TIME;
            hud.setBig("SETOR " + stageNumber
                    + " CONCLUÍDO\n" + stars(resultPending.stars));
        } else {
            int totalTime = (int) (priorTime + runTime);
            hud.setBig((totalStages > 1
                    ? "VOCÊ VENCEU OS " + totalStages + " MAPAS!"
                    : "MAPA CONCLUÍDO!") + "\n"
                    + stars(resultPending.stars) + "  ·  " + totalTime
                    + "s — toque para jogar de novo");
        }
        hud.setObjective(null);
    }

    private static String stars(int count) {
        return count >= 3 ? "★★★" : count == 2 ? "★★☆" : "★☆☆";
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
        return doorProgress.length == 0 ? 0f : doorProgress[0];
    }

    public boolean terminalActive() {
        return terminalActive.length > 0 && terminalActive[0];
    }

    public float doorProgress(int index) { return doorProgress[index]; }
    public boolean terminalActive(int index) { return terminalActive[index]; }

    public float playerX() { return player.x(); }
    public float playerY() { return player.y(); }
    public float playerZ() { return player.z(); }

    /** Pedido de conversa consumido uma vez pelo renderer/UI. */
    public RuntimeNpc takeNpcInteraction() {
        RuntimeNpc value = npcInteractionPending;
        npcInteractionPending = null;
        return value;
    }

    /** Saudação automática consumida uma vez pela UI/voz. */
    public RuntimeNpc takeNpcGreeting() {
        RuntimeNpc value = npcGreetingPending;
        npcGreetingPending = null;
        return value;
    }

    /** Lido antes de takeNpcCombatLine(), na mesma thread GL. */
    public RuntimeNpc npcCombatSpeaker() {
        return npcCombatSpeakerPending;
    }

    /** Fala local ocasional; consumida uma vez pelo renderer. */
    public String takeNpcCombatLine() {
        String value = npcCombatLinePending;
        npcCombatLinePending = null;
        npcCombatSpeakerPending = null;
        return value;
    }

    /** Resultado consumido uma vez para persistir fora da thread GL. */
    public GameResult takeResult() {
        GameResult value = resultPending;
        resultPending = null;
        return value;
    }
}
