import com.diffplug.gradle.spotless.JavaExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.xpdustry.toxopid.ToxopidExtension
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec
import net.kyori.indra.IndraExtension
import net.kyori.indra.git.task.RequireClean
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.tasks.run.BootRun
import java.util.Locale
import javax.inject.Inject

plugins {
    id("com.diffplug.spotless") version "8.10.3" apply false
    id("net.kyori.indra") version "4.1.0" apply false
    id("net.kyori.indra.publishing") version "4.1.0" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("com.xpdustry.toxopid") version "4.2.0" apply false
    id("net.ltgt.errorprone") version "5.1.1" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.xpdustry"
    version = "4.0.0-beta.11" + if (findProperty("is_release").toString().toBoolean()) "" else "-SNAPSHOT"
    description = "NO HORNY IN MY SERVER!"
}

val spotlessJava: JavaExtension.() -> Unit = {
    palantirJavaFormat()
    formatAnnotations()
    importOrder("", "\\#")
    forbidModuleImports()
    forbidWildcardImports()
    licenseHeader("// SPDX-License-Identifier: MIT")
}

// The CI scripts and the build scripts live in the root project
apply(plugin = "com.diffplug.spotless")
repositories {
    mavenCentral()
}
configure<SpotlessExtension> {
    java {
        target(".github/scripts/*.java")
        spotlessJava()
    }
    // The default ktlint_official style is far more invasive than the IntelliJ one
    kotlinGradle {
        target("*.gradle.kts")
        ktlint().editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea", "max_line_length" to "120"))
    }
}

// nohorny-native is a pure CMake project
configure(subprojects - project(":nohorny-native")) {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.kyori.indra")
    apply(plugin = "net.ltgt.errorprone")

    repositories {
        mavenCentral()
        anukeXpdustry()
    }

    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:2.50.0")
        "errorprone"("com.uber.nullaway:nullaway:0.14.2")
        "compileOnlyApi"("org.jspecify:jspecify:1.0.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.1.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    configure<IndraExtension> {
        javaVersions {
            target(25)
            minimumToolchain(25)
        }

        publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
        publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

        mitLicense()

        github("xpdustry", "nohorny") {
            ci(true)
            issues(true)
            scm(true)
        }

        configurePublications {
            pom {
                organization {
                    name = "xpdustry"
                    url = "https://www.xpdustry.com"
                }

                developers {
                    developer {
                        id.set("Phinner")
                        timezone.set("Europe/Brussels")
                    }

                    developer {
                        id.set("ZetaMap")
                        timezone.set("Europe/Paris")
                    }
                }
            }
        }
    }

    configure<SpotlessExtension> {
        java {
            spotlessJava()
        }
    }

    tasks.withType<RequireClean> {
        enabled = false
    }

    tasks.withType<JavaCompile> {
        options.errorprone {
            disableWarningsInGeneratedCode = true
            excludedPaths = ".*/build/generated/.*"
            disable("MissingSummary", "InlineMeSuggester")
            option("NullAway:OnlyNullMarked")
            check("NullAway", CheckSeverity.ERROR)
        }
    }
}

project(":nohorny-client") {
    apply(plugin = "net.kyori.indra.publishing")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "com.xpdustry.toxopid")

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

    val toxopid = the<ToxopidExtension>()
    toxopid.platforms = setOf(ModPlatform.SERVER)
    toxopid.compileVersion = "v${metadata.minGameVersion}"

    repositories {
        anukeXpdustry()
    }

    dependencies {
        "api"(project(":nohorny-common"))
        "compileOnly"(toxopid.dependencies.mindustryCore)
        "testImplementation"(toxopid.dependencies.mindustryCore)
        "compileOnly"(toxopid.dependencies.arcCore)
        "testImplementation"(toxopid.dependencies.arcCore)
        "compileOnly"(toxopid.dependencies.mindustryHeadless)
        "testImplementation"(toxopid.dependencies.mindustryHeadless)
        "testImplementation"("com.code-intelligence:jazzer-junit:0.30.0")
    }

    configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
        exclude(group = "org.slf4j")
        exclude(group = "com.google.errorprone")
    }

    val generateMetadataFile = tasks.register("generateMetadataFile") {
        description = "Generate the plugin.json file."
        inputs.property("metadata", metadata)
        val output = temporaryDir.resolve("plugin.json")
        outputs.file(output)
        doLast { output.writeText(ModMetadata.toJson(metadata)) }
    }

    tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME) {
        archiveFileName = "${project.name}.jar"
        archiveClassifier = "plugin"
        from(rootProject.file("LICENSE.md")) { into("META-INF") }
        mergeServiceFiles()
        from(generateMetadataFile)
        minimize()
    }

    tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
        dependsOn(tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME))
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

    tasks.withType<Test> {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    val testSourceSet = the<SourceSetContainer>()[SourceSet.TEST_SOURCE_SET_NAME]
    tasks.register<Test>("fuzz") {
        description = "Run the fuzz tests in fuzzing mode."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform()
        filter.includeTestsMatching("*FuzzTest")
        environment("JAZZER_FUZZ", "1")
        outputs.upToDateWhen { false }
    }

    val mainSourceSet = the<SourceSetContainer>()[SourceSet.MAIN_SOURCE_SET_NAME]
    val jmhSourceSet =
        the<SourceSetContainer>().create("jmh") {
            compileClasspath += mainSourceSet.output
            runtimeClasspath += mainSourceSet.output
        }

    configurations.named(jmhSourceSet.implementationConfigurationName) {
        extendsFrom(configurations[JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME])
    }

    dependencies {
        "jmhImplementation"("org.openjdk.jmh:jmh-core:1.37")
        "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
        "jmhCompileOnly"(toxopid.dependencies.mindustryCore)
        "jmhCompileOnly"(toxopid.dependencies.arcCore)
        // The real server jar, which also bundles the assets required by the logic processors
        "jmhRuntimeOnly"(files(tasks.named("downloadMindustryServer")))
    }

    tasks.register<JavaExec>("jmh") {
        description = "Run the JMH benchmarks, use -Pjmh=\"<args>\" to pass arguments to JMH."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        classpath = jmhSourceSet.runtimeClasspath
        mainClass = "org.openjdk.jmh.Main"
        val results = layout.buildDirectory.file("jmh/results.json")
        outputs.file(results)
        outputs.upToDateWhen { false }
        args(providers.gradleProperty("jmh").getOrElse("").split(' ').filter { it.isNotBlank() })
        args("-rf", "json", "-rff", results.get().asFile.absolutePath)
        doFirst { results.get().asFile.parentFile.mkdirs() }
    }

    tasks.withType<MindustryExec> {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        args("host Shattered sandbox,rules add planet sun")
    }
}

abstract class CMakeBuild : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val platform: Property<String>

    // Kept between builds, OpenCV takes a while to compile
    @get:Internal
    abstract val buildDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        // The native library is optional, the server gracefully fails to load it when missing
        onlyIf("CMake is available") {
            val available =
                try {
                    ProcessBuilder("cmake", "--version").redirectErrorStream(true).start().run {
                        inputStream.readAllBytes()
                        waitFor() == 0
                    }
                } catch (_: java.io.IOException) {
                    false
                }
            if (!available) logger.warn("CMake is not available, skipping the nohorny native library")
            available
        }
    }

    @TaskAction
    fun build() {
        // CMake relinks the library if it is missing, so it does not leave stale files behind
        fs.delete { delete(outputDirectory) }
        val output = outputDirectory.get().dir("natives/${platform.get()}").asFile
        exec.exec {
            commandLine(
                "cmake",
                "-S",
                sourceDirectory.get().asFile,
                "-B",
                buildDirectory.get().asFile,
                "-DCMAKE_BUILD_TYPE=${buildType.get()}",
                "-DNOHORNY_OUTPUT_DIRECTORY=$output",
            )
        }
        // Without a job count, the Makefile generator spawns as many jobs as there are sources
        val jobs = Runtime.getRuntime().availableProcessors()
        exec.exec {
            commandLine(
                "cmake",
                "--build",
                buildDirectory.get().asFile,
                "--config",
                buildType.get(),
                "--parallel",
                jobs,
            )
        }
    }
}

project(":nohorny-native") {
    apply(plugin = "base")

    // Must match the platform naming of NoHornyNative
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val platformOs =
        when {
            "win" in os -> "windows"
            "mac" in os -> "macos"
            else -> "linux"
        }
    val platformArch = if (arch == "amd64" || arch == "x86_64") "x86_64" else arch
    val platform = "$platformOs-$platformArch"

    val cmakeBuild = tasks.register<CMakeBuild>("cmakeBuild") {
        description = "Compile the native library with CMake."
        sourceDirectory = layout.projectDirectory
        sources.from("CMakeLists.txt", "cmake", "src")
        buildType = "Release"
        this.platform = platform
        buildDirectory = layout.buildDirectory.dir("cmake")
        outputDirectory = layout.buildDirectory.dir("generated/native")
    }

    configurations.consumable("natives") {
        outgoing.artifact(cmakeBuild.flatMap { it.outputDirectory })
    }
}

project(":nohorny-server") {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "api"(project(":nohorny-common"))

        "implementation"("org.springframework.boot:spring-boot-starter-webmvc")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")
        "implementation"("org.springframework.shell:spring-shell-starter:4.0.3")
        "testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
        "developmentOnly"("org.springframework.boot:spring-boot-devtools")
    }

    // CI builds the natives of each platform separately, then bundles them all with -Pprebuilt_natives=<dir>
    val nativesScope = configurations.dependencyScope("natives")
    val natives = configurations.resolvable("nativesFiles") { extendsFrom(nativesScope.get()) }
    dependencies {
        "natives"(project(path = ":nohorny-native", configuration = "natives"))
    }
    val prebuilt = providers.gradleProperty("prebuilt_natives").map { rootProject.file(it) }
    the<SourceSetContainer>()[SourceSet.MAIN_SOURCE_SET_NAME].resources.srcDir(
        if (prebuilt.isPresent) prebuilt else natives,
    )

    tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME) {
        archiveClassifier = "plain"
    }

    tasks.named<Jar>("bootJar") {
        archiveClassifier = "boot"
        archiveFileName = "${project.name}.jar"
    }

    tasks.named<BootRun>("bootRun") {
        workingDir = temporaryDir
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        args("start")
    }
}

configure(listOf(project(":nohorny-common"), project(":nohorny-client"))) {
    apply(plugin = "net.kyori.indra.publishing")
    configure<SigningExtension> {
        useInMemoryPgpKeys(findProperty("signingKey")?.toString(), findProperty("signingPassword")?.toString())
    }
}
