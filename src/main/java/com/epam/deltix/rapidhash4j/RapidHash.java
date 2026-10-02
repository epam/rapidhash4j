package com.epam.deltix.rapidhash4j;

import java.nio.ByteBuffer;

public final class RapidHash {

    private static final long DEFAULT_SEED = 0L;

    static {
        NativeLoader.load();
    }

    private RapidHash() {}

    private static native long nativeHash(byte[] data, int offset, int length, long seed);

    private static native long nativeHashDirect(ByteBuffer buffer, int offset, int length, long seed);

    public static long hash(byte[] data) {
        if (data == null) throw new NullPointerException("data");
        return nativeHash(data, 0, data.length, DEFAULT_SEED);
    }

    public static long hash(byte[] data, long seed) {
        if (data == null) throw new NullPointerException("data");
        return nativeHash(data, 0, data.length, seed);
    }

    public static long hash(byte[] data, int offset, int length) {
        validateArray(data, offset, length);
        return nativeHash(data, offset, length, DEFAULT_SEED);
    }

    public static long hash(byte[] data, int offset, int length, long seed) {
        validateArray(data, offset, length);
        return nativeHash(data, offset, length, seed);
    }

    public static long hash(ByteBuffer buffer) {
        return hash(buffer, DEFAULT_SEED);
    }

    public static long hash(ByteBuffer buffer, long seed) {
        if (buffer == null)
            throw new NullPointerException("buffer");

        int pos = buffer.position();
        int len = buffer.remaining();
        if (buffer.isDirect()) {
            return nativeHashDirect(buffer, pos, len, seed);
        } else if (buffer.hasArray()) {
            return nativeHash(buffer.array(), buffer.arrayOffset() + pos, len, seed);
        } else {
            byte[] tmp = new byte[len];
            buffer.duplicate().get(tmp);
            return nativeHash(tmp, 0, len, seed);
        }
    }

    private static void validateArray(byte[] data, int offset, int length) {
        if (data == null) throw new NullPointerException("data");
        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset=" + offset + " length=" + length + " array.length=" + data.length);
        }
    }
}
