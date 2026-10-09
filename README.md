# rapidhash4j

Java 11+ bindings for [rapidhash](https://github.com/Nicoshev/rapidhash).

The release JAR includes native libraries for Linux glibc/musl, macOS, and Windows on x86_64 and ARM64.

For local development, run `./gradlew build` with a JDK 17+ and a native C compiler.
A local build without `-PnativeBinariesDir` produces a host-specific JAR.

This project uses the [Apache License 2.0](LICENSE).
The bundled native rapidhash code retains its [MIT license](native/rapidhash/LICENSE).
