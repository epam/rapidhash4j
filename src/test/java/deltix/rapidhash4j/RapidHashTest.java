package deltix.rapidhash4j;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RapidHashTest {

    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] QUICK_FOX = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

    @Test
    void hashEmpty() {
        assertEquals(0x0338dc4be2cecdaeL, RapidHash.hash(new byte[0]));
    }

    @Test
    void hashSingleByte() {
        assertEquals(0xdf39ad7f42b5c997L, RapidHash.hash(new byte[]{0x42}));
    }

    @Test
    void hashHelloWorld() {
        assertEquals(0x75bff66af6ba4d5bL, RapidHash.hash(HELLO));
    }

    @Test
    void hashQuickFox() {
        assertEquals(0x91722dc8d52a3f7bL, RapidHash.hash(QUICK_FOX));
    }

    @Test
    void hashSixteenBytes() {
        byte[] data = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        assertEquals(0xd6bfc1bcf7e9ca19L, RapidHash.hash(data));
    }

    @Test
    void hashSixtyFourBytes() {
        byte[] data = new byte[64];
        for (int i = 0; i < 64; i++) data[i] = (byte) i;
        assertEquals(0xd1a6cc5fe6cf87f4L, RapidHash.hash(data));
    }

    @Test
    void hashTwoFiftySixBytes() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) (i & 0xFF);
        assertEquals(0xa2f383f4b9260b9dL, RapidHash.hash(data));
    }

    @Test
    void hashWithSeed42() {
        assertEquals(0x9293ba21a570895dL, RapidHash.hash(new byte[0], 42L));
        assertEquals(0x115800a3df9828cfL, RapidHash.hash(HELLO, 42L));
        assertEquals(0x49883dccb32018faL, RapidHash.hash(QUICK_FOX, 42L));
    }

    @Test
    void hashWithSeedMaxValue() {
        assertEquals(0x8fa078c381e257b3L, RapidHash.hash(HELLO, Long.MAX_VALUE));
    }

    @Test
    void hashWithSeedNegativeOne() {
        assertEquals(0xaf88dc2472d9e4efL, RapidHash.hash(HELLO, -1L));
    }

    @Test
    void hashSlice() {
        assertEquals(0x340ef9ffdb1ee8c3L, RapidHash.hash(HELLO, 7, 6));
    }

    @Test
    void hashSliceMatchesFullArray() {
        byte[] slice = "World!".getBytes(StandardCharsets.UTF_8);
        assertEquals(RapidHash.hash(slice), RapidHash.hash(HELLO, 7, 6));
    }

    @Test
    void hashDirectByteBuffer() {
        ByteBuffer direct = ByteBuffer.allocateDirect(HELLO.length);
        direct.put(HELLO).flip();
        assertEquals(0x75bff66af6ba4d5bL, RapidHash.hash(direct));
    }

    @Test
    void hashHeapByteBuffer() {
        ByteBuffer heap = ByteBuffer.wrap(HELLO);
        assertEquals(0x75bff66af6ba4d5bL, RapidHash.hash(heap));
    }

    @Test
    void hashReadOnlyByteBuffer() {
        ByteBuffer readOnly = ByteBuffer.wrap(HELLO).asReadOnlyBuffer();
        assertEquals(0x75bff66af6ba4d5bL, RapidHash.hash(readOnly));
    }

    @Test
    void hashByteBufferWithPosition() {
        ByteBuffer buf = ByteBuffer.allocateDirect(HELLO.length);
        buf.put(HELLO).flip();
        buf.position(7);
        assertEquals(0x340ef9ffdb1ee8c3L, RapidHash.hash(buf));
    }

    @Test
    void hashDirectBufferWithSeed() {
        ByteBuffer direct = ByteBuffer.allocateDirect(HELLO.length);
        direct.put(HELLO).flip();
        assertEquals(0x115800a3df9828cfL, RapidHash.hash(direct, 42L));
    }

    @Test
    void byteArrayAndDirectBufferConsistency() {
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).flip();
        assertEquals(RapidHash.hash(data), RapidHash.hash(direct));
    }
}
