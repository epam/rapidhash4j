package com.epam.deltix.rapidhash4j;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class RapidHashStreamBenchmark {

    @Param({"64", "256", "1024", "4096", "65536", "1048576"})
    int size;

    @State(Scope.Thread)
    public static class ChunkState {
        @Param({"16", "64", "112", "256", "1024", "4096"})
        public int chunkSize;

        @Setup(Level.Trial)
        public void setup() {
            if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
    }

    byte[] data;
    RapidHashStream stream;
    long expectedHash;

    @Setup(Level.Trial)
    public void setup() {
        data = new byte[size];
        new Random(0xDEADBEEF).nextBytes(data);
        stream = new RapidHashStream();
        expectedHash = RapidHash.hash(data);
        long streamHash = stream.update(data).finish();
        if (streamHash != expectedHash) {
            throw new AssertionError(
                    "Stream/bulk mismatch for size=" + size +
                    ": bulk=0x" + Long.toHexString(expectedHash) +
                    " stream=0x" + Long.toHexString(streamHash));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        stream.close();
    }

    @Benchmark
    public long bulkOneShot() {
        return RapidHash.hash(data);
    }

    @Benchmark
    public long streamSingleUpdate() {
        return stream.update(data).finish();
    }

    @Benchmark
    public long streamChunked(ChunkState chunks) {
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(chunks.chunkSize, data.length - offset);
            stream.update(data, offset, len);
            offset += len;
        }
        return stream.finish();
    }
}
