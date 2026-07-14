package br.com.termia.construajogue.engine;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Malha estática intercalada: posição(3) + normal(3) + cor(3) por vértice.
 * Upload por ByteBuffer direto com GL_STATIC_DRAW (padrão do dicom3d).
 * Criar SOMENTE na thread GL; após perda de contexto, criar de novo.
 */
public final class Mesh {

    private static final int FLOATS_PER_VERTEX = 9;
    private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;

    private final int buffer;
    private final int vertexCount;

    public Mesh(float[] interleaved) {
        vertexCount = interleaved.length / FLOATS_PER_VERTEX;
        FloatBuffer data = ByteBuffer.allocateDirect(interleaved.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(interleaved);
        data.position(0);
        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, interleaved.length * 4,
                data, GLES30.GL_STATIC_DRAW);
        buffer = buffers[0];
    }

    /** Atributos fixos: 0=posição, 1=normal, 2=cor. */
    public void draw() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false,
                STRIDE_BYTES, 0);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false,
                STRIDE_BYTES, 12);
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false,
                STRIDE_BYTES, 24);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount);
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);
        GLES30.glDisableVertexAttribArray(2);
    }

    /** Libera o VBO ao trocar de nível dentro do mesmo contexto GL. */
    public void release() {
        int[] buffers = {buffer};
        GLES30.glDeleteBuffers(1, buffers, 0);
    }
}
