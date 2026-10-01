// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.natives;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/// Bindings to the nohorny native library, see `nohorny-native/src/nohorny.h`.
/// The library is optional, it is only bundled if CMake was available at build time.
final class NoHornyNative {

    private static final @Nullable Bindings BINDINGS;
    private static final @Nullable Throwable FAILURE;

    static {
        Bindings bindings = null;
        Throwable failure = null;
        try {
            bindings = Bindings.create(load());
        } catch (final IOException | RuntimeException | LinkageError e) {
            failure = e;
        }
        BINDINGS = bindings;
        FAILURE = failure;
    }

    private NoHornyNative() {}

    static Bindings bindings() {
        if (BINDINGS == null) {
            throw new NativeException("The nohorny native library is unavailable for " + platform(), FAILURE);
        }
        return BINDINGS;
    }

    /// Builds an exception from the last error of the calling thread, then clears it.
    static NativeException lastError() {
        final var bindings = bindings();
        try {
            final var error = (MemorySegment) bindings.errorGet().invokeExact();
            final var message = error.equals(MemorySegment.NULL)
                    ? "Unknown native error"
                    : error.reinterpret(Long.MAX_VALUE).getString(0);
            bindings.errorClear().invokeExact();
            return new NativeException(message, null);
        } catch (final Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static SymbolLookup load() throws IOException {
        final var name = System.mapLibraryName("nohorny");
        try (final var stream = NoHornyNative.class.getResourceAsStream("/natives/" + platform() + "/" + name)) {
            if (stream == null) {
                throw new UnsatisfiedLinkError("No native library bundled, was CMake available at build time?");
            }
            final var file = Files.createTempFile("nohorny-", "-" + name);
            file.toFile().deleteOnExit();
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            return SymbolLookup.libraryLookup(file, Arena.global());
        }
    }

    // Must match the platform naming of the cmakeBuild Gradle task
    private static String platform() {
        final var os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final var arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return (os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux")
                + "-"
                + (arch.equals("amd64") || arch.equals("x86_64") ? "x86_64" : arch);
    }

    record Bindings(
            MethodHandle classifierCreate,
            MethodHandle classifierClassify,
            MethodHandle classifierClose,
            MethodHandle errorGet,
            MethodHandle errorClear) {

        static Bindings create(final SymbolLookup lookup) {
            final var linker = Linker.nativeLinker();
            return new Bindings(
                    linker.downcallHandle(
                            lookup.findOrThrow("nh_classifier_create"), FunctionDescriptor.of(ADDRESS, ADDRESS)),
                    linker.downcallHandle(
                            lookup.findOrThrow("nh_classifier_classify"),
                            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT)),
                    linker.downcallHandle(
                            lookup.findOrThrow("nh_classifier_close"), FunctionDescriptor.ofVoid(ADDRESS)),
                    linker.downcallHandle(lookup.findOrThrow("nh_error_get"), FunctionDescriptor.of(ADDRESS)),
                    linker.downcallHandle(lookup.findOrThrow("nh_error_clear"), FunctionDescriptor.ofVoid()));
        }
    }
}
