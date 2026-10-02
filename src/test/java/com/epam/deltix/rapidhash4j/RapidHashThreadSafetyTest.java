package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RapidHashThreadSafetyTest {

    @Test
    void concurrentByteArrayHashing() throws InterruptedException {
        byte[] data = "concurrent-test-data".getBytes();
        long expected = RapidHash.hash(data);
        int threads = 8;
        int iterations = 100_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (RapidHash.hash(data) != expected) {
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

        assertFalse(failed.get(), "Hash mismatch detected under concurrent access");
    }

    @Test
    void concurrentDirectBufferHashing() throws InterruptedException {
        byte[] data = "concurrent-direct-buffer-test".getBytes();
        long expected = RapidHash.hash(data);
        int threads = 8;
        int iterations = 100_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    ByteBuffer buf = ByteBuffer.allocateDirect(data.length);
                    buf.put(data).flip();
                    for (int i = 0; i < iterations; i++) {
                        buf.clear();
                        if (RapidHash.hash(buf) != expected) {
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

        assertFalse(failed.get(), "Hash mismatch detected under concurrent DirectByteBuffer access");
    }
}
