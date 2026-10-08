// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

// Measures the heap still reachable between the iterations, outside the timed region,
// always enabled by the jmh task.
// JMH runs the trial setup within the first iteration and the trial teardown within the last one,
// so the first iteration is measured after it runs and the others before they run.
// A trial of a single iteration reports nothing, as both happen within it.
public final class RetainedHeapProfiler implements InternalProfiler {

    private static final double BYTES_PER_MIB = 1024D * 1024D;

    private boolean first = true;
    private long retained;

    @Override
    public String getDescription() {
        return "Retained heap after a full garbage collection between the iterations";
    }

    @Override
    public void beforeIteration(final BenchmarkParams benchmarkParams, final IterationParams iterationParams) {
        if (!this.first) {
            this.retained = measure();
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Collection<? extends Result> afterIteration(
            final BenchmarkParams benchmarkParams,
            final IterationParams iterationParams,
            final IterationResult result) {
        if (this.first) {
            this.first = false;
            if (benchmarkParams.getWarmup().getCount()
                            + benchmarkParams.getMeasurement().getCount()
                    == 1) {
                return List.of();
            }
            this.retained = measure();
        }
        return List.of(new ScalarResult("heap.retained", this.retained / BYTES_PER_MIB, "MiB", AggregationPolicy.AVG));
    }

    private static long measure() {
        // System.gc() blocks until a full collection completes on G1, Parallel, Serial and ZGC
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
