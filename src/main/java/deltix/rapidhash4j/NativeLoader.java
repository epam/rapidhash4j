package deltix.rapidhash4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NativeLoader {

    private static volatile boolean loaded = false;

    private NativeLoader() {}

    static void load() {
        if (loaded) return;
        synchronized (NativeLoader.class) {
            if (loaded) return;
            doLoad();
            loaded = true;
        }
    }

    private static void doLoad() {
        String override = System.getProperty("deltix.rapidhash4j.lib.path");
        if (override != null) {
            System.load(override);
            return;
        }

        try {
            System.loadLibrary("rapidhash4j");
            return;
        } catch (UnsatisfiedLinkError ignored) {
        }

        String os = detectOs();
        String arch = detectArch();
        String libName = libName(os);
        String resourcePath = "/deltix/rapidhash4j/native/" + os + "-" + arch + "/" + libName;

        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(
                        "Native library not found on classpath: " + resourcePath);
            }

            Path tempDir = Files.createTempDirectory("rapidhash4j-");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();

            System.load(tempLib.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }

    private static String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
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

    private static String libName(String os) {
        if ("windows".equals(os)) return "rapidhash4j.dll";
        if ("darwin".equals(os)) return "librapidhash4j.dylib";
        return "librapidhash4j.so";
    }
}
