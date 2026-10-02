package com.epam.deltix.rapidhash4j;

import java.nio.ByteBuffer;

/**
 * Incremental rapidhash V3. Hashing chunks produces the same result as
 * {@link RapidHash} hashing their concatenation with the same seed.
 *
 * <p>Instances are not thread-safe. Confine each instance to one writer thread
 * and use try-with-resources to release its native memory. Input bytes must
 * remain unchanged until an update returns; they can then be reused.
 * After close, updates, finish and resets throw {@link IllegalStateException}.
 */
public final class RapidHashStream implements AutoCloseable {

    private long nativeState;
    private long seed;

    static {
        NativeLoader.load();
    }

    /** Creates an empty stream with seed zero. */
    public RapidHashStream() {
        this(0L);
    }

    /**
     * Creates an empty stream with the given seed.
     * @throws OutOfMemoryError if native state cannot be allocated
     */
    public RapidHashStream(long seed) {
        nativeState = nativeStreamInit(seed);
        if (nativeState == 0) throw new OutOfMemoryError("Cannot allocate RapidHashStream native state");
        this.seed = seed;
    }

    /** Appends all bytes in the array. */
    public RapidHashStream update(byte[] data) {
        if (data == null) throw new NullPointerException("data");
        checkOpen();
        nativeStreamUpdate(nativeState, data, 0, data.length);
        return this;
    }

    /** Appends the given array slice. Empty slices are allowed. */
    public RapidHashStream update(byte[] data, int offset, int length) {
        validateArray(data, offset, length);
        checkOpen();
        nativeStreamUpdate(nativeState, data, offset, length);
        return this;
    }

    /** Appends the bytes between position and limit without changing either. */
    public RapidHashStream update(ByteBuffer buffer) {
        if (buffer == null)
            throw new NullPointerException("buffer");
        checkOpen();
        int pos = buffer.position();
        int len = buffer.remaining();
        if (buffer.isDirect()) {
            nativeStreamUpdateDirect(nativeState, buffer, pos, len);
        } else if (buffer.hasArray()) {
            nativeStreamUpdate(nativeState, buffer.array(), buffer.arrayOffset() + pos, len);
        } else {
            byte[] tmp = new byte[len];
            buffer.duplicate().get(tmp);
            nativeStreamUpdate(nativeState, tmp, 0, len);
        }
        return this;
    }

    /** Returns the digest and resets to an empty stream, preserving the current seed. */
    public long finish() {
        checkOpen();
        return nativeStreamFinish(nativeState);
    }

    /** Discards accumulated bytes, preserving the current seed. */
    public void reset() {
        reset(seed);
    }

    /** Discards accumulated bytes and changes the seed for subsequent hashes. */
    public void reset(long seed) {
        checkOpen();
        nativeStreamReset(nativeState, seed);
        this.seed = seed;
    }

    /** Releases native memory. Repeated calls have no effect. */
    @Override
    public void close() {
        if (nativeState != 0) {
            nativeStreamFree(nativeState);
            nativeState = 0;
        }
    }

    private void checkOpen() {
        if (nativeState == 0) throw new IllegalStateException("RapidHashStream is closed");
    }

    private static void validateArray(byte[] data, int offset, int length) {
        if (data == null)
            throw new NullPointerException("data");

        if (offset < 0 || length < 0 || offset > data.length - length) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset=" + offset + " length=" + length + " array.length=" + data.length);
        }
    }

    private static native long nativeStreamInit(long seed);

    private static native void nativeStreamUpdate(long state, byte[] data, int offset, int length);

    private static native void nativeStreamUpdateDirect(long state, ByteBuffer buffer, int offset, int length);

    private static native long nativeStreamFinish(long state);

    private static native void nativeStreamReset(long state, long seed);

    private static native void nativeStreamFree(long state);
}
