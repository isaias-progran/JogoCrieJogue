package br.com.termia.construajogue.runtime;

/** Porta dinâmica com collider próprio e vetor completo de abertura. */
public final class RuntimeDoor {
    public final String id;
    public final String controllerId;
    public final int colliderIndex;
    public final float[] original;
    public final float[] vertexData;
    public final float moveX;
    public final float moveY;
    public final float moveZ;
    /** Abre por proximidade e volta a fechar quando o jogador se afasta. */
    public final boolean automatic;

    public RuntimeDoor(String id, String controllerId, int colliderIndex,
                       float[] original, float[] vertexData,
                       float moveX, float moveY, float moveZ,
                       boolean automatic) {
        this.id = id;
        this.controllerId = controllerId;
        this.colliderIndex = colliderIndex;
        this.original = original;
        this.vertexData = vertexData;
        this.moveX = moveX;
        this.moveY = moveY;
        this.moveZ = moveZ;
        this.automatic = automatic;
    }

    public float centerX() {
        return (original[0] + original[3]) / 2f;
    }

    public float centerZ() {
        return (original[2] + original[5]) / 2f;
    }

    public float centerY() {
        return (original[1] + original[4]) / 2f;
    }
}
