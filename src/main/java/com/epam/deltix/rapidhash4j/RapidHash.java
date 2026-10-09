package com.epam.deltix.rapidhash4j;

import java.nio.ByteBuffer;

public final class RapidHash {

    private static final long DEFAULT_SEED = 0L;

    private static final Throwable LOAD_ERROR = NativeLoader.loadError();

    private RapidHash() {}

    /**
     * Attempts native loading once per class loader and returns the cached outcome.
     * @return null if loaded, otherwise a cached diagnostic with the original failure in its cause chain
     */
    public static Throwable loadError() {
        return LOAD_ERROR;
    }

    /**
     * @return {@code loadError() == null}
     */
    public static boolean isAvailable() {
        return LOAD_ERROR == null;
    }

    private static native long nativeHash(byte[] data, int offset, int length, long seed);

    private static native long nativeHashDirect(ByteBuffer buffer, int offset, int length, long seed);

    public static long hash(byte[] data) {
        return hash(data, DEFAULT_SEED);
    }

    public static long hash(byte[] data, long seed) {
        if (LOAD_ERROR != null) throw NativeLoader.unavailable();
        if (data == null) throw new NullPointerException("data");
        return nativeHash(data, 0, data.length, seed);
    }

    public static long hash(byte[] data, int offset, int length) {
        return hash(data, offset, length, DEFAULT_SEED);
    }

    public static long hash(byte[] data, int offset, int length, long seed) {
        if (LOAD_ERROR != null) throw NativeLoader.unavailable();
        validateArray(data, offset, length);
        return nativeHash(data, offset, length, seed);
    }

    public static long hash(ByteBuffer buffer) {
        return hash(buffer, DEFAULT_SEED);
    }

    public static long hash(ByteBuffer buffer, long seed) {
        if (LOAD_ERROR != null) throw NativeLoader.unavailable();
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
