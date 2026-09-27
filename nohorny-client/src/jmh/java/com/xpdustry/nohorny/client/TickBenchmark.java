// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import java.util.concurrent.TimeUnit;
import mindustry.Vars;
import mindustry.core.GameState;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

// Measures the art changes and the tracker ticks, nohorny=false measures the changes alone
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(
        value = 1,
        jvmArgsAppend = {"-Xmx2g", "--enable-native-access=ALL-UNNAMED"})
public class TickBenchmark {

    @Param({"100", "250", "500"})
    public int size;

    @Param({"0", "10", "100"})
    public int changesPerTick;

    @Param({"false", "true"})
    public boolean nohorny;

    private @Nullable ArtWorld world;
    private @Nullable NoHornyHarness harness;
    private long groups;
    private long grouped;

    @Setup(Level.Trial)
    public void setup() {
        HeadlessMindustry.init();
        this.world = ArtWorld.generate(this.size, 42L);
        this.harness = this.nohorny ? new NoHornyHarness() : null;
        Vars.state.set(GameState.State.playing);
    }

    @TearDown(Level.Iteration)
    public void report() {
        if (this.harness != null) {
            System.out.printf(
                    "%n%d groups (%d buildings) sent to classification during this iteration%n",
                    this.harness.groups - this.groups, this.harness.grouped - this.grouped);
            this.groups = this.harness.groups;
            this.grouped = this.harness.grouped;
        }
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (this.harness != null) {
            this.harness.close();
        }
        Vars.state.set(GameState.State.menu);
    }

    @Benchmark
    @SuppressWarnings("NullAway")
    public void tick() {
        for (int i = 0; i < this.changesPerTick; i++) {
            this.world.change();
        }
        if (this.harness != null) {
            this.harness.tick();
        }
    }
}
