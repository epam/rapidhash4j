package com.epam.deltix.rapidhash4j;

import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class RapidHashBenchmark {

    @Param({"16", "64", "256", "1024", "4096", "65536", "1048576"})
    int size;

    byte[] data;
    ByteBuffer directBuffer;

    @Setup
    public void setup() {
        data = new byte[size];
        new Random(0xDEADBEEF).nextBytes(data);
        directBuffer = ByteBuffer.allocateDirect(size);
        directBuffer.put(data).flip();
    }

    @Benchmark
    public long rapidhashByteArray() {
        return RapidHash.hash(data);
    }

    @Benchmark
    public long rapidhashDirectBuffer() {
        return RapidHash.hash(directBuffer);
    }

    @Benchmark
    public int baselineArraysHashCode() {
        return Arrays.hashCode(data);
    }
}
