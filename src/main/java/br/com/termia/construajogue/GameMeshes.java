package br.com.termia.construajogue;

import br.com.termia.construajogue.engine.Boxes;
import br.com.termia.construajogue.engine.Mesh;

/** Malhas pequenas compartilhadas pelos dois setores da campanha. */
final class GameMeshes {

    final Mesh drone = new Mesh(buildDrone());
    final Mesh mutant = new Mesh(buildMutant());
    final Mesh gun = new Mesh(buildGun());
    final Mesh flash = new Mesh(buildFlash());
    final Mesh impact = new Mesh(buildImpact());
    final Mesh health = new Mesh(buildHealth());
    final Mesh ammo = new Mesh(buildAmmo());
    final Mesh terminalLight = new Mesh(buildTerminalLight());

    /** Corpo vermelho + sensor escuro + luz inferior. */
    private static float[] buildDrone() {
        float[] out = new float[3 * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        cursor = Boxes.emitCentered(out, cursor, 0f, 0f, 0f,
                0.28f, 0.14f, 0.28f, 0.72f, 0.16f, 0.14f);
        cursor = Boxes.emitCentered(out, cursor, 0f, 0.19f, 0f,
                0.10f, 0.05f, 0.10f, 0.16f, 0.17f, 0.20f);
        Boxes.emitCentered(out, cursor, 0f, -0.17f, 0f,
                0.06f, 0.03f, 0.06f, 1.0f, 0.15f, 0.10f);
        return out;
    }

    /** Mutante: cabeça, torso, braços e pernas verde-acinzentados. */
    private static float[] buildMutant() {
        float[] out = new float[6 * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        cursor = Boxes.emitCentered(out, cursor, 0f, 0.60f, 0f,
                0.18f, 0.20f, 0.17f, 0.48f, 0.62f, 0.38f);
        cursor = Boxes.emitCentered(out, cursor, 0f, 0.16f, 0f,
                0.23f, 0.34f, 0.15f, 0.30f, 0.38f, 0.28f);
        cursor = Boxes.emitCentered(out, cursor, -0.31f, 0.14f, 0f,
                0.07f, 0.32f, 0.07f, 0.42f, 0.55f, 0.34f);
        cursor = Boxes.emitCentered(out, cursor, 0.31f, 0.14f, 0f,
                0.07f, 0.32f, 0.07f, 0.42f, 0.55f, 0.34f);
        cursor = Boxes.emitCentered(out, cursor, -0.12f, -0.47f, 0f,
                0.09f, 0.28f, 0.10f, 0.20f, 0.24f, 0.23f);
        Boxes.emitCentered(out, cursor, 0.12f, -0.47f, 0f,
                0.09f, 0.28f, 0.10f, 0.20f, 0.24f, 0.23f);
        return out;
    }

    /** Pistola em espaço de visão (câmera olha para -z). */
    private static float[] buildGun() {
        float[] out = new float[3 * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        cursor = Boxes.emitCentered(out, cursor, 0.16f, -0.13f, -0.42f,
                0.025f, 0.03f, 0.09f, 0.15f, 0.16f, 0.19f);
        cursor = Boxes.emitCentered(out, cursor, 0.16f, -0.115f, -0.54f,
                0.012f, 0.012f, 0.05f, 0.09f, 0.10f, 0.12f);
        Boxes.emitCentered(out, cursor, 0.16f, -0.175f, -0.365f,
                0.02f, 0.035f, 0.024f, 0.12f, 0.13f, 0.15f);
        return out;
    }

    private static float[] buildFlash() {
        float[] out = new float[Boxes.FLOATS_PER_BOX];
        Boxes.emitCentered(out, 0, 0.16f, -0.115f, -0.62f,
                0.022f, 0.022f, 0.022f, 1.0f, 0.85f, 0.5f);
        return out;
    }

    private static float[] buildImpact() {
        float[] out = new float[Boxes.FLOATS_PER_BOX];
        Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.03f, 0.03f, 0.03f, 1.0f, 0.8f, 0.35f);
        return out;
    }

    private static float[] buildHealth() {
        float[] out = new float[3 * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        cursor = Boxes.emitCentered(out, cursor, 0f, 0f, 0f,
                0.16f, 0.12f, 0.16f, 0.88f, 0.90f, 0.92f);
        cursor = Boxes.emitCentered(out, cursor, 0f, 0.125f, 0f,
                0.11f, 0.012f, 0.035f, 0.15f, 0.75f, 0.35f);
        Boxes.emitCentered(out, cursor, 0f, 0.125f, 0f,
                0.035f, 0.012f, 0.11f, 0.15f, 0.75f, 0.35f);
        return out;
    }

    private static float[] buildAmmo() {
        float[] out = new float[2 * Boxes.FLOATS_PER_BOX];
        int cursor = 0;
        cursor = Boxes.emitCentered(out, cursor, 0f, 0f, 0f,
                0.15f, 0.10f, 0.11f, 0.72f, 0.58f, 0.16f);
        Boxes.emitCentered(out, cursor, 0f, 0.11f, 0f,
                0.16f, 0.02f, 0.12f, 0.45f, 0.37f, 0.12f);
        return out;
    }

    private static float[] buildTerminalLight() {
        float[] out = new float[Boxes.FLOATS_PER_BOX];
        Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.10f, 0.10f, 0.10f, 1f, 1f, 1f);
        return out;
    }
}
