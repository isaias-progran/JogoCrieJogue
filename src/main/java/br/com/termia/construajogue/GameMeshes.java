package br.com.termia.construajogue;

import br.com.termia.construajogue.engine.Boxes;
import br.com.termia.construajogue.engine.Mesh;

/** Malhas pequenas compartilhadas pelos dois setores da campanha. */
final class GameMeshes {

    final Mesh drone = new Mesh(buildDrone());
    final Mesh mutant = new Mesh(buildMutant());
    final Mesh turret = new Mesh(buildTurret());
    final Mesh kamikaze = new Mesh(buildKamikaze());
    final Mesh boss = new Mesh(scaleMesh(buildDrone(), 1.7f));
    final Mesh human = new Mesh(buildHuman());
    final Mesh gun = new Mesh(buildGun());
    final Mesh flash = new Mesh(buildFlash());
    final Mesh impact = new Mesh(buildImpact());
    final Mesh health = new Mesh(buildHealth());
    final Mesh ammo = new Mesh(buildAmmo());
    final Mesh token = new Mesh(buildToken());
    final Mesh special = new Mesh(buildSpecial());
    final Mesh terminalLight = new Mesh(buildTerminalLight());
    final Mesh shadow = new Mesh(buildShadow());

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

    /** Pessoa low-poly amigável, com origem nos pés. */
    private static float[] buildHuman() {
        float[] out = new float[8 * Boxes.FLOATS_PER_BOX];
        int c = 0;
        c = Boxes.emitCentered(out, c, -0.12f, 0.42f, 0f,
                0.09f, 0.42f, 0.11f, 0.18f, 0.23f, 0.31f);
        c = Boxes.emitCentered(out, c, 0.12f, 0.42f, 0f,
                0.09f, 0.42f, 0.11f, 0.18f, 0.23f, 0.31f);
        c = Boxes.emitCentered(out, c, 0f, 1.08f, 0f,
                0.27f, 0.30f, 0.15f, 0.20f, 0.48f, 0.68f);
        c = Boxes.emitCentered(out, c, -0.34f, 1.04f, 0f,
                0.07f, 0.32f, 0.08f, 0.70f, 0.49f, 0.35f);
        c = Boxes.emitCentered(out, c, 0.34f, 1.04f, 0f,
                0.07f, 0.32f, 0.08f, 0.70f, 0.49f, 0.35f);
        c = Boxes.emitCentered(out, c, 0f, 1.55f, 0f,
                0.18f, 0.18f, 0.17f, 0.76f, 0.56f, 0.41f);
        c = Boxes.emitCentered(out, c, 0f, 1.73f, 0.025f,
                0.19f, 0.07f, 0.18f, 0.16f, 0.12f, 0.10f);
        Boxes.emitCentered(out, c, 0f, 1.55f, -0.18f,
                0.08f, 0.035f, 0.02f, 0.18f, 0.16f, 0.15f);
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

    private static float[] buildToken() {
        float[] out = new float[2 * Boxes.FLOATS_PER_BOX];
        int cursor = Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.18f, 0.04f, 0.18f, 0.20f, 0.75f, 1.15f);
        Boxes.emitCentered(out, cursor, 0f, 0.08f, 0f,
                0.10f, 0.04f, 0.10f, 0.85f, 1.05f, 1.25f);
        return out;
    }

    private static float[] buildSpecial() {
        float[] out = new float[3 * Boxes.FLOATS_PER_BOX];
        int cursor = Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.17f, 0.10f, 0.12f, 0.95f, 0.55f, 0.10f);
        cursor = Boxes.emitCentered(out, cursor, -0.08f, 0.14f, 0f,
                0.035f, 0.08f, 0.035f, 1.2f, 0.85f, 0.2f);
        Boxes.emitCentered(out, cursor, 0.08f, 0.14f, 0f,
                0.035f, 0.08f, 0.035f, 1.2f, 0.85f, 0.2f);
        return out;
    }

    private static float[] buildTurret() {
        float[] out = new float[4 * Boxes.FLOATS_PER_BOX];
        int c = Boxes.emitCentered(out, 0, 0f, -0.42f, 0f,
                0.32f, 0.12f, 0.32f, 0.24f, 0.28f, 0.34f);
        c = Boxes.emitCentered(out, c, 0f, -0.10f, 0f,
                0.16f, 0.24f, 0.16f, 0.32f, 0.38f, 0.46f);
        c = Boxes.emitCentered(out, c, 0f, 0.14f, 0f,
                0.30f, 0.16f, 0.24f, 0.52f, 0.18f, 0.14f);
        Boxes.emitCentered(out, c, 0f, 0.14f, -0.34f,
                0.07f, 0.07f, 0.18f, 0.16f, 0.17f, 0.20f);
        return out;
    }

    private static float[] buildKamikaze() {
        float[] out = new float[4 * Boxes.FLOATS_PER_BOX];
        int c = Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.24f, 0.16f, 0.24f, 0.88f, 0.36f, 0.10f);
        c = Boxes.emitCentered(out, c, -0.32f, 0f, 0f,
                0.09f, 0.09f, 0.09f, 1.1f, 0.52f, 0.12f);
        c = Boxes.emitCentered(out, c, 0.32f, 0f, 0f,
                0.09f, 0.09f, 0.09f, 1.1f, 0.52f, 0.12f);
        Boxes.emitCentered(out, c, 0f, -0.24f, 0f,
                0.07f, 0.10f, 0.07f, 1.2f, 0.2f, 0.08f);
        return out;
    }

    private static float[] scaleMesh(float[] mesh, float scale) {
        float[] out = mesh.clone();
        for (int i = 0; i < out.length; i += 9) {
            out[i] *= scale;
            out[i + 1] *= scale;
            out[i + 2] *= scale;
        }
        return out;
    }

    private static float[] buildTerminalLight() {
        float[] out = new float[Boxes.FLOATS_PER_BOX];
        Boxes.emitCentered(out, 0, 0f, 0f, 0f,
                0.10f, 0.10f, 0.10f, 1f, 1f, 1f);
        return out;
    }

    private static float[] buildShadow() {
        final int sides = 12;
        float[] out = new float[sides * 3 * 9];
        int cursor = 0;
        for (int i = 0; i < sides; i++) {
            double a = -Math.PI * 2.0 * i / sides;
            double b = -Math.PI * 2.0 * (i + 1) / sides;
            cursor = shadowVertex(out, cursor, 0f, 0f);
            cursor = shadowVertex(out, cursor,
                    0.46f * (float) Math.cos(a),
                    0.46f * (float) Math.sin(a));
            cursor = shadowVertex(out, cursor,
                    0.46f * (float) Math.cos(b),
                    0.46f * (float) Math.sin(b));
        }
        return out;
    }

    private static int shadowVertex(float[] out, int cursor,
                                    float x, float z) {
        out[cursor++] = x;
        out[cursor++] = 0f;
        out[cursor++] = z;
        out[cursor++] = 0f;
        out[cursor++] = 1f;
        out[cursor++] = 0f;
        out[cursor++] = 0.10f;
        out[cursor++] = 0.11f;
        out[cursor++] = 0.12f;
        return cursor;
    }
}
