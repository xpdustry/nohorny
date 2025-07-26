import com.xpdustry.ksr.kotlinRelocate
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.indra.common)
    alias(libs.plugins.indra.git)
    alias(libs.plugins.indra.publishing)
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
    alias(libs.plugins.ksr)
    alias(libs.plugins.dokka)
}

val metadata = ModMetadata.fromJson(rootProject.file("plugin.json"))
if (indraGit.headTag() == null) metadata.version += "-SNAPSHOT"
group = "com.xpdustry"
version = metadata.version
description = metadata.description

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenCentral()
    anukeXpdustry()
}

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly(libs.kotlinx.coroutines.jdk8)
    compileOnly(libs.kotlinx.serialization.json)

    compileOnly(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryCore)

    compileOnly(libs.slf4j.api)
    testImplementation(libs.slf4j.simple)

    implementation(libs.okhttp)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.guava)
    implementation(libs.hikari) {
        exclude("org.slf4j")
    }

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishSnapshotsTo("xpdustry", "https://maven.xpdustry.com/snapshots")
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    mitLicense()

    if (metadata.repository.isNotBlank()) {
        val repo = metadata.repository.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            organization {
                name = "xpdustry"
                url = "https://www.xpdustry.com"
            }
        }
    }
}

spotless {
    kotlin {
        ktlint()
        licenseHeaderFile(rootProject.file("HEADER.txt"))
    }
    kotlinGradle {
        ktlint()
    }
}

kotlin {
    explicitApi()
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
}

val generateMetadataFile by tasks.registering {
    inputs.property("metadata", metadata)
    val output = temporaryDir.resolve("plugin.json")
    outputs.file(output)
    doLast { output.writeText(ModMetadata.toJson(metadata)) }
}

tasks.shadowJar {
    archiveFileName = "${metadata.name}.jar"
    archiveClassifier = "plugin"

    from(generateMetadataFile)
    from(rootProject.file("LICENSE.md")) { into("META-INF") }

    val shadowPackage = "com.xpdustry.nohorny.shadow"
    kotlinRelocate("com.sksamuel.hoplite", "$shadowPackage.hoplite")
    kotlinRelocate("okhttp3", "$shadowPackage.okhttp3")
    kotlinRelocate("okio", "$shadowPackage.okio")
    relocate("org.yaml.snakeyaml", "$shadowPackage.snakeyaml")
    relocate("com.google.common", "$shadowPackage.guava")
    relocate("com.zaxxer.hikari", "$shadowPackage.hikari")

    mergeServiceFiles()
    minimize {
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
    }
}

tasks.javadocJar {
    from(tasks.dokkaHtml)
}

tasks.register<Copy>("release") {
    dependsOn(tasks.build)
    from(tasks.shadowJar)
    destinationDir = temporaryDir
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md.jar"
    version = "v${libs.versions.slf4md.get()}"
}

val downloadSql4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "sql4md"
    asset = "sql4md.jar"
    version = "v${libs.versions.sql4md.get()}"
}

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "kotlin-runtime"
    asset = "kotlin-runtime.jar"
    version = "v${libs.versions.kotlin.runtime.get()}-k.${libs.versions.kotlin.core.get()}"
}

tasks.runMindustryServer {
    mods.from(downloadSlf4md, downloadSql4md, downloadKotlinRuntime)
}
