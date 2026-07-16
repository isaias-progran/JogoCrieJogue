package br.com.termia.construajogue.sharing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerador QR offline em modo byte, correção L e máscara 0. Suporta versões
 * 1–40 (até cerca de 2,9 KB); não depende de biblioteca nem de rede.
 */
public final class QrCode {

    private static final int[] ECC_CODEWORDS_PER_BLOCK = {-1,
            7, 10, 15, 20, 26, 18, 20, 24, 30,
            18, 20, 24, 26, 30, 22, 24, 28, 30, 28,
            28, 28, 28, 30, 30, 26, 28, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30};
    private static final int[] NUM_ERROR_CORRECTION_BLOCKS = {-1,
            1, 1, 1, 1, 1, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 6, 6, 6, 6, 7,
            8, 8, 9, 9, 10, 12, 12, 12, 13, 14,
            15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25};

    public final int size;
    private final int version;
    private final boolean[][] modules;
    private final boolean[][] function;

    private QrCode(int version, byte[] dataCodewords) {
        this.version = version;
        size = version * 4 + 17;
        modules = new boolean[size][size];
        function = new boolean[size][size];
        drawFunctionPatterns();
        byte[] allCodewords = addEccAndInterleave(dataCodewords);
        drawCodewords(allCodewords);
        applyMask0();
        drawFormatBits(0);
    }

    public static QrCode encodeText(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version;
        int dataCapacityBits = 0;
        for (version = 1; version <= 40; version++) {
            int countBits = version <= 9 ? 8 : 16;
            dataCapacityBits = getNumDataCodewords(version) * 8;
            if (4 + countBits + data.length * 8 <= dataCapacityBits
                    && data.length < (1 << countBits)) break;
        }
        if (version > 40) {
            throw new IllegalArgumentException(
                    "mapa grande demais para um único QR");
        }
        BitBuffer bits = new BitBuffer();
        bits.append(0x4, 4); // modo byte
        bits.append(data.length, version <= 9 ? 8 : 16);
        for (byte value : data) bits.append(value & 0xFF, 8);
        bits.append(0, Math.min(4, dataCapacityBits - bits.size()));
        bits.append(0, (8 - bits.size() % 8) % 8);
        boolean toggle = true;
        while (bits.size() < dataCapacityBits) {
            bits.append(toggle ? 0xEC : 0x11, 8);
            toggle = !toggle;
        }
        return new QrCode(version, bits.toBytes());
    }

    public boolean module(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] align = alignmentPositions(version);
        for (int i = 0; i < align.length; i++) {
            for (int j = 0; j < align.length; j++) {
                if ((i == 0 && j == 0)
                        || (i == 0 && j == align.length - 1)
                        || (i == align.length - 1 && j == 0)) continue;
                drawAlignment(align[i], align[j]);
            }
        }
        drawFormatBits(0); // também reserva os módulos
        drawVersion();
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(cx + dx, cy + dy,
                        Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask) {
        int data = (1 << 3) | mask; // nível L = 01
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean value = bit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunction(a, b, value);
            setFunction(b, a, value);
        }
    }

    private void drawCodewords(byte[] data) {
        int bitIndex = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vertical = 0; vertical < size; vertical++) {
                boolean upward = ((right + 1) & 2) == 0;
                int y = upward ? size - 1 - vertical : vertical;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (!function[y][x] && bitIndex < data.length * 8) {
                        modules[y][x] = ((data[bitIndex >>> 3]
                                >>> (7 - (bitIndex & 7))) & 1) != 0;
                        bitIndex++;
                    }
                }
            }
        }
        if (bitIndex != data.length * 8) {
            throw new IllegalStateException("dados QR não couberam");
        }
    }

    private void applyMask0() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y][x] && (x + y) % 2 == 0) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS[version];
        int blockEccLen = ECC_CODEWORDS_PER_BLOCK[version];
        int rawCodewords = getNumRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;
        byte[] divisor = reedSolomonDivisor(blockEccLen);
        List<byte[]> blocks = new ArrayList<>();
        int dataIndex = 0;
        for (int i = 0; i < numBlocks; i++) {
            int dataLength = shortBlockLen - blockEccLen
                    + (i < numShortBlocks ? 0 : 1);
            byte[] blockData = new byte[dataLength];
            System.arraycopy(data, dataIndex, blockData, 0, dataLength);
            dataIndex += dataLength;
            byte[] ecc = reedSolomonRemainder(blockData, divisor);
            byte[] block = new byte[shortBlockLen + 1];
            System.arraycopy(blockData, 0, block, 0, blockData.length);
            System.arraycopy(ecc, 0, block, block.length - ecc.length,
                    ecc.length);
            blocks.add(block);
        }
        if (dataIndex != data.length) throw new IllegalStateException();
        byte[] result = new byte[rawCodewords];
        int out = 0;
        for (int i = 0; i < blocks.get(0).length; i++) {
            for (int j = 0; j < blocks.size(); j++) {
                if (i != shortBlockLen - blockEccLen
                        || j >= numShortBlocks) {
                    result[out++] = blocks.get(j)[i];
                }
            }
        }
        if (out != result.length) throw new IllegalStateException();
        return result;
    }

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) result[j] ^= result[j + 1];
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte value : data) {
            int factor = (value ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void setFunction(int x, int y, boolean value) {
        modules[y][x] = value;
        function[y][x] = true;
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    private static int[] alignmentPositions(int version) {
        if (version == 1) return new int[0];
        int count = version / 7 + 2;
        int step = version == 32 ? 26
                : (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2;
        int[] result = new int[count];
        result[0] = 6;
        for (int i = count - 1, pos = version * 4 + 10;
             i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    private static int getNumDataCodewords(int version) {
        return getNumRawDataModules(version) / 8
                - ECC_CODEWORDS_PER_BLOCK[version]
                * NUM_ERROR_CORRECTION_BLOCKS[version];
    }

    private static int getNumRawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int align = version / 7 + 2;
            result -= (25 * align - 10) * align - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    private static final class BitBuffer {
        private final List<Boolean> bits = new ArrayList<>();

        int size() { return bits.size(); }

        void append(int value, int length) {
            if (length < 0 || length > 31 || value >>> length != 0) {
                throw new IllegalArgumentException();
            }
            for (int i = length - 1; i >= 0; i--) {
                bits.add(((value >>> i) & 1) != 0);
            }
        }

        byte[] toBytes() {
            byte[] result = new byte[(bits.size() + 7) / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) result[i >>> 3] |= 1 << (7 - (i & 7));
            }
            return result;
        }
    }
}
