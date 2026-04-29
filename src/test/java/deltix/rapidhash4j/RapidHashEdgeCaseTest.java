package deltix.rapidhash4j;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RapidHashEdgeCaseTest {

    @Test
    void nullByteArrayThrowsNPE() {
        assertThrows(NullPointerException.class, () -> RapidHash.hash((byte[]) null));
    }

    @Test
    void nullByteBufferThrowsNPE() {
        assertThrows(NullPointerException.class, () -> RapidHash.hash((ByteBuffer) null));
    }

    @Test
    void negativeOffsetThrows() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> RapidHash.hash(new byte[10], -1, 5));
    }

    @Test
    void negativeLengthThrows() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> RapidHash.hash(new byte[10], 0, -1));
    }

    @Test
    void lengthExceedingArrayThrows() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> RapidHash.hash(new byte[10], 5, 6));
    }

    @Test
    void offsetAtEndThrows() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> RapidHash.hash(new byte[10], 10, 1));
    }

    @Test
    void zeroLengthSliceIsValid() {
        assertDoesNotThrow(() -> RapidHash.hash(new byte[10], 5, 0));
    }

    @Test
    void emptyDirectBuffer() {
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        assertDoesNotThrow(() -> RapidHash.hash(empty));
    }

    @Test
    void largeInput() {
        byte[] large = new byte[1024 * 1024];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i & 0xFF);
        assertDoesNotThrow(() -> RapidHash.hash(large));
    }
}
