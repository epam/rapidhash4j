package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RapidHashStreamTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 7, 8, 15, 16, 17, 32, 48, 64, 96, 112})
    void singleUpdateMatchesOneShot_shortPath(int size) {
        byte[] data = testData(size);
        try (RapidHashStream stream = new RapidHashStream()) {
            long streamHash = stream.update(data).finish();
            assertEquals(RapidHash.hash(data), streamHash, "Mismatch for size=" + size);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {113, 128, 200, 224, 225, 256, 336, 512, 1024, 4096, 65536, 1048576})
    void singleUpdateMatchesOneShot_longPath(int size) {
        byte[] data = testData(size);
        try (RapidHashStream stream = new RapidHashStream()) {
            long streamHash = stream.update(data).finish();
            assertEquals(RapidHash.hash(data), streamHash, "Mismatch for size=" + size);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 13, 16, 32, 55, 56, 64, 100, 111, 112, 113, 128, 200, 224})
    void chunkedUpdateMatchesOneShot(int chunkSize) {
        byte[] data = testData(1024);
        long expected = RapidHash.hash(data);

        try (RapidHashStream stream = new RapidHashStream()) {
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(chunkSize, data.length - offset);
                stream.update(data, offset, len);
                offset += len;
            }
            assertEquals(expected, stream.finish(), "Mismatch for chunkSize=" + chunkSize);
        }
    }

    @Test
    void byteAtATimeMatchesOneShot() {
        byte[] data = testData(300);
        long expected = RapidHash.hash(data);

        try (RapidHashStream stream = new RapidHashStream()) {
            for (byte b : data) {
                stream.update(new byte[]{b});
            }
            assertEquals(expected, stream.finish());
        }
    }

    @Test
    void seededStreamMatchesOneShot() {
        byte[] data = testData(500);
        long seed = 42L;
        long expected = RapidHash.hash(data, seed);

        try (RapidHashStream stream = new RapidHashStream(seed)) {
            assertEquals(expected, stream.update(data).finish());
        }
    }

    @Test
    void finishAutoResetsPreservingSeed() {
        byte[] data = testData(200);
        long expected = RapidHash.hash(data, 42L);

        try (RapidHashStream stream = new RapidHashStream(42L)) {
            assertEquals(expected, stream.update(data).finish());
            assertEquals(RapidHash.hash(new byte[0], 42L), stream.finish());
            assertEquals(expected, stream.update(data).finish());
            assertEquals(expected, stream.update(data).finish());
        }
    }

    @Test
    void explicitResetPreservesSeed() {
        byte[] data = testData(200);
        try (RapidHashStream stream = new RapidHashStream(42L)) {
            stream.update(data);
            stream.reset();

            assertEquals(RapidHash.hash(new byte[0], 42L), stream.finish());
            assertEquals(RapidHash.hash(data, 42L), stream.update(data).finish());
        }
    }

    @Test
    void resetWithNewSeed() {
        byte[] data = testData(200);
        try (RapidHashStream stream = new RapidHashStream()) {
            stream.update(data);
            stream.reset(99L);
            long expected = RapidHash.hash(data, 99L);
            assertEquals(expected, stream.update(data).finish());

            stream.update(data);
            stream.reset();
            assertEquals(expected, stream.update(data).finish());

            stream.reset(0L);
            assertEquals(RapidHash.hash(data), stream.update(data).finish());
        }
    }

    @Test
    void directByteBufferUpdate() {
        byte[] data = testData(300);
        long expected = RapidHash.hash(data);

        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).flip();

        try (RapidHashStream stream = new RapidHashStream()) {
            assertEquals(expected, stream.update(direct).finish());
        }
    }

    @Test
    void directByteBufferChunked() {
        byte[] data = testData(500);
        long expected = RapidHash.hash(data);

        try (RapidHashStream stream = new RapidHashStream()) {
            ByteBuffer buf = ByteBuffer.allocateDirect(100);
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(100, data.length - offset);
                buf.clear();
                buf.put(data, offset, len).flip();
                stream.update(buf);
                offset += len;
            }
            assertEquals(expected, stream.finish());
        }
    }

    @Test
    void closePreventsFurtherUse() {
        RapidHashStream stream = new RapidHashStream();
        stream.close();
        assertThrows(IllegalStateException.class, () -> stream.update(new byte[1]));
        assertThrows(IllegalStateException.class, () -> stream.update(new byte[1], 0, 1));
        assertThrows(IllegalStateException.class, () -> stream.update(ByteBuffer.allocateDirect(1)));
        assertThrows(IllegalStateException.class, stream::finish);
        assertThrows(IllegalStateException.class, stream::reset);
        assertThrows(IllegalStateException.class, () -> stream.reset(42L));
    }

    @Test
    void doubleCloseIsSafe() {
        RapidHashStream stream = new RapidHashStream();
        stream.close();
        assertDoesNotThrow(stream::close);
    }

    @Test
    void emptyFinish() {
        try (RapidHashStream stream = new RapidHashStream()) {
            assertEquals(RapidHash.hash(new byte[0]), stream.finish());
        }
    }

    @Test
    void exactBlockBoundary() {
        byte[] data = testData(112);
        long expected = RapidHash.hash(data);
        try (RapidHashStream stream = new RapidHashStream()) {
            stream.update(data, 0, 56);
            stream.update(data, 56, 56);
            assertEquals(expected, stream.finish());
        }
    }

    @Test
    void twoExactBlocks() {
        byte[] data = testData(224);
        long expected = RapidHash.hash(data);
        try (RapidHashStream stream = new RapidHashStream()) {
            stream.update(data, 0, 112);
            stream.update(data, 112, 112);
            assertEquals(expected, stream.finish());
        }
    }

    @Test
    void threeExactBlocks() {
        byte[] data = testData(336);
        long expected = RapidHash.hash(data);
        try (RapidHashStream stream = new RapidHashStream()) {
            stream.update(data, 0, 112);
            stream.update(data, 112, 112);
            stream.update(data, 224, 112);
            assertEquals(expected, stream.finish());
        }
    }

    @Test
    void independentStreamsWorkConcurrently() throws InterruptedException {
        byte[] data = testData(1024);
        long expected = RapidHash.hash(data);
        int threads = 8;
        int iterations = 50_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try (RapidHashStream stream = new RapidHashStream()) {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (stream.update(data).finish() != expected) {
                            failed.set(true);
                            return;
                        }
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();
        assertFalse(failed.get(), "Streaming hash mismatch under concurrent use");
    }

    @ParameterizedTest
    @ValueSource(ints = {113, 150, 224, 300, 500, 1024})
    void variousChunkSizes_largeInput(int totalSize) {
        byte[] data = testData(totalSize);
        long expected = RapidHash.hash(data);
        Random rng = new Random(totalSize);

        try (RapidHashStream stream = new RapidHashStream()) {
            int offset = 0;
            while (offset < data.length) {
                int chunk = 1 + rng.nextInt(50);
                int len = Math.min(chunk, data.length - offset);
                stream.update(data, offset, len);
                offset += len;
            }
            assertEquals(expected, stream.finish(),
                    "Mismatch for totalSize=" + totalSize + " with random chunks");
        }
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, -1", "5, 6", "11, 0", "2147483647, 1",
                "1, 2147483647", "2147483647, 2147483647"})
    void invalidRangeLeavesStreamUnchanged(int offset, int length) {
        byte[] data = testData(10);
        try (RapidHashStream stream = new RapidHashStream(42L)) {
            stream.update(data);
            assertThrows(ArrayIndexOutOfBoundsException.class,
                    () -> stream.update(data, offset, length));
            assertEquals(RapidHash.hash(data, 42L), stream.finish());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 42L, -1L, Long.MIN_VALUE, Long.MAX_VALUE})
    void everySplitMatchesOneShotAtThresholds(long seed) {
        int[] sizes = { 0, 1, 3, 4, 7, 8, 15, 16, 17, 31, 32, 33, 47, 48, 49,
                        63, 64, 65, 79, 80, 81, 95, 96, 97, 111, 112, 113,
                        223, 224, 225, 335, 336, 337, 1024 };
        Random random = new Random(12345);
        try (RapidHashStream stream = new RapidHashStream(seed)) {
            for (int size : sizes) {
                byte[] data = new byte[size];
                random.nextBytes(data);
                long expected = RapidHash.hash(data, seed);
                for (int split = 0; split <= size; split++) {
                    stream.update(data, 0, split);
                    stream.update(data, split, size - split);
                    stream.update(data, size, 0);
                    assertEquals(expected, stream.finish(),
                            "size=" + size + " split=" + split + " seed=" + seed);
                }
            }
        }
    }

    @Test
    void mixedBufferChunksPreservePositionsAndLimits() {
        byte[] data = testData(339);
        ByteBuffer heap = ByteBuffer.wrap(data, 0, 113).slice();
        ByteBuffer readOnly = ByteBuffer.wrap(data, 113, 113).slice().asReadOnlyBuffer();
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).flip();
        direct.position(226);
        try (RapidHashStream stream = new RapidHashStream(42L)) {
            for (ByteBuffer buffer : new ByteBuffer[]{heap, readOnly, direct}) {
                int position = buffer.position();
                int limit = buffer.limit();
                stream.update(buffer);
                assertEquals(position, buffer.position());
                assertEquals(limit, buffer.limit());
            }
            assertEquals(RapidHash.hash(data, 42L), stream.finish());
        }
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/rapidhash-v3.csv")
    void matchesV3Reference(int size, String seedHex, String hashHex) {
        long seed = Long.parseUnsignedLong(seedHex, 16);
        long expected = Long.parseUnsignedLong(hashHex, 16);

        byte[] data = new byte[size];

        int state = 0x12345678;
        for (int i = 0; i < size; i++) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            data[i] = (byte) state;
        }

        ByteBuffer direct = ByteBuffer.allocateDirect(size);
        direct.put(data).flip();

        assertEquals(expected, RapidHash.hash(data, seed), "one-shot array");
        assertEquals(expected, RapidHash.hash(direct, seed), "one-shot direct buffer");

        try (RapidHashStream stream = new RapidHashStream(seed)) {
            assertEquals(expected, stream.update(data).finish(), "stream single update");

            Random chunks = new Random(seed ^ size);
            int offset = 0;
            while (offset < size) {
                int length = Math.min(1 + chunks.nextInt(257), size - offset);
                stream.update(data, offset, length);
                offset += length;
            }

            assertEquals(expected, stream.finish(), "stream array chunks");

            direct.position(0);
            while (direct.position() < size) {
                direct.limit(Math.min(size, direct.position() + 1 + chunks.nextInt(257)));
                stream.update(direct);
                direct.position(direct.limit());
            }

            assertEquals(expected, stream.finish(), "stream direct buffer chunks");
        }
    }

    private static byte[] testData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 31 + 17);
        }
        return data;
    }
}
