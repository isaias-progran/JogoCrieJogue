package br.com.termia.construajogue.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Triangulação por corte de orelhas para os contornos desenhados
 * (pares x,z). Puro Java, testável no JVM. Polígonos são normalizados
 * para CCW (shoelace positivo com X à direita e Z para cima).
 */
public final class Triangulator {

    private Triangulator() {
    }

    /** Dobro da área com sinal (positivo = CCW). */
    public static float area2(float[] p) {
        float sum = 0f;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        return sum;
    }

    /** Cópia em ordem CCW. */
    public static float[] ccw(float[] p) {
        if (area2(p) >= 0f) {
            return p.clone();
        }
        int n = p.length / 2;
        float[] out = new float[p.length];
        for (int i = 0; i < n; i++) {
            out[i * 2] = p[(n - 1 - i) * 2];
            out[i * 2 + 1] = p[(n - 1 - i) * 2 + 1];
        }
        return out;
    }

    /** Índices dos triângulos (3 por triângulo); entrada CCW. */
    public static int[] earClip(float[] p) {
        int n = p.length / 2;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            idx.add(i);
        }
        int[] out = new int[(n - 2) * 3];
        int cursor = 0;
        int guard = 0;
        while (idx.size() > 3 && guard++ < 10000) {
            boolean clipped = false;
            int m = idx.size();
            for (int i = 0; i < m; i++) {
                int a = idx.get((i + m - 1) % m);
                int b = idx.get(i);
                int c = idx.get((i + 1) % m);
                if (cross(p, a, b, c) <= 0f) {
                    continue; // reflexo: não é orelha
                }
                boolean blocked = false;
                for (int other : idx) {
                    if (other != a && other != b && other != c
                            && inTriangle(p, a, b, c, other)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) {
                    continue;
                }
                out[cursor++] = a;
                out[cursor++] = b;
                out[cursor++] = c;
                idx.remove(i);
                clipped = true;
                break;
            }
            if (!clipped) {
                break; // degenerado: cai no leque abaixo
            }
        }
        if (idx.size() == 3) {
            out[cursor++] = idx.get(0);
            out[cursor++] = idx.get(1);
            out[cursor++] = idx.get(2);
        } else {
            for (int i = 1; i + 1 < idx.size(); i++) {
                out[cursor++] = idx.get(0);
                out[cursor++] = idx.get(i);
                out[cursor++] = idx.get(i + 1);
            }
        }
        int[] trimmed = new int[cursor];
        System.arraycopy(out, 0, trimmed, 0, cursor);
        return trimmed;
    }

    /** true se alguma aresta não adjacente cruza outra. */
    public static boolean selfIntersects(float[] p) {
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) {
                    continue;
                }
                if (segmentsCross(p, i, (i + 1) % n, j, (j + 1) % n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static float cross(float[] p, int a, int b, int c) {
        return (p[b * 2] - p[a * 2]) * (p[c * 2 + 1] - p[b * 2 + 1])
                - (p[b * 2 + 1] - p[a * 2 + 1]) * (p[c * 2] - p[b * 2]);
    }

    private static boolean inTriangle(float[] p, int a, int b, int c,
                                      int q) {
        float d1 = cross(p, a, b, q);
        float d2 = cross(p, b, c, q);
        float d3 = cross(p, c, a, q);
        return d1 >= 0f && d2 >= 0f && d3 >= 0f;
    }

    private static boolean segmentsCross(float[] p, int a, int b,
                                         int c, int d) {
        float d1 = cross(p, c, d, a);
        float d2 = cross(p, c, d, b);
        float d3 = cross(p, a, b, c);
        float d4 = cross(p, a, b, d);
        return ((d1 > 0f) != (d2 > 0f)) && ((d3 > 0f) != (d4 > 0f));
    }
}
