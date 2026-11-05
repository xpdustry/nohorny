@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://maven.xpdustry.com/releases") {
            name = "xpdustry-releases"
            mavenContent { includeGroup("com.xpdustry") }
        }
        maven("https://maven.xpdustry.com/mindustry") {
            name = "anuke-xpdustry"
            mavenContent { includeGroupByRegex("^com\\.github\\.Anuken(\\.[\\w-]+)*$") }
            metadataSources {
                gradleMetadata()
                mavenPom()
                artifact()
            }
        }
        // Lets Gradle download and cache the ViT model of the server tests like any other dependency
        exclusiveContent {
            forRepository {
                ivy("https://huggingface.co") {
                    name = "huggingface"
                    patternLayout { artifact("[organisation]/[module]/resolve/[revision]/onnx/[artifact].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter {
                includeGroup("onnx-community")
            }
        }
    }
}

rootProject.name = "nohorny"

includeBuild("build-logic")

include("nohorny-common")
include("nohorny-plugin")
include("nohorny-frontend")
include("nohorny-server")
