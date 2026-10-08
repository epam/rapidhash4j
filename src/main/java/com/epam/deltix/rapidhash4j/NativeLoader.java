package com.epam.deltix.rapidhash4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class NativeLoader {

    private static boolean loaded;

    private NativeLoader() {}

    static synchronized void load() {
        if (loaded) return;
        doLoad();
        loaded = true;
    }

    private static void doLoad() {
        String override = System.getProperty("com.epam.deltix.rapidhash4j.lib.path");
        if (override != null) {
            System.load(override);
            return;
        }

        String os = detectOs();
        String arch = detectArch();
        String libc = "linux".equals(os) ? detectLinuxLibc() : null;
        String libName = System.mapLibraryName("rapidhash4j");
        String resourceRoot = "/com/epam/deltix/rapidhash4j/native/" + os + "-" + arch;
        String resourcePath = resourceRoot + ("musl".equals(libc) ? "-musl" : "") + "/" + libName;

        try {
            try {
                loadResource(resourcePath, libName);
            } catch (UnsatisfiedLinkError first) {
                if (!"linux".equals(os) || libc != null) throw first;
                resourcePath = resourceRoot + "-musl/" + libName;
                try {
                    loadResource(resourcePath, libName);
                } catch (UnsatisfiedLinkError | IOException second) {
                    first.addSuppressed(second);
                    throw first;
                }
            }
        } catch (IOException e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                    "Failed to extract native library " + resourcePath + ": " + e.getMessage());
            error.initCause(e);
            throw error;
        }
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

    private static String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "linux";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("win")) return "windows";
        throw new UnsatisfiedLinkError("Unsupported OS: " + os);
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch", "");
        if ("amd64".equals(arch) || "x86_64".equals(arch)) return "x86_64";
        if ("aarch64".equals(arch) || "arm64".equals(arch)) return "aarch64";
        throw new UnsatisfiedLinkError("Unsupported architecture: " + arch);
    }
}
