package com.epam.deltix.rapidhash4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeLoaderTest {
    @Test
    void detectsMuslWithCompatibilityLibrary() {
        assertEquals("musl", NativeLoader.detectLinuxLibc(
                "1000-2000 r-xp 00000000 00:01 1 /lib/libgcompat.so.0\n"
                        + "2000-3000 r-xp 00000000 00:01 2 /lib/ld-musl-x86_64.so.1\n"));
        assertEquals("musl", NativeLoader.detectLinuxLibc(
                "1000-2000 r-xp 00000000 00:01 1 /lib/libc.musl-aarch64.so.1 (deleted)\n"));
    }

    @Test
    void detectsGlibcWithoutMatchingDirectoryNames() {
        assertEquals("glibc", NativeLoader.detectLinuxLibc(
                "1000-2000 r-xp 00000000 00:01 1 /opt/musl/lib/libc.so.6\n"));
        assertEquals("glibc", NativeLoader.detectLinuxLibc(
                "1000-2000 r-xp 00000000 00:01 1 /lib64/libc-2.17.so\n"));
    }

    @Test
    void leavesUnknownOrMixedLibcsUndetermined() {
        assertNull(NativeLoader.detectLinuxLibc(""));
        assertNull(NativeLoader.detectLinuxLibc(
                "1000-2000 rw-p 00000000 00:00 0 [heap]\n"
                        + "2000-3000 r-xp 00000000 00:01 1 /opt/musl/lib/libjvm.so\n"));
        assertNull(NativeLoader.detectLinuxLibc(
                "1000-2000 r-xp 00000000 00:01 1 /lib/libc.so.6\n"
                        + "2000-3000 r-xp 00000000 00:01 2 /lib/ld-musl-x86_64.so.1\n"));
    }
}
