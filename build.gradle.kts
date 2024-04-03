import com.xpdustry.ksr.kotlinRelocate
import fr.xpdustry.toxopid.dsl.mindustryDependencies
import fr.xpdustry.toxopid.spec.ModMetadata
import fr.xpdustry.toxopid.spec.ModPlatform
import fr.xpdustry.toxopid.task.GithubArtifactDownload
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    kotlin("jvm") version "1.9.23"
    id("com.diffplug.spotless") version "6.25.0"
    id("net.kyori.indra") version "3.1.3"
    id("net.kyori.indra.publishing") version "3.1.3"
    id("net.kyori.indra.git") version "3.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("fr.xpdustry.toxopid") version "3.2.0"
    id("com.xpdustry.ksr") version "2.0.0-SNAPSHOT"
    id("org.jetbrains.dokka") version "1.9.20"
}

val metadata = ModMetadata.fromJson(file("plugin.json"))

// Remove the following line if you don't want snapshot versions
if (indraGit.headTag() == null) {
    metadata.version += "-SNAPSHOT"
}

group = "com.xpdustry"
version = metadata.version
description = metadata.description

toxopid {
    compileVersion.set("v${metadata.minGameVersion}")
    platforms.set(setOf(ModPlatform.HEADLESS))
}

repositories {
    mavenCentral()

    // This repository provides mindustry artifacts built by xpdustry
    maven("https://maven.xpdustry.com/mindustry") {
        name = "xpdustry-mindustry"
        mavenContent { releasesOnly() }
    }

    // This repository provides xpdustry libraries, such as the distributor-api
    maven("https://maven.xpdustry.com/releases") {
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    mindustryDependencies()
    compileOnly(kotlin("stdlib-jdk8"))

    compileOnly("org.slf4j:slf4j-api:2.0.12")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.sksamuel.hoplite:hoplite-core:2.7.5")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.7.5")

    val junit = "5.10.2"
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit")
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
}

kotlin {
    jvmToolchain(17)
    explicitApi = ExplicitApiMode.Strict
    target {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = "17"
                apiVersion = "1.9"
            }
        }
    }
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    if (metadata.repo.isNotBlank()) {
        val repo = metadata.repo.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            organization {
                name.set("xpdustry")
                url.set("https://www.xpdustry.com")
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spotless {
    kotlin {
        fun toLongComment(text: String) =
            buildString {
                appendLine("/*")
                text.lines().forEach { appendLine(" * ${it.trim()}") }
                appendLine(" */")
            }

        ktfmt().dropboxStyle()
        licenseHeader(toLongComment(rootProject.file("LICENSE_HEADER.md").readText()))
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
    }
}

// Required for the GitHub actions
tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.shadowJar {
    archiveFileName.set("${metadata.name}.jar")
    archiveClassifier.set("plugin")

    val relocationPackage = "com.xpdustry.nohorny.shadow"
    kotlinRelocate("okhttp3", "$relocationPackage.okhttp3")
    kotlinRelocate("okio", "$relocationPackage.okio")
    kotlinRelocate("com.sksamuel.hoplite", "$relocationPackage.hoplite")
    relocate("org.yaml.snakeyaml", "$relocationPackage.snakeyaml")

    mergeServiceFiles()
    minimize {
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
    }

    exclude("META-INF/proguard/**")

    doFirst {
        val temp = temporaryDir.resolve("plugin.json")
        temp.writeText(metadata.toJson(true))
        from(temp)
    }

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
    }
}

tasks.build {
    // Make sure the shadow jar is built during the build task
    dependsOn(tasks.shadowJar)
}

tasks.javadocJar {
    from(tasks.dokkaHtml)
}

val downloadDistributorCore =
    tasks.register<GithubArtifactDownload>("downloadDistributorCore") {
        user.set("xpdustry")
        repo.set("distributor")
        version.set("v3.3.0")
        name.set("distributor-core.jar")
    }

val downloadKotlinRuntime =
    tasks.register<GithubArtifactDownload>("downloadKotlinRuntime") {
        user.set("xpdustry")
        repo.set("kotlin-runtime")
        name.set("kotlin-runtime.jar")
        version.set("v3.2.0-k.1.9.23")
    }

tasks.runMindustryServer {
    mods.setFrom(tasks.shadowJar, downloadKotlinRuntime)
    // mods.from(downloadDistributorCore)
}
