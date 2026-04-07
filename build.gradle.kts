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

plugins {
    id("com.diffplug.spotless") version "8.3.0" apply false
    id("net.kyori.indra") version "4.0.0" apply false
    id("net.kyori.indra.publishing") version "4.0.0" apply false
    id("com.gradleup.shadow") version "9.3.2" apply false
    id("com.xpdustry.toxopid") version "4.2.0" apply false
    id("net.ltgt.errorprone") version "4.4.0" apply false
    id("org.springframework.boot") version "4.0.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.xpdustry"
version = "4.0.0-beta.1" + if (findProperty("release").toString().toBoolean()) "" else "-SNAPSHOT"
description = "NO HORNY IN MY SERVER!"

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.kyori.indra")
    apply(plugin = "net.kyori.indra.publishing")
    apply(plugin = "net.ltgt.errorprone")

    repositories {
        mavenCentral()
        anukeXpdustry()
    }

    dependencies {
        "errorprone"("com.google.errorprone:error_prone_core:2.46.0")
        "compileOnlyApi"("org.jspecify:jspecify:1.0.0")
        "annotationProcessor"("com.uber.nullaway:nullaway:0.12.15")
        "testAnnotationProcessor"("com.uber.nullaway:nullaway:0.12.15")
        "testImplementation"("org.junit.jupiter:junit-jupiter:6.0.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    configure<SigningExtension> {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
    }

    configure<IndraExtension> {
        javaVersions {
            target(25)
            minimumToolchain(25)
        }

        // publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
        // publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

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
            palantirJavaFormat()
            formatAnnotations()
            importOrder("", "\\#")
            forbidModuleImports()
            forbidWildcardImports()
            licenseHeader("// SPDX-License-Identifier: MIT")
        }
        kotlinGradle {
            ktlint()
        }
    }

    tasks.withType<RequireClean> {
        enabled = false
    }

    tasks.withType<JavaCompile> {
        options.errorprone {
            disableWarningsInGeneratedCode = true
            disable("MissingSummary", "InlineMeSuggester")
            option("NullAway:OnlyNullMarked")
            check("NullAway", CheckSeverity.ERROR)
        }
    }
}

project(":nohorny-server") {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-webmvc")
        "testImplementation"("org.springframework.boot:spring-boot-starter-webmvc-test")
        "developmentOnly"("org.springframework.boot:spring-boot-devtools")

        "implementation"(project(":nohorny-common"))

        "implementation"("ai.djl:api:0.36.0")
        "runtimeOnly"("ai.djl.onnxruntime:onnxruntime-engine:0.36.0")
        "runtimeOnly"("ai.djl.pytorch:pytorch-engine:0.36.0")

        "implementation"("org.springframework.boot:spring-boot-starter-validation")
    }

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
    }
}

project(":nohorny-client") {
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
            minGameVersion = "156.2",
            dependencies = mutableListOf(ModDependency("slf4md")),
        )

    val toxopid = extensions.getByType<ToxopidExtension>()
    toxopid.platforms = setOf(ModPlatform.SERVER)
    toxopid.compileVersion = "v${metadata.minGameVersion}"

    repositories {
        anukeXpdustry()
    }

    dependencies {
        "compileOnly"(toxopid.dependencies.mindustryCore)
        "testImplementation"(toxopid.dependencies.mindustryCore)
        "compileOnly"(toxopid.dependencies.arcCore)
        "testImplementation"(toxopid.dependencies.arcCore)
        "compileOnly"(toxopid.dependencies.mindustryHeadless)
        "testImplementation"(toxopid.dependencies.mindustryHeadless)

        "implementation"(project(":nohorny-common"))

        "implementation"("com.google.code.gson:gson:2.13.2")
        "compileOnly"("org.slf4j:slf4j-api:2.0.17")
        "testImplementation"("org.slf4j:slf4j-api:2.0.17")
        "testRuntimeOnly"("org.slf4j:slf4j-simple:2.0.17")
    }

    configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
        exclude(group = "org.slf4j")
    }

    val generateMetadataFile by tasks.registering {
        inputs.property("metadata", metadata)
        val output = temporaryDir.resolve("plugin.json")
        outputs.file(output)
        doLast { output.writeText(ModMetadata.toJson(metadata)) }
    }

    tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME) {
        archiveFileName = "${project.name}.jar"
        archiveClassifier = "shaded"
        from(rootProject.file("LICENSE.md")) { into("META-INF") }
        mergeServiceFiles()
        relocate("com.google.code.gson", "com.xpdustry.nohorny.client.shadow.gson")
        from(generateMetadataFile)
    }

    tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
        dependsOn(tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME))
    }

    val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
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
    }
}
