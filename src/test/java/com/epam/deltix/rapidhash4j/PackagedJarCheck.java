package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Tag("integration")
public final class PackagedJarCheck {
    @Test
    void packagedJarLoadsAndHashes() throws Exception {
        Path artifact = Path.of(System.getProperty("rapidhash4j.test.jar")).toRealPath();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            List<String> binaries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith("com/epam/deltix/rapidhash4j/native/"))
                    .sorted()
                    .collect(Collectors.toList());
            List<String> expected = Arrays.stream(System.getProperty("rapidhash4j.test.nativeResources").split("\n"))
                    .sorted().collect(Collectors.toList());
            if (!binaries.equals(expected)) {
                throw new AssertionError("Unexpected packaged native files: " + binaries);
            }
        }
        Path loadedFrom = Path.of(RapidHash.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toRealPath();
        if (!artifact.equals(loadedFrom)) {
            throw new AssertionError("Hash classes loaded from " + loadedFrom + " instead of " + artifact);
        }
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        if (RapidHash.hash(data) != 0xa2f383f4b9260b9dL) {
            throw new AssertionError("Packaged hash mismatch");
        }
        System.out.println("Packaged native loading verified: " + System.getProperty("rapidhash4j.test.hostResource"));
    }
}
