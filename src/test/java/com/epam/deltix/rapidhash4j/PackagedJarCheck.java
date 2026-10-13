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
        PackagedJarSmokeTest.main(new String[] {artifact.toString()});
        System.out.println("Packaged native loading verified: " + System.getProperty("rapidhash4j.test.hostResource"));
    }
}
