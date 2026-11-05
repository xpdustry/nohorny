import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec

plugins {
    id("nohorny.publishing")
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
}

val metadata =
    ModMetadata(
        name = "nohorny",
        displayName = "NoHorny",
        description = description!!,
        author = "Xpdustry",
        version = version.toString(),
        mainClass = "com.xpdustry.nohorny.client.NoHornyPlugin",
        repository = "xpdustry/nohorny",
        java = true,
        hidden = true,
        minGameVersion = "159",
        dependencies = mutableListOf(ModDependency("slf4md", soft = true)),
    )

toxopid {
    platforms = setOf(ModPlatform.SERVER)
    compileVersion = "v${metadata.minGameVersion}"
}

val jmh = sourceSets.create("jmh") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations.named(jmh.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    api(project(":nohorny-common"))
    compileOnly(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    testImplementation(toxopid.dependencies.mindustryHeadless)
    testImplementation(libs.jazzer.junit)

    "jmhImplementation"(libs.jmh.core)
    "jmhAnnotationProcessor"(libs.jmh.generator)
    "jmhCompileOnly"(toxopid.dependencies.mindustryCore)
    "jmhCompileOnly"(toxopid.dependencies.arcCore)
    // The real server jar, which also bundles the assets required by the logic processors
    "jmhRuntimeOnly"(files(tasks.named("downloadMindustryServer")))
}

configurations.runtimeClasspath {
    exclude(group = "org.slf4j")
    exclude(group = "com.google.errorprone")
}

val generateMetadataFile = tasks.register("generateMetadataFile") {
    description = "Generate the plugin.json file."
    // A local copy, the action must not capture the script
    val metadata = metadata
    inputs.property("metadata", metadata)
    val output = temporaryDir.resolve("plugin.json")
    outputs.file(output)
    doLast { output.writeText(ModMetadata.toJson(metadata)) }
}

tasks.shadowJar {
    archiveFileName = "${project.name}.jar"
    archiveClassifier = "plugin"
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    mergeServiceFiles()
    from(generateMetadataFile)
    minimize()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val downloadSlf4md = tasks.register<GithubAssetDownload>("downloadSlf4md") {
    description = "Download sl4md.jar from GitHub."
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md.jar"
    version = "v1.2.0"
}

tasks.named<MindustryExec>(MindustryExec.SERVER_EXEC_TASK_NAME) {
    mods.from(downloadSlf4md)
}

tasks.withType<MindustryExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args("host Shattered sandbox,rules add planet sun")
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<Test>("fuzz") {
    description = "Run the fuzz tests in fuzzing mode."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*FuzzTest")
    environment("JAZZER_FUZZ", "1")
    outputs.upToDateWhen { false }
}

tasks.withType<Jmh>().configureEach { classpath = jmh.runtimeClasspath }

tasks.register<Jmh>("jmh") {
    description = "Run the JMH benchmarks once on G1."
    // Kept with --args, JMH rejects a second -f, so jmhFull runs the other forks and collectors
    argumentProviders.add { listOf("-f", "1", "-p", "gc=G1") }
}

tasks.register<Jmh>("jmhFull") {
    description = "Run the JMH benchmarks in full."
}
