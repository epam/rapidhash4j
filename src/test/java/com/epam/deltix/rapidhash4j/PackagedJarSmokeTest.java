package com.epam.deltix.rapidhash4j;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class PackagedJarSmokeTest {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toRealPath();
        Path loadedFrom = Path.of(RapidHash.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
        if (!jar.equals(loadedFrom)) {
            throw new AssertionError("Hash classes loaded from " + loadedFrom + " instead of " + jar);
        }
        if (!RapidHash.isAvailable()) {
            throw new AssertionError("Packaged native library failed to load", RapidHash.loadError());
        }

        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        long expected = 0xa2f383f4b9260b9dL;
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).flip();
        if (RapidHash.hash(data) != expected || RapidHash.hash(direct) != expected) {
            throw new AssertionError("Packaged array or direct-buffer hash mismatch");
        }
        try (RapidHashStream stream = new RapidHashStream()) {
            stream.update(data, 0, 100).update(data, 100, data.length - 100);
            if (stream.finish() != expected) {
                throw new AssertionError("Packaged streaming array hash mismatch");
            }
            stream.update(direct);
            if (stream.finish() != expected) {
                throw new AssertionError("Packaged streaming direct-buffer hash mismatch");
            }
        }
        System.out.println("Native loading and hashing verified from " + jar
                + " on " + System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
    }
}
