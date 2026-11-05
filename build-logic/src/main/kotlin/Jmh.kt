import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class Jmh : JavaExec() {
    @get:Internal
    abstract val results: RegularFileProperty

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        mainClass.set("org.openjdk.jmh.Main")
        doNotTrackState("Benchmarks must always run")
        // Inherited by the forks, unlike the @Fork arguments, -jvmArgsAppend then adds to them instead of replacing them
        jvmArgs("-Xmx2g", "--enable-native-access=ALL-UNNAMED")
        results.convention(project.layout.buildDirectory.file("jmh/$name.json"))
        val results = results
        // Providers, unlike args, are kept when --args replaces the arguments
        argumentProviders.add {
            listOf(
                "-prof",
                "com.xpdustry.nohorny.client.GarbageCollectorSelector",
                "-prof",
                "com.xpdustry.nohorny.client.RetainedHeapProfiler",
                "-rf",
                "json",
                "-rff",
                results.get().asFile.absolutePath,
            )
        }
        doFirst { results.get().asFile.parentFile.mkdirs() }
    }
}
