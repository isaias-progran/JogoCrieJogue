package br.com.termia.construajogue.engine;

import android.opengl.Matrix;

/**
 * Câmera em primeira pessoa: posição do olho + yaw/pitch.
 * Herda da OrbitCamera do dicom3d o clamp de pitch (±83°) que garante que a
 * base vetorial nunca degenera. Convenção y-para-cima: yaw 0 olha para -z e
 * cresce virando para a direita (+x).
 * Usada SOMENTE na thread GL (os deltas de toque chegam já sincronizados
 * pelo TouchControls), então não precisa de synchronized.
 */
public final class FpsCamera {

    /** ±83°: longe o bastante do polo para o lookAt nunca degenerar. */
    private static final float PITCH_LIMIT = 1.45f;

    private float yaw;
    private float pitch;
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    public void reset(float yawRadians) {
        yaw = yawRadians;
        pitch = 0f;
    }

    public void rotate(float dYaw, float dPitch) {
        yaw = (float) ((yaw + dYaw) % (2.0 * Math.PI));
        pitch = clamp(pitch + dPitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    public void setEye(float x, float y, float z) {
        eyeX = x;
        eyeY = y;
        eyeZ = z;
    }

    public float yaw() {
        return yaw;
    }

    /** Copia a posição do olho (para o uniforme da neblina). */
    public void eyeInto(float[] out3) {
        out3[0] = eyeX;
        out3[1] = eyeY;
        out3[2] = eyeZ;
    }

    /** Direção de visão normalizada (origem do hitscan). */
    public void forwardInto(float[] out3) {
        float cp = (float) Math.cos(pitch);
        out3[0] = cp * (float) Math.sin(yaw);
        out3[1] = (float) Math.sin(pitch);
        out3[2] = -cp * (float) Math.cos(yaw);
    }

    /** Matriz de visão a partir do olho e da direção yaw/pitch. */
    public void computeView(float[] out16) {
        float cp = (float) Math.cos(pitch);
        float fx = cp * (float) Math.sin(yaw);
        float fy = (float) Math.sin(pitch);
        float fz = -cp * (float) Math.cos(yaw);
        Matrix.setLookAtM(out16, 0, eyeX, eyeY, eyeZ,
                eyeX + fx, eyeY + fy, eyeZ + fz, 0f, 1f, 0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
