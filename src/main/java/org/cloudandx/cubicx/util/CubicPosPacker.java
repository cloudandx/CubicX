package org.cloudandx.cubicx.util;

public final class CubicPosPacker {
    private static final int PAGE_BITS = 12;
    private static final int MAX_PAGES = 4094;
    private static final int TABLE_SIZE = 8192;
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private static final int BLOCK_XZ_BITS = 18;
    private static final int BLOCK_Y_BITS = 16;
    private static final long BLOCK_XZ_MASK = (1L << BLOCK_XZ_BITS) - 1L;
    private static final long BLOCK_Y_MASK = (1L << BLOCK_Y_BITS) - 1L;

    private static final Object BLOCK_LOCK = new Object();
    private static final long[] BLOCK_HASH_KEYS = new long[TABLE_SIZE];
    private static final int[] BLOCK_HASH_PAGES = new int[TABLE_SIZE];
    private static final int[] BLOCK_PAGE_HX = new int[MAX_PAGES + 1];
    private static final int[] BLOCK_PAGE_HY = new int[MAX_PAGES + 1];
    private static final int[] BLOCK_PAGE_HZ = new int[MAX_PAGES + 1];
    private static int nextBlockPage = 1;
    private static volatile long mruBlockKey = 0L;
    private static volatile int mruBlockPage = 0;

    private static final Object SEC_LOCK = new Object();
    private static final long[] SEC_HASH_KEYS = new long[TABLE_SIZE];
    private static final int[] SEC_HASH_PAGES = new int[TABLE_SIZE];
    private static final int[] SEC_PAGE_HX = new int[MAX_PAGES + 1];
    private static final int[] SEC_PAGE_HY = new int[MAX_PAGES + 1];
    private static final int[] SEC_PAGE_HZ = new int[MAX_PAGES + 1];
    private static int nextSecPage = 1;
    private static volatile long mruSecKey = 0L;
    private static volatile int mruSecPage = 0;

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            BLOCK_HASH_KEYS[i] = EMPTY_KEY;
            SEC_HASH_KEYS[i] = EMPTY_KEY;
        }
    }

    private CubicPosPacker() {
    }

    public static long packBlock(final int x, final int y, final int z) {
        int hx = x >> BLOCK_XZ_BITS;
        int hy = y >> BLOCK_Y_BITS;
        int hz = z >> BLOCK_XZ_BITS;
        int page = (hx == 0 && hy == 0 && hz == 0) ? 0 : resolveBlockPage(hx, hy, hz);
        long lx = (long) x & BLOCK_XZ_MASK;
        long lz = (long) z & BLOCK_XZ_MASK;
        long ly = (long) y & BLOCK_Y_MASK;
        return ((long) page << 52) | (lx << 34) | (lz << 16) | ly;
    }

    public static int unpackBlockX(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int lx = (int) ((packed >>> 34) & BLOCK_XZ_MASK);
        return (BLOCK_PAGE_HX[page] << BLOCK_XZ_BITS) | lx;
    }

    public static int unpackBlockY(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int ly = (int) (packed & BLOCK_Y_MASK);
        return (BLOCK_PAGE_HY[page] << BLOCK_Y_BITS) | ly;
    }

    public static int unpackBlockZ(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int lz = (int) ((packed >>> 16) & BLOCK_XZ_MASK);
        return (BLOCK_PAGE_HZ[page] << BLOCK_XZ_BITS) | lz;
    }

    public static long packSection(final int secX, final int secY, final int secZ) {
        int hx = secX >> BLOCK_XZ_BITS;
        int hy = secY >> BLOCK_Y_BITS;
        int hz = secZ >> BLOCK_XZ_BITS;
        int page = (hx == 0 && hy == 0 && hz == 0) ? 0 : resolveSectionPage(hx, hy, hz);
        long lx = (long) secX & BLOCK_XZ_MASK;
        long lz = (long) secZ & BLOCK_XZ_MASK;
        long ly = (long) secY & BLOCK_Y_MASK;
        return ((long) page << 52) | (lx << 34) | (lz << 16) | ly;
    }

    public static int unpackSectionX(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int lx = (int) ((packed >>> 34) & BLOCK_XZ_MASK);
        return (SEC_PAGE_HX[page] << BLOCK_XZ_BITS) | lx;
    }

    public static int unpackSectionY(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int ly = (int) (packed & BLOCK_Y_MASK);
        return (SEC_PAGE_HY[page] << BLOCK_Y_BITS) | ly;
    }

    public static int unpackSectionZ(final long packed) {
        int page = (int) (packed >>> 52) & 0xFFF;
        int lz = (int) ((packed >>> 16) & BLOCK_XZ_MASK);
        return (SEC_PAGE_HZ[page] << BLOCK_XZ_BITS) | lz;
    }

    private static long composeHighKey(final int hx, final int hy, final int hz) {
        return (((long) hx & 0xFFFFFL) << 40) | (((long) hz & 0xFFFFFL) << 20) | ((long) hy & 0xFFFFFL);
    }

    private static int mixHash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) k & TABLE_MASK;
    }

    private static int resolveBlockPage(final int hx, final int hy, final int hz) {
        long key = composeHighKey(hx, hy, hz);
        if (key == mruBlockKey) {
            int cached = mruBlockPage;
            if (cached != 0) {
                return cached;
            }
        }
        synchronized (BLOCK_LOCK) {
            int slot = mixHash(key);
            while (true) {
                long k = BLOCK_HASH_KEYS[slot];
                if (k == key) {
                    int page = BLOCK_HASH_PAGES[slot];
                    mruBlockKey = key;
                    mruBlockPage = page;
                    return page;
                }
                if (k == EMPTY_KEY) {
                    int page = nextBlockPage;
                    nextBlockPage = (page >= MAX_PAGES) ? 1 : (page + 1);
                    BLOCK_PAGE_HX[page] = hx;
                    BLOCK_PAGE_HY[page] = hy;
                    BLOCK_PAGE_HZ[page] = hz;
                    BLOCK_HASH_KEYS[slot] = key;
                    BLOCK_HASH_PAGES[slot] = page;
                    mruBlockKey = key;
                    mruBlockPage = page;
                    return page;
                }
                slot = (slot + 1) & TABLE_MASK;
            }
        }
    }

    private static int resolveSectionPage(final int hx, final int hy, final int hz) {
        long key = composeHighKey(hx, hy, hz);
        if (key == mruSecKey) {
            int cached = mruSecPage;
            if (cached != 0) {
                return cached;
            }
        }
        synchronized (SEC_LOCK) {
            int slot = mixHash(key);
            while (true) {
                long k = SEC_HASH_KEYS[slot];
                if (k == key) {
                    int page = SEC_HASH_PAGES[slot];
                    mruSecKey = key;
                    mruSecPage = page;
                    return page;
                }
                if (k == EMPTY_KEY) {
                    int page = nextSecPage;
                    nextSecPage = (page >= MAX_PAGES) ? 1 : (page + 1);
                    SEC_PAGE_HX[page] = hx;
                    SEC_PAGE_HY[page] = hy;
                    SEC_PAGE_HZ[page] = hz;
                    SEC_HASH_KEYS[slot] = key;
                    SEC_HASH_PAGES[slot] = page;
                    mruSecKey = key;
                    mruSecPage = page;
                    return page;
                }
                slot = (slot + 1) & TABLE_MASK;
            }
        }
    }
}