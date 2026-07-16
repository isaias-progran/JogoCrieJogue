package br.com.termia.construajogue;

import br.com.termia.construajogue.game.GameResult;
import br.com.termia.construajogue.game.NpcCompanion;
import br.com.termia.construajogue.game.NpcGreetingTracker;
import br.com.termia.construajogue.game.ObjectiveTracker;
import br.com.termia.construajogue.game.SpatialRules;
import br.com.termia.construajogue.game.Weapon;
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
        System.out.println("OK GameplayRulesTest: " + checks
                + " verificações");
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
