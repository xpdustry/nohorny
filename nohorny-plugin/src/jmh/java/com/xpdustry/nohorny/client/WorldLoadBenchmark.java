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

// Measures indexing an existing world when the game enters the playing state
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
// The forks inherit their JVM arguments from the jmh task
@Fork(3)
public class WorldLoadBenchmark {

    @Param({"100", "250", "500"})
    public int size;

    // Applied by GarbageCollectorSelector when starting the fork
    @Param({"G1", "Serial"})
    @SuppressWarnings("NullAway.Init")
    public String gc;

    private @Nullable NoHornyHarness harness;

    @Setup(Level.Trial)
    public void setup() {
        GarbageCollectorSelector.check(this.gc);
        HeadlessMindustry.init();
        final var world = ArtWorld.generate(this.size, 42L);
        System.out.printf("%n%d buildings in a %dx%d world%n", world.buildings(), this.size, this.size);
        this.harness = new NoHornyHarness();
    }

    @TearDown(Level.Trial)
    @SuppressWarnings("NullAway")
    public void teardown() {
        this.harness.close();
    }

    @TearDown(Level.Invocation)
    public void unload() {
        Vars.state.set(GameState.State.menu);
    }

    @Benchmark
    public void load() {
        Vars.state.set(GameState.State.playing);
    }
}
