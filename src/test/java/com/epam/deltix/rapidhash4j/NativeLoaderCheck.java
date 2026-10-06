package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Tag("integration")
public final class NativeLoaderCheck {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--load")) {
            NativeLoader.load();
            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                long count = files.count();
                long expected = System.getProperty("com.epam.deltix.rapidhash4j.lib.path") == null ? 1 : 0;
                if (count != expected) throw new AssertionError("Unexpected extraction count: " + count);
            }
            return;
        }
        if (args[0].equals("--failure")) {
            try {
                NativeLoader.load();
                throw new AssertionError("Native loading unexpectedly succeeded");
            } catch (UnsatisfiedLinkError error) {
                if (args[1].equals("extraction") && !(error.getCause() instanceof IOException)) {
                    throw new AssertionError("Extraction error lost its cause", error);
                }
                if (args[1].equals("load")) {
                    Path temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
                    if (error.getMessage() == null
                            || !error.getMessage().contains(temp.resolve("rapidhash4j-").toString())
                            || !error.getMessage().contains(System.mapLibraryName("rapidhash4j"))) {
                        throw new AssertionError("Expected failure loading the extracted native binary", error);
                    }
                    try (var files = Files.list(temp)) {
                        if (files.findAny().isPresent()) {
                            throw new AssertionError("Failed native loading left extraction files", error);
                        }
                    }
                }
            }
            return;
        }
        throw new IllegalArgumentException("Unknown loader check: " + args[0]);
    }

    @Test
    void nativeLoaderHandlesExtractionOverridesAndFailures() throws Exception {
        Path artifact = Path.of(System.getProperty("rapidhash4j.test.jar")).toRealPath();
        String resource = System.getProperty("rapidhash4j.test.hostResource");
        Path work = Files.createTempDirectory("rapidhash4j-loader-");
        try {
            Path temp = Files.createDirectory(work.resolve("tmp"));
            Path nativeLibrary = work.resolve(System.mapLibraryName("rapidhash4j"));
            try (JarFile jar = new JarFile(artifact.toFile());
                 var in = jar.getInputStream(jar.getJarEntry(resource))) {
                Files.copy(in, nativeLibrary);
            }
            run(temp, "-Djava.library.path=" + work, "--load");
            // Windows can retain a loaded DLL until process exit, preventing deleteOnExit cleanup.
            if (!System.getProperty("os.name").startsWith("Windows")) {
                try (var files = Files.list(temp)) {
                    if (files.findAny().isPresent()) throw new AssertionError("Extraction files left behind");
                }
            }
            Path overrideTemp = Files.createDirectory(work.resolve("override-tmp"));
            run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + nativeLibrary, "--load");
            run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + work.resolve("missing"), "--failure", "override");
            run(work.resolve("missing-tmp"), "-Djava.library.path=" + work, "--failure", "extraction");
            Path brokenResources = Files.createDirectory(work.resolve("broken-resources"));
            Path brokenLibrary = brokenResources.resolve(resource);
            Files.createDirectories(brokenLibrary.getParent());
            Files.write(brokenLibrary, new byte[256]);
            runWithClasspath(Files.createDirectory(work.resolve("failed-load-tmp")),
                    "-Djava.library.path=" + work,
                    brokenResources + File.pathSeparator + System.getProperty("rapidhash4j.test.classpath"),
                    "--failure", "load");
        } finally {
            try (var files = Files.walk(work)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("Native loader integration checks passed: " + resource);
    }

    private static void run(Path temp, String property, String... args) throws Exception {
        runWithClasspath(temp, property, System.getProperty("rapidhash4j.test.classpath"), args);
    }

    private static void runWithClasspath(Path temp, String property, String classpath, String... args) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(Arrays.asList(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djava.io.tmpdir=" + temp, property, "-cp", classpath,
                NativeLoaderCheck.class.getName()));
        command.addAll(Arrays.asList(args));
        Process child = new ProcessBuilder(command).inheritIO().start();
        try {
            if (!child.waitFor(30, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
                throw new AssertionError("Native loading check timed out after 30 seconds: " + command);
            }
            if (child.exitValue() != 0) throw new AssertionError("Native loading check failed: " + command);
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }
}
