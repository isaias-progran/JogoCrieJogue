package br.com.termia.construajogue;

import java.util.Arrays;

/** Mini-asserts dos testes JVM (sem JUnit neste ambiente). */
public final class Check {

    private static int passed;

    private Check() {
    }

    public static void that(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("FALHOU: " + what);
        }
        passed++;
    }

    public static void equal(Object actual, Object expected, String what) {
        that(actual == null ? expected == null : actual.equals(expected),
                what + " (obtido " + actual + ", esperado " + expected + ")");
    }

    public static void sameFloats(float[] actual, float[] expected,
                                  String what) {
        that(Arrays.equals(actual, expected), what + " (obtido "
                + Arrays.toString(actual) + ", esperado "
                + Arrays.toString(expected) + ")");
    }

    public static void sameRows(float[][] actual, float[][] expected,
                                String what) {
        that(actual.length == expected.length,
                what + ": quantidade de linhas");
        for (int i = 0; i < actual.length; i++) {
            sameFloats(actual[i], expected[i], what + " linha " + i);
        }
    }

    public static void done(String name) {
        System.out.println("OK " + name + ": " + passed + " verificações");
    }

    public static void fails(Runnable action, String what) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            passed++;
            return;
        }
        throw new AssertionError("FALHOU: " + what + " deveria lançar erro");
    }
}
