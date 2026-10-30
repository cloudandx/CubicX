package org.cloudandx.cubicx.util;

public final class CubicMath {
    public static final int MIN_BLOCK = Integer.MIN_VALUE;
    public static final int MAX_BLOCK = Integer.MAX_VALUE;
    public static final int MIN_CUBE = Integer.MIN_VALUE >> 4;
    public static final int MAX_CUBE = Integer.MAX_VALUE >> 4;
    public static final int TOTAL_CUBE_COUNT = (MAX_CUBE - MIN_CUBE) + 1;

    private static final int FAST_BLOCK_X_MIN = -16384;
    private static final int FAST_BLOCK_X_MAX = 16383;
    private static final int FAST_BLOCK_Z_MIN = -32768;
    private static final int FAST_BLOCK_Z_MAX = 32767;

    private static final int FAST_CUBE_X_MIN = -65536;
    private static final int FAST_CUBE_X_MAX = 65535;
    private static final int FAST_CUBE_Z_MIN = -131072;
    private static final int FAST_CUBE_Z_MAX = 131071;

    private static final int CACHE_BITS = 18;
    private static final int CACHE_SIZE = 1 << CACHE_BITS;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private static final long[] BLOCK_KEYS = new long[CACHE_SIZE];
    private static final int[] BLOCK_X = new int[CACHE_SIZE];
    private static final int[] BLOCK_Y = new int[CACHE_SIZE];
    private static final int[] BLOCK_Z = new int[CACHE_SIZE];

    private static final long[] CUBE_KEYS = new long[CACHE_SIZE];
    private static final int[] CUBE_X = new int[CACHE_SIZE];
    private static final int[] CUBE_Y = new int[CACHE_SIZE];
    private static final int[] CUBE_Z = new int[CACHE_SIZE];

    private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
    private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME64_3 = 0x165667B19E3779F9L;
    private static final long PRIME64_4 = 0x85EBCA77C2B2AE63L;
    private static final long PRIME64_5 = 0x27D4EB2F165667C5L;

    private CubicMath() {
    }

    public static int blockToCube(int blockCoord) {
        return blockCoord >> 4;
    }

    public static int cubeToMinBlock(int cubeCoord) {
        return cubeCoord << 4;
    }

    public static int cubeToMaxBlock(int cubeCoord) {
        return (cubeCoord << 4) | 15;
    }

    public static int cubeToBlock(int cubeCoord, int localCoord) {
        return (cubeCoord << 4) | (localCoord & 15);
    }

    public static int blockToLocal(int blockCoord) {
        return blockCoord & 15;
    }

    public static int cubeToSectionIndex(int cubeCoord) {
        return cubeCoord + 4;
    }

    public static int sectionIndexToCube(int index) {
        return index - 4;
    }

    public static int clampAdd(int a, int b) {
        long sum = (long) a + (long) b;
        if (sum > MAX_BLOCK) {
            return MAX_BLOCK;
        }
        if (sum < MIN_BLOCK) {
            return MIN_BLOCK;
        }
        return (int) sum;
    }

    public static long packBlockPos(int x, int y, int z) {
        if (x >= FAST_BLOCK_X_MIN && x <= FAST_BLOCK_X_MAX && z >= FAST_BLOCK_Z_MIN && z <= FAST_BLOCK_Z_MAX) {
            return (((long) x & 0x7FFFL) << 48) | (((long) z & 0xFFFFL) << 32) | ((long) y & 0xFFFFFFFFL);
        }
        long key = hashBlockPos(x, y, z) | Long.MIN_VALUE;
        int slot = ((int) (key ^ (key >>> 32))) & CACHE_MASK;
        BLOCK_X[slot] = x;
        BLOCK_Y[slot] = y;
        BLOCK_Z[slot] = z;
        BLOCK_KEYS[slot] = key;
        return key;
    }

    public static int unpackBlockX(long node) {
        if (node >= 0L) {
            return (int) (node << 1 >> 49);
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return BLOCK_KEYS[slot] == node ? BLOCK_X[slot] : 0;
    }

    public static int unpackBlockY(long node) {
        if (node >= 0L) {
            return (int) node;
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return BLOCK_KEYS[slot] == node ? BLOCK_Y[slot] : 0;
    }

    public static int unpackBlockZ(long node) {
        if (node >= 0L) {
            return (int) (node << 16 >> 48);
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return BLOCK_KEYS[slot] == node ? BLOCK_Z[slot] : 0;
    }

    public static long packCubePos(int x, int y, int z) {
        if (x >= FAST_CUBE_X_MIN && x <= FAST_CUBE_X_MAX
            && z >= FAST_CUBE_Z_MIN && z <= FAST_CUBE_Z_MAX
            && y >= MIN_CUBE && y <= MAX_CUBE) {
            return (((long) x & 0x1FFFFL) << 46) | (((long) z & 0x3FFFFL) << 28) | ((long) y & 0xFFFFFFFL);
        }
        long key = hashCubePos(x, y, z) | Long.MIN_VALUE;
        int slot = ((int) (key ^ (key >>> 32))) & CACHE_MASK;
        CUBE_X[slot] = x;
        CUBE_Y[slot] = y;
        CUBE_Z[slot] = z;
        CUBE_KEYS[slot] = key;
        return key;
    }

    public static int unpackCubeX(long node) {
        if (node >= 0L) {
            return (int) (node << 1 >> 47);
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return CUBE_KEYS[slot] == node ? CUBE_X[slot] : 0;
    }

    public static int unpackCubeY(long node) {
        if (node >= 0L) {
            return (int) (node << 36 >> 36);
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return CUBE_KEYS[slot] == node ? CUBE_Y[slot] : 0;
    }

    public static int unpackCubeZ(long node) {
        if (node >= 0L) {
            return (int) (node << 18 >> 46);
        }
        int slot = ((int) (node ^ (node >>> 32))) & CACHE_MASK;
        return CUBE_KEYS[slot] == node ? CUBE_Z[slot] : 0;
    }

    public static long hashBlockPos(int x, int y, int z) {
        long h = PRIME64_5 + 12L;
        long k1 = ((long) x & 0xFFFFFFFFL) | (((long) y & 0xFFFFFFFFL) << 32);
        k1 *= PRIME64_2;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= PRIME64_1;
        h ^= k1;
        h = Long.rotateLeft(h, 27) * PRIME64_1 + PRIME64_4;

        long k2 = ((long) z & 0xFFFFFFFFL) * PRIME64_3;
        k2 = Long.rotateLeft(k2, 31);
        k2 *= PRIME64_1;
        h ^= k2;
        h = h * PRIME64_1 + PRIME64_4;

        h ^= h >>> 33;
        h *= PRIME64_2;
        h ^= h >>> 29;
        h *= PRIME64_3;
        h ^= h >>> 32;
        return h;
    }

    public static long hashCubePos(int cubeX, int cubeY, int cubeZ) {
        long h = PRIME64_5 ^ 0x43554245584833L;
        long v1 = ((long) cubeX & 0xFFFFFFFFL) | (((long) cubeY & 0xFFFFFFFFL) << 32);
        long v2 = (long) cubeZ & 0xFFFFFFFFL;

        h ^= mixStep(v1, PRIME64_2, PRIME64_1);
        h = Long.rotateLeft(h, 27) * PRIME64_1 + PRIME64_4;
        h ^= mixStep(v2, PRIME64_3, PRIME64_1);

        h ^= h >>> 33;
        h *= PRIME64_2;
        h ^= h >>> 29;
        h *= PRIME64_3;
        h ^= h >>> 32;
        return h;
    }

    private static long mixStep(long value, long p1, long p2) {
        long v = value * p1;
        v = Long.rotateLeft(v, 31);
        return v * p2;
    }
}