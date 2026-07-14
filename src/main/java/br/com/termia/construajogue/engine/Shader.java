package br.com.termia.construajogue.engine;

import android.opengl.GLES30;

/**
 * Compilação e link de shaders ES 3.0 com mensagens de erro legíveis.
 * Transplantado do GlProgram do dicom3d (comprovado neste aparelho).
 * Falha vira exceção com o log do driver — nunca tela preta silenciosa.
 */
public final class Shader {

    private Shader() {
    }

    /** Compila e linka; falha vira exceção com o log do driver. */
    public static int build(String vertexSource, String fragmentSource) {
        int vertex = compile(GLES30.GL_VERTEX_SHADER, "vertex", vertexSource);
        int fragment = compile(GLES30.GL_FRAGMENT_SHADER, "fragment",
                fragmentSource);
        int program = GLES30.glCreateProgram();
        if (program == 0) {
            throw new IllegalStateException("glCreateProgram devolveu 0");
        }
        GLES30.glAttachShader(program, vertex);
        GLES30.glAttachShader(program, fragment);
        GLES30.glLinkProgram(program);
        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
        int[] linked = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new IllegalStateException("Falha ao linkar programa GL: " + log);
        }
        return program;
    }

    private static int compile(int type, String name, String source) {
        int shader = GLES30.glCreateShader(type);
        if (shader == 0) {
            throw new IllegalStateException("glCreateShader devolveu 0");
        }
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(shader);
            GLES30.glDeleteShader(shader);
            throw new IllegalStateException(
                    "Falha no shader " + name + ": " + log);
        }
        return shader;
    }
}
