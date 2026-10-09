package com.epam.deltix.rapidhash4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

final class NativeLoader {

    private static int loadAttempts;
    private static boolean completed;
    private static UnsatisfiedLinkError failure;

    private NativeLoader() {}

    static synchronized Throwable loadError() {
        if (loadAttempts == 0) {
            loadAttempts++;
            failure = doLoad();
            completed = true;
        }
        if (!completed) throw new IllegalStateException("Native loading did not complete");
        return failure;
    }

    static synchronized int loadAttempts() {
        return loadAttempts;
    }

    static UnsatisfiedLinkError unavailable() {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(failure.getMessage());
        error.initCause(failure);
        return error;
    }

    private static UnsatisfiedLinkError doLoad() {
        String osName = "unknown";
        String arch = "unknown";
        String libc = null;
        String source = "not selected";
        try {
            String override = System.getProperty("com.epam.deltix.rapidhash4j.lib.path");
            if (override != null) source = "override " + override;
            try {
                osName = System.getProperty("os.name", "");
                arch = System.getProperty("os.arch", "");
            } catch (SecurityException denied) {
                if (override == null) throw denied;
            }
            if (override != null) {
                System.load(override);
                return null;
            }

            String os = detectOs(osName);
            String resourceRoot = "/com/epam/deltix/rapidhash4j/native/" + os + "-" + detectArch(arch);
            libc = "linux".equals(os) ? detectLinuxLibc() : null;
            String libName = System.mapLibraryName("rapidhash4j");
            String resourcePath = selectResource(resourceRoot, libc, libName,
                    path -> NativeLoader.class.getResource(path) != null);
            source = resourcePath;
            if ("musl".equals(libc) && !resourcePath.contains("-musl/")) source += " (glibc fallback)";

            try {
                loadResource(resourcePath, libName);
            } catch (UnsatisfiedLinkError first) {
                if (!"linux".equals(os) || libc != null) throw first;
                resourcePath = resourceRoot + "-musl/" + libName;
                source += " then " + resourcePath;
                try {
                    loadResource(resourcePath, libName);
                } catch (UnsatisfiedLinkError | IOException second) {
                    first.addSuppressed(second);
                    throw first;
                }
            }
            return null;
        } catch (IOException | UnsatisfiedLinkError | SecurityException error) {
            Throwable original = error;
            if (error instanceof IOException) {
                UnsatisfiedLinkError extractionError = new UnsatisfiedLinkError(
                        "Failed to extract native library " + source + ": " + error.getMessage());
                extractionError.initCause(error);
                original = extractionError;
            }
            UnsatisfiedLinkError diagnostic = new UnsatisfiedLinkError(
                    "Failed to load native library " + source + " (os=" + osName
                            + ", arch=" + arch + ", detected libc=" + libc + "): " + original.getMessage());
            diagnostic.initCause(original);
            return diagnostic;
        }
    }

    static String selectResource(String resourceRoot, String libc, String libName, Predicate<String> exists) {
        String musl = resourceRoot + "-musl/" + libName;
        return "musl".equals(libc) && exists.test(musl) ? musl : resourceRoot + "/" + libName;
    }

    static String detectLinuxLibc() {
        try {
            return detectLinuxLibc(Files.readString(Path.of("/proc/self/maps")));
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    static String detectLinuxLibc(String maps) {
        boolean musl = false;
        boolean glibc = false;
        var lines = maps.lines().iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            int slash = line.lastIndexOf('/');
            if (slash < 0) continue;
            String name = line.substring(slash + 1);
            if (name.endsWith(" (deleted)")) {
                name = name.substring(0, name.length() - " (deleted)".length());
            }
            musl |= (name.startsWith("ld-musl-") || name.startsWith("libc.musl-"))
                    && (name.endsWith(".so") || name.endsWith(".so.1"));
            glibc |= name.equals("libc.so.6")
                    || (name.startsWith("libc-") && name.endsWith(".so"));
        }
        return musl == glibc ? null : musl ? "musl" : "glibc";
    }

    private static void loadResource(String resourcePath, String libName) throws IOException {
        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(
                        "Native library not found on classpath: " + resourcePath);
            }

            Path tempDir = Files.createTempDirectory("rapidhash4j-");
            Path tempLib = tempDir.resolve(libName);
            try {
                tempDir.toFile().deleteOnExit();
                tempLib.toFile().deleteOnExit();
                Files.copy(in, tempLib);
                System.load(tempLib.toAbsolutePath().toString());
            } catch (IOException | RuntimeException | LinkageError error) {
                deleteAfterFailure(tempLib, error);
                deleteAfterFailure(tempDir, error);
                throw error;
            }
        }
    }

    private static void deleteAfterFailure(Path path, Throwable error) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException cleanupError) {
            error.addSuppressed(cleanupError);
        }
    }

    private static String detectOs(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("win")) return "windows";
        throw new UnsatisfiedLinkError("Unsupported OS: " + os);
    }

    private static String detectArch(String arch) {
        if ("amd64".equals(arch) || "x86_64".equals(arch)) return "x86_64";
        if ("aarch64".equals(arch) || "arm64".equals(arch)) return "aarch64";
        throw new UnsatisfiedLinkError("Unsupported architecture: " + arch);
    }
}
