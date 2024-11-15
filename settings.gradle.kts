pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.xpdustry.com/releases") {
            name = "xpdustry-releases"
            mavenContent { releasesOnly() }
        }
    }
}

// The project name, used as the name of the final artifact
rootProject.name = "nohorny"
