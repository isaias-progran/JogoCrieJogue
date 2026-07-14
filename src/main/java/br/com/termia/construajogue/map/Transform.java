package br.com.termia.construajogue.map;

/** Posição em metros (Y para cima, Y na base) e yaw em graus no eixo Y. */
public final class Transform {
    public float x;
    public float y;
    public float z;
    public float yaw;

    public Transform() {
    }

    public Transform(float x, float y, float z, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public Transform copy() {
        return new Transform(x, y, z, yaw);
    }
}
