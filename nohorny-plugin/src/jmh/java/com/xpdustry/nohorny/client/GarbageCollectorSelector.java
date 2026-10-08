// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.profile.ExternalProfiler;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;

// Starts each fork with the garbage collector named by its "gc" param, such as G1 for -XX:+UseG1GC,
// since params are only applied once the fork is running, too late to pick the collector.
// The jmh task always enables it, the benchmarks call check to fail when it is missing.
public final class GarbageCollectorSelector implements ExternalProfiler {

    private static final String PARAM = "gc";

    public static void check(final String gc) {
        final var option = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                .getVMOption("Use" + gc + "GC");
        if (!Boolean.parseBoolean(option.getValue())) {
            throw new IllegalStateException("The fork does not run the " + gc + " collector, enable "
                    + GarbageCollectorSelector.class.getName() + " with -prof");
        }
    }

    @Override
    public Collection<String> addJVMInvokeOptions(final BenchmarkParams params) {
        return List.of();
    }

    @Override
    public Collection<String> addJVMOptions(final BenchmarkParams params) {
        final var gc = params.getParam(PARAM);
        if (gc == null) {
            return List.of();
        }
        // Serial only compacts away the dead objects every 4th full GC by default, skewing the retained heap
        return gc.equals("Serial")
                ? List.of("-XX:+UseSerialGC", "-XX:MarkSweepAlwaysCompactCount=1")
                : List.of("-XX:+Use" + gc + "GC");
    }

    @Override
    public void beforeTrial(final BenchmarkParams params) {}

    @Override
    @SuppressWarnings("rawtypes")
    public Collection<? extends Result> afterTrial(
            final BenchmarkResult result, final long pid, final File stdOut, final File stdErr) {
        return List.of();
    }

    @Override
    public boolean allowPrintOut() {
        return true;
    }

    @Override
    public boolean allowPrintErr() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Selects the garbage collector of each fork from the gc param";
    }
}
