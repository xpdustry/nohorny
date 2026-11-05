import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.xpdustry"
version = libs.versions.nohorny.get() +
    if (providers.gradleProperty("is_release").map(String::toBoolean).getOrElse(false)) "" else "-SNAPSHOT"
description = "NO HORNY IN MY SERVER!"

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    compileOnlyApi(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
}

// Any JDK from 25 runs the build, the Docker image builds with the one it runs on
tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
        disable("MissingSummary", "InlineMeSuggester")
        option("NullAway:OnlyNullMarked")
        check("NullAway", CheckSeverity.ERROR)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
