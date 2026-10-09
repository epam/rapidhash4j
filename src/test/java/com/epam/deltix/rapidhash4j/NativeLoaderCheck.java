package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
public final class NativeLoaderCheck {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--load-linux") || args[0].equals("--failure-linux")) {
            System.setProperty("os.name", "Linux");
        }

        if (args[0].equals("--concurrent")) {
            checkConcurrentLoading(args.length > 1);
            return;
        }

        if (args[0].equals("--restricted-override")) {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission permission) {}

                @Override
                public void checkPropertyAccess(String key) {
                    if (key.equals(args[1])) throw new SecurityException("Denied " + key);
                }
            });

            Throwable error = RapidHash.loadError();
            if (args.length == 2) {
                assertNull(error);
                assertTrue(RapidHash.isAvailable());
                assertEquals(0x0338dc4be2cecdaeL, RapidHash.hash(new byte[0]));
                try (RapidHashStream stream = new RapidHashStream()) {
                    assertEquals(0x0338dc4be2cecdaeL, stream.finish());
                }
            } else {
                assertInstanceOf(UnsatisfiedLinkError.class, error);
                assertFalse(RapidHash.isAvailable());
                assertInstanceOf(UnsatisfiedLinkError.class, error.getCause());
                assertTrue(error.getMessage().contains("override "
                        + System.getProperty("com.epam.deltix.rapidhash4j.lib.path")));
                assertTrue(error.getMessage().contains(
                        args[1].equals("os.name") ? "os=unknown" : "arch=unknown"));

                for (Runnable use : nativeUses()) {
                    UnsatisfiedLinkError thrown = assertThrows(UnsatisfiedLinkError.class, use::run);
                    assertSame(error, thrown.getCause());
                    assertEquals(error.getMessage(), thrown.getMessage());
                }
            }

            System.clearProperty("com.epam.deltix.rapidhash4j.lib.path");
            assertSame(error, RapidHash.loadError());
            assertEquals(1, NativeLoader.loadAttempts());
            return;
        }

        if (args[0].equals("--load") || args[0].equals("--load-linux")) {
            boolean overridden = System.getProperty("com.epam.deltix.rapidhash4j.lib.path") != null;
            assertNull(RapidHash.loadError());
            assertTrue(RapidHash.isAvailable());
            assertEquals(1, NativeLoader.loadAttempts());

            System.setProperty("com.epam.deltix.rapidhash4j.lib.path", "/nonexistent-after-success");
            assertNull(RapidHash.loadError());
            assertEquals(0x0338dc4be2cecdaeL, RapidHash.hash(new byte[0]));
            try (RapidHashStream stream = new RapidHashStream()) {
                assertEquals(0x0338dc4be2cecdaeL, stream.finish());
            }

            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                long count = files.count();
                long expected = overridden ? 0 : 1;
                if (count != expected) throw new AssertionError("Unexpected extraction count: " + count);
            }

            if (args.length > 1) {
                try (var files = Files.walk(Path.of(System.getProperty("java.io.tmpdir")))) {
                    Files.copy(files.filter(Files::isRegularFile).findFirst().orElseThrow(), Path.of(args[1]));
                }
            }

            return;
        }

        if (args[0].equals("--failure") || args[0].equals("--failure-linux")) {
            if (args[1].equals("os")) System.setProperty("os.name", "unsupported-test-os");
            if (args[1].equals("arch")) System.setProperty("os.arch", "unsupported-test-arch");

            UnsatisfiedLinkError firstUse = null;
            if (args.length > 2 && args[2].equals("hash")) {
                firstUse = assertThrows(UnsatisfiedLinkError.class, () -> RapidHash.hash(new byte[0]));
            } else if (args.length > 2 && args[2].equals("stream")) {
                firstUse = assertThrows(UnsatisfiedLinkError.class, () -> new RapidHashStream().close());
            }

            Throwable error = RapidHash.loadError();
            assertInstanceOf(UnsatisfiedLinkError.class, error);

            if (firstUse != null)
                assertSame(error, firstUse.getCause());

            String message = error.getMessage();
            Throwable cause = error.getCause();
            String override = System.getProperty("com.epam.deltix.rapidhash4j.lib.path");
            boolean unsupported = args[1].equals("os") || args[1].equals("arch");
            String expectedSource = override != null ? "override " + override : unsupported ? "not selected" : args[2];
            String expectedLibc = override != null || unsupported ? "null" : args[3];
            String expectedPrefix = "Failed to load native library " + expectedSource
                    + " (os=" + System.getProperty("os.name") + ", arch=" + System.getProperty("os.arch")
                    + ", detected libc=" + expectedLibc + "): ";
            assertTrue(message.startsWith(expectedPrefix), message);
            assertInstanceOf(UnsatisfiedLinkError.class, cause);
            assertEquals(expectedPrefix + cause.getMessage(), message);

            Throwable[] suppressed = cause.getSuppressed();
            System.clearProperty("com.epam.deltix.rapidhash4j.lib.path");
            for (int repeat = 0; repeat < 2; repeat++) {
                assertSame(error, RapidHash.loadError());
                assertFalse(RapidHash.isAvailable());

                for (Runnable use : nativeUses()) {
                    UnsatisfiedLinkError thrown = assertThrows(UnsatisfiedLinkError.class, use::run);
                    assertSame(error, thrown.getCause());
                    assertEquals(message, thrown.getMessage());
                }
            }

            assertEquals(1, NativeLoader.loadAttempts());
            assertEquals(message, error.getMessage());
            assertSame(cause, error.getCause());
            assertArrayEquals(suppressed, cause.getSuppressed());

            if (args[1].equals("extraction"))
                assertInstanceOf(IOException.class, cause.getCause());

            if (args[1].equals("both"))
                assertEquals(1, suppressed.length);

            if (args[1].equals("load") || args[1].equals("both")) {
                Path temp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
                assertTrue(message.contains(temp.resolve("rapidhash4j-").toString()));
                assertTrue(message.contains(System.mapLibraryName("rapidhash4j")));

                try (var files = Files.list(temp)) {
                    assertFalse(files.findAny().isPresent(), "Failed native loading left extraction files");
                }
            }

            return;
        }

        throw new IllegalArgumentException("Unknown loader check: " + args[0]);
    }

    private static Runnable[] nativeUses() {
        byte[] data = {0x42};
        ByteBuffer heap = ByteBuffer.wrap(data);
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        direct.put(data).flip();

        ByteBuffer readOnly = heap.asReadOnlyBuffer();
        return new Runnable[] {
                () -> RapidHash.hash(data), () -> RapidHash.hash(data, 42L),
                () -> RapidHash.hash(data, 0, 1), () -> RapidHash.hash(data, 0, 1, 42L),
                () -> RapidHash.hash(heap), () -> RapidHash.hash(heap, 42L),
                () -> RapidHash.hash(direct), () -> RapidHash.hash(direct, 42L),
                () -> RapidHash.hash(readOnly), () -> RapidHash.hash(readOnly, 42L),
                () -> new RapidHashStream().close(), () -> new RapidHashStream(42L).close()
        };
    }

    private static void checkConcurrentLoading(boolean failure) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 8; i++) {
                int operation = i % 4;

                results.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));

                    if (operation == 0) {
                        if (failure)
                            assertInstanceOf(UnsatisfiedLinkError.class, RapidHash.loadError());
                        else
                            assertNull(RapidHash.loadError());
                    } else if (operation == 1) {
                        assertEquals(!failure, RapidHash.isAvailable());
                    } else if (failure) {
                        UnsatisfiedLinkError error = assertThrows(UnsatisfiedLinkError.class,
                                () -> {
                                    if (operation == 2)
                                        RapidHash.hash(new byte[0]);
                                    else
                                        new RapidHashStream().close();
                                });
                        assertSame(RapidHash.loadError(), error.getCause());
                    } else if (operation == 2) {
                        assertEquals(0x0338dc4be2cecdaeL, RapidHash.hash(new byte[0]));
                    } else {
                        try (RapidHashStream stream = new RapidHashStream()) {
                            assertEquals(0x0338dc4be2cecdaeL, stream.finish());
                        }
                    }

                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> result : results)
                result.get(10, TimeUnit.SECONDS);

            assertEquals(1, NativeLoader.loadAttempts());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void preservesSecurityFailureCauseAndSuppressedExceptions() throws Exception {
        SecurityException failure = new SecurityException("denied", new IOException("original cause"));
        IOException suppressed = new IOException("original suppressed");
        failure.addSuppressed(suppressed);

        URL jar = Path.of(System.getProperty("rapidhash4j.test.jar")).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar}, null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                throw failure;
            }
        }) {
            Class<?> hash = Class.forName(RapidHash.class.getName(), true, loader);
            var probe = hash.getMethod("loadError");
            Throwable diagnostic = assertInstanceOf(UnsatisfiedLinkError.class, probe.invoke(null));
            assertSame(failure, diagnostic.getCause());
            assertSame(diagnostic, probe.invoke(null));

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> hash.getMethod("hash", byte[].class).invoke(null, new byte[0]));
            assertInstanceOf(UnsatisfiedLinkError.class, thrown.getCause());
            assertSame(diagnostic, thrown.getCause().getCause());
            assertEquals("denied", failure.getMessage());
            assertEquals("original cause", failure.getCause().getMessage());
            assertArrayEquals(new Throwable[] {suppressed}, failure.getSuppressed());
        }
    }

    @Test
    void propagatesJvmErrors() throws Exception {
        OutOfMemoryError failure = new OutOfMemoryError("synthetic JVM failure");
        URL jar = Path.of(System.getProperty("rapidhash4j.test.jar")).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar}, null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                throw failure;
            }
        }) {
            assertSame(failure, assertThrows(OutOfMemoryError.class,
                    () -> Class.forName(RapidHash.class.getName(), true, loader)));
            assertThrows(ExceptionInInitializerError.class,
                    () -> Class.forName(RapidHashStream.class.getName(), true, loader));
        }
    }

    @Test
    void nativeLoaderHandlesExtractionOverridesAndFailures() throws Exception {
        String resource = System.getProperty("rapidhash4j.test.hostResource");
        boolean linux = System.getProperty("os.name").startsWith("Linux");
        String libc = linux ? NativeLoader.detectLinuxLibc() : null;
        Path work = Files.createTempDirectory("rapidhash4j-loader-");

        try {
            Path temp = Files.createDirectory(work.resolve("tmp"));
            Path nativeLibrary = work.resolve(System.mapLibraryName("rapidhash4j"));
            run(temp, "-Djava.library.path=" + work, "--load", nativeLibrary.toString());
            // Windows can retain a loaded DLL until process exit, preventing deleteOnExit cleanup.
            if (!System.getProperty("os.name").startsWith("Windows")) {
                try (var files = Files.list(temp)) {
                    if (files.findAny().isPresent()) throw new AssertionError("Extraction files left behind");
                }
            }

            Path overrideTemp = Files.createDirectory(work.resolve("override-tmp"));
            run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + nativeLibrary, "--load");
            run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + work.resolve("missing"), "--failure", "override");

            if (Runtime.version().feature() < 24) {
                for (String property : new String[] {"os.name", "os.arch"}) {
                    run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + nativeLibrary,
                            "--restricted-override", property);
                    run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + work.resolve("missing"),
                            "--restricted-override", property, "failure");
                }
            }

            for (String first : new String[] {"hash", "stream"}) {
                run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + work.resolve("missing"),
                        "--failure", "override", first);
            }

            run(Files.createDirectory(work.resolve("concurrent-tmp")), "-Djava.library.path=" + work, "--concurrent");
            run(overrideTemp, "-Dcom.epam.deltix.rapidhash4j.lib.path=" + work.resolve("missing"),
                    "--concurrent", "failure");
            run(overrideTemp, "-Djava.library.path=" + work, "--failure", "os");
            run(overrideTemp, "-Djava.library.path=" + work, "--failure", "arch");
            run(work.resolve("missing-tmp"), "-Djava.library.path=" + work,
                    "--failure", "extraction", "/" + resource, String.valueOf(libc));

            Path brokenResources = Files.createDirectory(work.resolve("broken-resources"));
            Path brokenLibrary = brokenResources.resolve(resource.replace("-musl/", "/"));
            Files.createDirectories(brokenLibrary.getParent());
            Files.write(brokenLibrary, new byte[256]);
            String failedSource = "/" + resource;

            if (linux) {
                Path brokenMusl = brokenLibrary.getParent()
                        .resolveSibling(brokenLibrary.getParent().getFileName() + "-musl")
                        .resolve(brokenLibrary.getFileName());
                Files.createDirectories(brokenMusl.getParent());
                Files.write(brokenMusl, new byte[256]);

                if ("glibc".equals(libc)) {
                    Files.copy(nativeLibrary, brokenMusl, StandardCopyOption.REPLACE_EXISTING);
                } else if ("musl".equals(libc)) {
                    Files.copy(nativeLibrary, brokenLibrary, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    failedSource = "/" + brokenResources.relativize(brokenLibrary).toString().replace(File.separatorChar, '/')
                            + " then /" + brokenResources.relativize(brokenMusl).toString().replace(File.separatorChar, '/');
                }
            }

            runWithClasspath(Files.createDirectory(work.resolve("failed-load-tmp")),
                    "-Djava.library.path=" + work,
                    brokenResources + File.pathSeparator + System.getProperty("rapidhash4j.test.classpath"),
                    "--failure", "load", failedSource, String.valueOf(libc));

            // Non-Linux hosts have no process maps, exercising unknown-libc fallback.
            if (!linux) {
                Path linuxResources = Files.createDirectory(work.resolve("linux-resources"));
                Path glibcLibrary = linuxResources.resolve(resource
                        .replace("/darwin-", "/linux-").replace("/windows-", "/linux-"));
                Path muslLibrary = glibcLibrary.getParent()
                        .resolveSibling(glibcLibrary.getParent().getFileName() + "-musl")
                        .resolve(glibcLibrary.getFileName());

                Files.createDirectories(glibcLibrary.getParent());
                Files.createDirectories(muslLibrary.getParent());
                Files.write(glibcLibrary, new byte[256]);
                Files.copy(nativeLibrary, muslLibrary);

                String linuxClasspath = linuxResources + File.pathSeparator
                        + System.getProperty("rapidhash4j.test.classpath");
                runWithClasspath(Files.createDirectory(work.resolve("musl-tmp")),
                        "-Djava.library.path=" + work, linuxClasspath, "--load-linux");

                Files.write(muslLibrary, new byte[256]);
                runWithClasspath(Files.createDirectory(work.resolve("both-failed-tmp")),
                        "-Djava.library.path=" + work, linuxClasspath, "--failure-linux", "both",
                        "/" + linuxResources.relativize(glibcLibrary).toString().replace(File.separatorChar, '/')
                                + " then /" + linuxResources.relativize(muslLibrary).toString().replace(File.separatorChar, '/'),
                        "null");
            }
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

        if (args[0].equals("--restricted-override") && Runtime.version().feature() >= 18) {
            command.add(1, "-Djava.security.manager=allow");
        }

        command.addAll(Arrays.asList(args));

        Process child = new ProcessBuilder(command).inheritIO().start();
        try {
            if (!child.waitFor(30, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
                throw new AssertionError("Native loading check timed out after 30 seconds: " + command);
            }

            if (child.exitValue() != 0)
                throw new AssertionError("Native loading check failed: " + command);
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }
}
