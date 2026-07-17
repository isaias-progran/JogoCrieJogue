package br.com.termia.construajogue;

import br.com.termia.construajogue.game.GameResult;
import br.com.termia.construajogue.game.Enemy;
import br.com.termia.construajogue.game.NpcCompanion;
import br.com.termia.construajogue.game.NpcGreetingTracker;
import br.com.termia.construajogue.game.ObjectiveTracker;
import br.com.termia.construajogue.game.SpatialRules;
import br.com.termia.construajogue.game.Weapon;
import br.com.termia.construajogue.game.WeaponSpec;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.runtime.RuntimeNpc;

public final class GameplayRulesTest {
    private static int checks;

    public static void main(String[] args) {
        ObjectiveSpec collect = new ObjectiveSpec();
        collect.type = ObjectiveSpec.COLLECT;
        collect.target = 2;
        ObjectiveTracker tracker = new ObjectiveTracker(collect, 3);
        tracker.reset();
        tracker.tokenCollected();
        check(!tracker.complete(), "uma ficha ainda não conclui");
        tracker.tokenCollected();
        check(tracker.complete(), "duas fichas concluem");

        ObjectiveSpec eliminate = new ObjectiveSpec();
        eliminate.type = ObjectiveSpec.ELIMINATE_ALL;
        tracker = new ObjectiveTracker(eliminate, 2);
        tracker.reset();
        tracker.enemyEliminated();
        tracker.enemyEliminated();
        check(tracker.complete(), "todos inimigos");

        ObjectiveSpec survive = new ObjectiveSpec();
        survive.type = ObjectiveSpec.SURVIVE;
        survive.durationSeconds = 5f;
        tracker = new ObjectiveTracker(survive, 0);
        tracker.reset();
        tracker.tick(4.9f);
        check(!tracker.complete(), "sobrevivência em andamento");
        tracker.tick(0.2f);
        check(tracker.complete(), "sobrevivência concluída");

        ObjectiveSpec timed = new ObjectiveSpec();
        timed.timeLimitSeconds = 2f;
        tracker = new ObjectiveTracker(timed, 0);
        tracker.reset();
        tracker.tick(2f);
        check(tracker.failed(), "tempo-limite falha");
        tracker.enteredExit();
        check(!tracker.complete(), "falha não volta a concluir");

        ObjectiveSpec stars = new ObjectiveSpec();
        stars.twoStarSeconds = 60f;
        stars.threeStarSeconds = 30f;
        check(new GameResult("id", "mapa", 25f, 2, 1, 0, 100,
                stars).stars == 3, "três estrelas");
        check(new GameResult("id", "mapa", 45f, 2, 1, 0, 100,
                stars).stars == 2, "duas estrelas");
        check(new GameResult("id", "mapa", 80f, 2, 1, 0, 100,
                stars).stars == 1, "uma estrela");

        ObjectiveSpec surviveStars = new ObjectiveSpec();
        surviveStars.type = ObjectiveSpec.SURVIVE;
        surviveStars.durationSeconds = 30f;
        surviveStars.twoStarSeconds = 60f;
        surviveStars.threeStarSeconds = 30f;
        check(new GameResult("id", "mapa", 30f, 2, 1, 0, 100,
                surviveStars).stars == 3,
                "sobreviver ileso vale três estrelas");
        check(new GameResult("id", "mapa", 30f, 2, 1, 0,
                GameResult.SURVIVE_THREE_STAR_HEALTH - 1,
                surviveStars).stars == 2, "vida média vale duas estrelas");
        check(new GameResult("id", "mapa", 30f, 2, 1, 0,
                GameResult.SURVIVE_TWO_STAR_HEALTH - 1,
                surviveStars).stars == 1,
                "quase morrer vale uma estrela mesmo dentro da meta de tempo");

        Weapon arsenal = new Weapon();
        arsenal.reset();
        check(arsenal.spec() == WeaponSpec.PISTOL && arsenal.ammo() == 12,
                "pistola é a arma inicial");
        arsenal.equip(WeaponSpec.SHOTGUN);
        check(arsenal.ammo() == 6 && arsenal.reserve() == 18,
                "escopeta equipa pente e reserva próprios");
        check(arsenal.tryFire() && arsenal.shotDamage() == 3,
                "escopeta derruba um drone num tiro");
        check(!arsenal.tryFire(), "cadência da escopeta segura o 2º tiro");
        arsenal.update(0.9f);
        check(arsenal.tryFire(), "cadência liberada após o intervalo");
        arsenal.equip(WeaponSpec.RIFLE);
        arsenal.addSpecial(1);
        check(arsenal.tryFire() && arsenal.shotDamage() == 6,
                "bala especial soma +2 ao dano do rifle");
        check(WeaponSpec.byId("smg") == WeaponSpec.SMG
                        && WeaponSpec.byId("faca") == null,
                "byId só conhece a allowlist de armas");

        Weapon weapon = new Weapon();
        weapon.reset();
        weapon.addSpecial(2);
        check(weapon.tryFire() && weapon.lastShotSpecial(),
                "tiro especial usado");
        check(weapon.special() == 1, "munição especial consumida");
        weapon.update(1f);
        check(weapon.tryFire() && weapon.special() == 0,
                "segundo tiro especial");

        check(SpatialRules.insideExit(new float[]{2f, 4f, 1.2f},
                        2f, 20f, 4f), "saída legada continua 2D");
        check(!SpatialRules.insideExit(new float[]{2f, 4f, 1.2f},
                        0f, true, 2f, 3.3f, 4f),
                "saída JSON térrea não atravessa pavimento");
        float[] upperExit = {2f, 3.3f, 4f, 1.2f};
        check(SpatialRules.insideExit(upperExit, 2f, 3.3f, 4f),
                "saída aceita jogador no mesmo andar");
        check(!SpatialRules.insideExit(upperExit, 2f, 0f, 4f),
                "saída não atravessa pavimento");

        RuntimeLevel level = emptyLevel();
        level.setHazards(new float[][]{{RuntimeLevel.HAZARD_WATER,
                -2f, -0.3f, -2f, 2f, 0f, 2f}});
        check(level.speedMultiplierAt(0f, 0f, 0f) < 1f,
                "água reduz velocidade na sua laje");
        check(level.speedMultiplierAt(0f, 3.3f, 0f) == 1f,
                "água térrea não reduz velocidade no andar de cima");

        NpcGreetingTracker greetings = new NpcGreetingTracker(2);
        check(greetings.firstApproach(0), "primeira aproximação cumprimenta");
        check(!greetings.firstApproach(0), "mesmo NPC não repete saudação");
        check(greetings.firstApproach(1), "outro NPC pode cumprimentar");

        RuntimeNpc npc = new RuntimeNpc("n", "Lia", "guia", "Olá", "",
                0f, 0f, 0f, 0f);
        NpcCompanion companion = new NpcCompanion(npc);
        companion.update(1f, 5f, 0f, 0f, new float[0][]);
        check(npc.x == 0f, "NPC parado antes de conhecer o jogador");
        companion.startFollowing();
        companion.update(1f, 5f, 0f, 0f, new float[0][]);
        check(npc.x > 2f && npc.moving, "companheiro segue localmente");
        float sameFloorX = npc.x;
        companion.update(1f, 5f, 3.3f, 0f, new float[0][]);
        check(npc.x == sameFloorX, "companheiro não atravessa pavimento");
        companion.reset();
        check(npc.x == 0f && !companion.following(),
                "reinício devolve NPC à origem");

        RuntimeNpc civilianNpc = fighter(false);
        NpcCompanion civilian = new NpcCompanion(civilianNpc);
        FakeEnemy civilianEnemy = new FakeEnemy(4f, 1.38f, 0f, 3);
        civilian.startFollowing();
        int event = civilian.update(0.5f, 0f, 0f, 5f,
                new float[0][], new Enemy[]{civilianEnemy},
                new NpcCompanion[]{civilian});
        check(event == NpcCompanion.EV_NONE && civilianEnemy.hits == 0,
                "NPC pacífico segue sem entrar no combate");

        RuntimeNpc waitingNpc = fighter(true);
        NpcCompanion waiting = new NpcCompanion(waitingNpc);
        FakeEnemy waitingEnemy = new FakeEnemy(4f, 1.38f, 0f, 3);
        waiting.update(0.5f, 0f, 0f, 5f, new float[0][],
                new Enemy[]{waitingEnemy}, new NpcCompanion[]{waiting});
        check(waitingEnemy.hits == 0,
                "aliado só luta depois de começar a seguir");

        RuntimeNpc fighterNpc = fighter(true);
        NpcCompanion fighter = new NpcCompanion(fighterNpc);
        FakeEnemy farEnemy = new FakeEnemy(7f, 1.38f, 0f, 3);
        FakeEnemy nearEnemy = new FakeEnemy(3f, 1.38f, 0f, 3);
        fighter.startFollowing();
        event = fighter.update(0.4f, 0f, 0f, 5f, new float[0][],
                new Enemy[]{farEnemy, nearEnemy},
                new NpcCompanion[]{fighter});
        check((event & NpcCompanion.EV_SHOT) != 0
                        && nearEnemy.hits == 1 && farEnemy.hits == 0,
                "aliado atira no inimigo visível mais próximo");
        check((event & NpcCompanion.EV_SPEECH) != 0
                        && "Linha um".equals(fighter.takeCombatSpeech()),
                "primeiro tiro pode emitir fala curta salva no mapa");
        check(fighterNpc.tracerTtl > 0f
                        && fighterNpc.tracerX == nearEnemy.x(),
                "tiro registra traçador visual até o alvo");
        check(NpcCompanion.COMBAT_RANGE == 14f,
                "alcance do aliado fica limitado a 14 metros");
        check(NpcCompanion.FIRE_INTERVAL > WeaponSpec.RIFLE.cooldown,
                "cadência do aliado é mais lenta que todas as armas");
        fighter.update(1f, 0f, 0f, 5f, new float[0][],
                new Enemy[]{nearEnemy}, new NpcCompanion[]{fighter});
        check(nearEnemy.hits == 1, "cadência impede tiro antecipado");
        fighter.update(0.21f, 0f, 0f, 5f, new float[0][],
                new Enemy[]{nearEnemy}, new NpcCompanion[]{fighter});
        check(nearEnemy.hits == 2, "cadência libera o tiro seguinte");

        RuntimeNpc coveredNpc = fighter(true);
        NpcCompanion covered = new NpcCompanion(coveredNpc);
        FakeEnemy coveredEnemy = new FakeEnemy(4f, 1.38f, 0f, 3);
        covered.startFollowing();
        float[][] wall = {{1f, -0.2f, -0.5f, 2f, 3f, 0.5f}};
        covered.update(0.4f, 0f, 0f, 5f, wall,
                new Enemy[]{coveredEnemy}, new NpcCompanion[]{covered});
        check(coveredEnemy.hits == 0,
                "parede interrompe a linha de visão do aliado");

        RuntimeNpc returnNpc = fighter(true);
        NpcCompanion returning = new NpcCompanion(returnNpc);
        FakeEnemy fragile = new FakeEnemy(4f, 1.38f, 0f, 1);
        returning.startFollowing();
        event = returning.update(0.4f, 8f, 0f, 4f, new float[0][],
                new Enemy[]{fragile}, new NpcCompanion[]{returning});
        check((event & NpcCompanion.EV_KILL) != 0,
                "morte causada pelo aliado é informada ao objetivo");
        returning.update(1f, 8f, 0f, 4f, new float[0][],
                new Enemy[]{fragile}, new NpcCompanion[]{returning});
        check(returnNpc.x == 0f,
                "aliado observa uma pausa curta depois de limpar a área");
        returning.update(0.6f, 8f, 0f, 4f, new float[0][],
                new Enemy[]{fragile}, new NpcCompanion[]{returning});
        check(returnNpc.x > 0f && returnNpc.moving,
                "aliado volta a seguir depois da área limpa");

        RuntimeNpc recoveryNpc = fighter(true);
        NpcCompanion recovery = new NpcCompanion(recoveryNpc);
        recovery.startFollowing();
        check(recovery.damage(NpcCompanion.MAX_HEALTH)
                        && recoveryNpc.downed && !recovery.canBeTargeted(),
                "vida zerada desmaia o aliado sem removê-lo");
        event = recovery.update(NpcCompanion.RECOVERY_DELAY + 0.1f,
                0f, 0f, 5f, new float[0][], new Enemy[0],
                new NpcCompanion[]{recovery});
        check((event & NpcCompanion.EV_RECOVERED) != 0
                        && recovery.health() == NpcCompanion.MAX_HEALTH / 2,
                "aliado recupera metade da vida quando a área fica segura");

        RuntimeNpc dangerNpc = fighter(true);
        NpcCompanion danger = new NpcCompanion(dangerNpc);
        danger.startFollowing();
        danger.damage(NpcCompanion.MAX_HEALTH);
        FakeEnemy nearby = new FakeEnemy(5f, 1.38f, 0f, 2);
        danger.update(20f, 0f, 0f, 5f, new float[0][],
                new Enemy[]{nearby}, new NpcCompanion[]{danger});
        check(dangerNpc.downed,
                "inimigo próximo impede recuperação durante o perigo");
        event = danger.update(NpcCompanion.RECOVERY_DELAY + 0.1f,
                0f, 0f, 5f, new float[0][], new Enemy[0],
                new NpcCompanion[]{danger});
        check((event & NpcCompanion.EV_RECOVERED) != 0,
                "recuperação recomeça quando o perigo desaparece");

        RuntimeNpc decoyNpc = new RuntimeNpc("d", "Bia", "apoio", "Oi", "",
                true, null, 3f, 0f, 0f, 0f);
        NpcCompanion decoy = new NpcCompanion(decoyNpc);
        decoy.startFollowing();
        FakeEnemy attacker = new FakeEnemy(0f, 1.38f, 0f, 5);
        check(NpcCompanion.targetForEnemy(attacker, 8f, 1.38f, 0f,
                        new NpcCompanion[]{decoy}, new float[0][]) == decoy,
                "inimigo pode mirar no aliado mais próximo");
        check(NpcCompanion.targetForEnemy(attacker, 1f, 1.38f, 0f,
                        new NpcCompanion[]{decoy}, new float[0][]) == null,
                "inimigo mantém o jogador quando ele é o mais próximo");
        check(NpcCompanion.targetForEnemy(attacker, 8f, 1.38f, 0f,
                        new NpcCompanion[]{decoy},
                        new float[][]{{1f, 0f, -0.4f, 2f, 3f, 0.4f}})
                        == null,
                "inimigo não escolhe aliado escondido por parede");
        decoy.damage(NpcCompanion.MAX_HEALTH);
        check(NpcCompanion.targetForEnemy(attacker, 8f, 1.38f, 0f,
                        new NpcCompanion[]{decoy}, new float[0][]) == null,
                "inimigo deixa de mirar no aliado desmaiado");

        RuntimeNpc provokerNpc = new RuntimeNpc("p", "Ivo", "apoio", "Oi", "",
                true, null, 6f, 0f, 0f, 0f);
        NpcCompanion provoker = new NpcCompanion(provokerNpc);
        provoker.startFollowing();
        FakeEnemy bait = new FakeEnemy(7f, 1.38f, 0f, 3);
        check(NpcCompanion.targetForEnemy(attacker, 5f, 1.38f, 0f,
                        new NpcCompanion[]{provoker}, new float[0][]) == null,
                "aliado distante não rouba o alvo sem provocar");
        provoker.update(0.4f, 6f, 0f, 5f, new float[0][],
                new Enemy[]{bait}, new NpcCompanion[]{provoker});
        check(NpcCompanion.targetForEnemy(attacker, 5f, 1.38f, 0f,
                        new NpcCompanion[]{provoker}, new float[0][])
                        == provoker,
                "tiro recente aumenta temporariamente a atenção do inimigo");
        System.out.println("OK GameplayRulesTest: " + checks
                + " verificações");
    }

    private static RuntimeNpc fighter(boolean combatant) {
        return new RuntimeNpc("f", "Rui", "vigia", "Bora", "",
                combatant, new String[]{"Linha um", "Linha dois",
                "Linha três"}, 0f, 0f, 0f, 0f);
    }

    private static final class FakeEnemy implements Enemy {
        private final float x;
        private final float y;
        private final float z;
        private int health;
        int hits;

        FakeEnemy(float x, float y, float z, int health) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
        }

        @Override public void wake() { }
        @Override public int update(float dt, float time, float[][] boxes,
                                    float px, float py, float pz,
                                    boolean alive) { return EV_NONE; }
        @Override public boolean hit(float[][] boxes) {
            if (health <= 0) return false;
            hits++;
            health--;
            return health == 0;
        }
        @Override public boolean targetable() { return health > 0; }
        @Override public boolean dormant() { return false; }
        @Override public boolean wreck() { return health <= 0; }
        @Override public boolean flashing() { return false; }
        @Override public boolean telegraphing() { return false; }
        @Override public void boundsInto(float[] out) {
            out[0] = x - 0.3f; out[1] = y - 0.3f; out[2] = z - 0.3f;
            out[3] = x + 0.3f; out[4] = y + 0.3f; out[5] = z + 0.3f;
        }
        @Override public int type() { return TYPE_DRONE; }
        @Override public int damage() { return 7; }
        @Override public float x() { return x; }
        @Override public float y() { return y; }
        @Override public float z() { return z; }
    }

    private static RuntimeLevel emptyLevel() {
        return new RuntimeLevel(new float[0][], new float[0], null,
                -1, null, null, null, new float[0][],
                new float[]{0f, 0f, 0f, 0f}, new float[0][],
                new float[0][], new float[0][], 0.35f,
                new float[]{0f, 0f, 0f}, 30f);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
