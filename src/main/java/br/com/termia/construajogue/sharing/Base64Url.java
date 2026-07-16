package br.com.termia.construajogue.sharing;

/** Base64 URL sem padding, disponível também no Android 7 (API 24/25). */
final class Base64Url {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                    .toCharArray();

    private Base64Url() {
    }

    static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 4 + 2) / 3);
        for (int i = 0; i < data.length; i += 3) {
            int remaining = data.length - i;
            int value = (data[i] & 0xFF) << 16;
            if (remaining > 1) value |= (data[i + 1] & 0xFF) << 8;
            if (remaining > 2) value |= data[i + 2] & 0xFF;
            out.append(ALPHABET[(value >>> 18) & 63]);
            out.append(ALPHABET[(value >>> 12) & 63]);
            if (remaining > 1) out.append(ALPHABET[(value >>> 6) & 63]);
            if (remaining > 2) out.append(ALPHABET[value & 63]);
        }
        return out.toString();
    }

    static byte[] decode(String text) {
        int length = text.length();
        if (length % 4 == 1) throw new IllegalArgumentException("base64");
        byte[] out = new byte[length * 3 / 4];
        int cursor = 0;
        int bits = 0;
        int bitCount = 0;
        for (int i = 0; i < length; i++) {
            int value = valueOf(text.charAt(i));
            if (value < 0) throw new IllegalArgumentException("base64");
            bits = (bits << 6) | value;
            bitCount += 6;
            if (bitCount >= 8) {
                bitCount -= 8;
                if (cursor < out.length) out[cursor++] =
                        (byte) (bits >>> bitCount);
            }
        }
        if (cursor != out.length) throw new IllegalArgumentException("base64");
        return out;
    }

    private static int valueOf(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= 'a' && value <= 'z') return value - 'a' + 26;
        if (value >= '0' && value <= '9') return value - '0' + 52;
        if (value == '-' || value == '+') return 62;
        if (value == '_' || value == '/') return 63;
        return -1;
    }
}
