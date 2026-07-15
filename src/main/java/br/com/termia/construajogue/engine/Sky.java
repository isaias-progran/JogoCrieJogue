package br.com.termia.construajogue.engine;

import android.opengl.GLES30;

/**
 * Skybox procedural: cubo desenhado por dentro com gradiente
 * horizonte→zênite, disco do sol com brilho, lua e estrelas geradas no
 * fragment shader (hash da direção — sem textura). Desenhar ANTES do
 * cenário, com depth test/write e cull desligados, usando a viewProj
 * SEM translação. O horizonte usa a cor da neblina do mapa, então o
 * cenário some suavemente contra o céu.
 */
public final class Sky {

    private static final String VERTEX = ""
            + "#version 300 es\n"
            + "uniform mat4 uViewProj;\n"
            + "layout(location = 0) in vec3 aPos;\n"
            + "out vec3 vDir;\n"
            + "void main() {\n"
            + "  vDir = aPos;\n"
            + "  gl_Position = (uViewProj * vec4(aPos, 0.0)).xyww;\n"
            + "}\n";

    private static final String FRAGMENT = ""
            + "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec3 vDir;\n"
            + "uniform vec3 uHorizon;\n"
            + "uniform vec3 uZenith;\n"
            + "uniform vec3 uSunDir;\n"
            + "uniform vec3 uSunCol;\n"
            + "uniform vec3 uGlowCol;\n"
            + "uniform vec3 uMoonDir;\n"
            + "uniform float uDisc;\n"
            + "uniform float uMoon;\n"
            + "uniform float uStars;\n"
            + "out vec4 outColor;\n"
            + "void main() {\n"
            + "  vec3 d = normalize(vDir);\n"
            + "  float h = clamp(d.y, 0.0, 1.0);\n"
            + "  vec3 col = mix(uHorizon, uZenith, pow(h, 0.65));\n"
            + "  float sd = dot(d, uSunDir);\n"
            + "  col += smoothstep(uDisc, uDisc + 0.0006, sd) * uSunCol;\n"
            + "  col += pow(max(sd, 0.0), 48.0) * uGlowCol;\n"
            + "  if (uMoon > 0.0) {\n"
            + "    float md = dot(d, uMoonDir);\n"
            + "    col += uMoon * smoothstep(0.99960, 0.99982, md)\n"
            + "        * vec3(0.85, 0.90, 1.00);\n"
            + "    col += uMoon * pow(max(md, 0.0), 96.0)\n"
            + "        * vec3(0.08, 0.10, 0.16);\n"
            + "  }\n"
            + "  if (uStars > 0.0 && d.y > 0.02) {\n"
            + "    vec3 g = floor(d * 140.0);\n"
            + "    float hsh = fract(sin(dot(g,\n"
            + "        vec3(12.9898, 78.233, 37.719))) * 43758.5453);\n"
            + "    vec3 f = fract(d * 140.0) - 0.5;\n"
            + "    float star = step(0.998, hsh)\n"
            + "        * smoothstep(0.22, 0.04, length(f));\n"
            + "    col += uStars * star\n"
            + "        * (0.6 + 0.8 * fract(hsh * 91.7));\n"
            + "  }\n"
            + "  outColor = vec4(col, 1.0);\n"
            + "}\n";

    /** Mesma direção da luz difusa do cenário: sol e sombra combinam. */
    private static final float[] SUN_DAY = {0.37139f, 0.74278f, 0.55708f};
    private static final float[] SUN_DUSK = norm(0.70f, 0.10f, 0.55f);
    private static final float[] MOON = norm(-0.40f, 0.65f, -0.50f);

    private final int program;
    private final Mesh cube;
    private final int viewProjLoc;
    private final int horizonLoc;
    private final int zenithLoc;
    private final int sunDirLoc;
    private final int sunColLoc;
    private final int glowColLoc;
    private final int moonDirLoc;
    private final int discLoc;
    private final int moonLoc;
    private final int starsLoc;

    /** Criar na thread GL (onSurfaceCreated). */
    public Sky() {
        program = Shader.build(VERTEX, FRAGMENT);
        viewProjLoc = GLES30.glGetUniformLocation(program, "uViewProj");
        horizonLoc = GLES30.glGetUniformLocation(program, "uHorizon");
        zenithLoc = GLES30.glGetUniformLocation(program, "uZenith");
        sunDirLoc = GLES30.glGetUniformLocation(program, "uSunDir");
        sunColLoc = GLES30.glGetUniformLocation(program, "uSunCol");
        glowColLoc = GLES30.glGetUniformLocation(program, "uGlowCol");
        moonDirLoc = GLES30.glGetUniformLocation(program, "uMoonDir");
        discLoc = GLES30.glGetUniformLocation(program, "uDisc");
        moonLoc = GLES30.glGetUniformLocation(program, "uMoon");
        starsLoc = GLES30.glGetUniformLocation(program, "uStars");
        // cubo unitário; normal/cor do formato do Mesh ficam sem uso
        float[] data = new float[Boxes.FLOATS_PER_BOX];
        Boxes.emitBounds(data, 0,
                new float[]{-1f, -1f, -1f, 1f, 1f, 1f}, 0f, 0f, 0f);
        cube = new Mesh(data);
    }

    /** viewProj SEM translação; mode = RuntimeLevel.SKY_*. */
    public void draw(float[] viewProjNoTranslation, int mode,
                     float[] horizon) {
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(viewProjLoc, 1, false,
                viewProjNoTranslation, 0);
        GLES30.glUniform3f(horizonLoc, horizon[0], horizon[1], horizon[2]);
        switch (mode) {
            case 2: // entardecer: sol baixo e quente, primeiras estrelas
                set(zenithLoc, 0.18f, 0.15f, 0.30f);
                GLES30.glUniform3fv(sunDirLoc, 1, SUN_DUSK, 0);
                set(sunColLoc, 1.7f, 1.1f, 0.6f);
                set(glowColLoc, 0.50f, 0.25f, 0.10f);
                GLES30.glUniform1f(discLoc, 0.9990f);
                GLES30.glUniform1f(moonLoc, 0f);
                GLES30.glUniform1f(starsLoc, 0.35f);
                break;
            case 3: // noite: lua, céu estrelado, sem sol
                set(zenithLoc, 0.008f, 0.012f, 0.035f);
                GLES30.glUniform3fv(sunDirLoc, 1, MOON, 0);
                set(sunColLoc, 0f, 0f, 0f);
                set(glowColLoc, 0f, 0f, 0f);
                GLES30.glUniform1f(discLoc, 2f); // sol nunca aparece
                GLES30.glUniform3fv(moonDirLoc, 1, MOON, 0);
                GLES30.glUniform1f(moonLoc, 1f);
                GLES30.glUniform1f(starsLoc, 1f);
                break;
            default: // dia: sol pleno alinhado com a luz do cenário
                set(zenithLoc, 0.25f, 0.45f, 0.78f);
                GLES30.glUniform3fv(sunDirLoc, 1, SUN_DAY, 0);
                set(sunColLoc, 1.6f, 1.5f, 1.2f);
                set(glowColLoc, 0.30f, 0.25f, 0.12f);
                GLES30.glUniform1f(discLoc, 0.9993f);
                GLES30.glUniform1f(moonLoc, 0f);
                GLES30.glUniform1f(starsLoc, 0f);
                break;
        }
        cube.draw();
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
    }

    private static void set(int loc, float r, float g, float b) {
        GLES30.glUniform3f(loc, r, g, b);
    }

    private static float[] norm(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / len, y / len, z / len};
    }
}
