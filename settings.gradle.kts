pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.xpdustry.com/releases") {
            name = "xpdustry-releases"
            mavenContent { releasesOnly() }
        }
        maven("https://maven.xpdustry.com/snapshots") {
            name = "xpdustry-snapshots"
            mavenContent { snapshotsOnly() }
        }
    }
}

// The project name, used as the name of the final artifact
rootProject.name = "nohorny"
