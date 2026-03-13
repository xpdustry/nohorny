import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless") version "8.2.1"
    id("net.kyori.indra") version "4.0.0"
    id("net.kyori.indra.publishing") version "4.0.0"
    id("com.gradleup.shadow") version "9.3.1"
    id("com.xpdustry.toxopid") version "4.2.0"
    id("net.ltgt.errorprone") version "4.4.0"
}

group = "com.xpdustry"
version = "4.0.0-beta.1" + if (findProperty("release").toString().toBoolean()) "" else "-SNAPSHOT"
description = "NO HORNY IN MY SERVER!"

val metadata =
    ModMetadata(
        name = "nohorny",
        displayName = "NoHorny",
        description = description!!,
        author = "Xpdustry",
        version = version.toString(),
        mainClass = "com.xpdustry.nohorny.NoHornyPlugin",
        repository = "xpdustry/nohorny",
        java = true,
        hidden = true,
        minGameVersion = "155",
        dependencies =
            mutableListOf(
                ModDependency("slf4md"),
                ModDependency("sql4md-mariadb", soft = true),
            ),
    )

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenCentral()
    anukeXpdustry()
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    compileOnly(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryHeadless)
    testImplementation(toxopid.dependencies.arcCore)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    compileOnlyApi("org.jspecify:jspecify:1.0.0")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.github.gestalt-config:gestalt-core:0.36.0")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.github.mizosoft.methanol:methanol:1.9.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.yaml:snakeyaml:2.6")
    implementation("ai.djl:api:0.36.0")
    runtimeOnly("ai.djl.pytorch:pytorch-engine:0.36.0")
    annotationProcessor("com.uber.nullaway:nullaway:0.12.15")
    errorprone("com.google.errorprone:error_prone_core:2.46.0")
}

configurations.runtimeClasspath {
    exclude(group = "org.slf4j")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
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
        }
    }
}

spotless {
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
    relocate("org.github.gestalt", "com.xpdustry.nohorny.shadow.gestalt")
    relocate("com.github.mizosoft.methanol", "com.xpdustry.nohorny.shadow.methanol")
    relocate("org.yaml.snakeyaml", "com.xpdustry.nohorny.shadow.snakeyaml")
    mergeServiceFiles()
    minimize {
        exclude(dependency("ai.djl.*:.*:.*"))
        exclude(dependency("com.github.mizosoft.methanol:methanol:.*"))
    }
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md.jar"
    version = "v1.2.0"
}

val downloadSql4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "sql4md"
    asset = "sql4md-mariadb.jar"
    version = "v2.0.1"
}

tasks.runMindustryServer {
    mods.from(downloadSlf4md, downloadSql4md)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.requireClean {
    enabled = false
}

tasks.withType<MindustryExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaCompile> {
    options.errorprone {
        disableWarningsInGeneratedCode = true
        disable("MissingSummary", "InlineMeSuggester")
        check("NullAway", if (name.contains("test", ignoreCase = true)) CheckSeverity.OFF else CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.xpdustry.slf4md")
    }
}
