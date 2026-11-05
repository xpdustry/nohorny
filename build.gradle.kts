plugins {
    alias(libs.plugins.spotless)
}

spotless {
    java {
        target(".github/scripts/*.java")
        palantirJavaFormat()
        formatAnnotations()
        importOrder("", "\\#")
        forbidModuleImports()
        forbidWildcardImports()
        licenseHeader("// SPDX-License-Identifier: MIT")
    }
    kotlin {
        target("build-logic/src/**/*.kt")
        ktlint().editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea", "max_line_length" to "120"))
    }
    // The default ktlint_official style is far more invasive than the IntelliJ one
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
        ktlint().editorConfigOverride(mapOf("ktlint_code_style" to "intellij_idea", "max_line_length" to "120"))
    }
}
