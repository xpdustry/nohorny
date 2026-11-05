plugins {
    id("nohorny.java")
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:-missing", true)
}

val repository = "xpdustry/nohorny"

publishing {
    publications.register<MavenPublication>("maven") {
        from(components["java"])
        pom {
            name = project.name
            description = provider { project.description }
            url = "https://github.com/$repository"

            licenses {
                license {
                    name = "The MIT License"
                    url = "https://opensource.org/licenses/MIT"
                }
            }

            organization {
                name = "xpdustry"
                url = "https://www.xpdustry.com"
            }

            developers {
                developer {
                    id = "Phinner"
                    timezone = "Europe/Brussels"
                }

                developer {
                    id = "ZetaMap"
                    timezone = "Europe/Paris"
                }
            }

            scm {
                connection = "scm:git:https://github.com/$repository.git"
                developerConnection = "scm:git:ssh://git@github.com/$repository.git"
                url = "https://github.com/$repository"
            }

            issueManagement {
                system = "GitHub"
                url = "https://github.com/$repository/issues"
            }

            ciManagement {
                system = "GitHub Actions"
                url = "https://github.com/$repository/actions"
            }
        }
    }

    repositories {
        maven {
            name = "xpdustry"
            val release = !version.toString().endsWith("-SNAPSHOT")
            url = uri("https://maven.xpdustry.com/${if (release) "releases" else "snapshots"}")
            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    isRequired = providers.environmentVariable("CI").isPresent
    useInMemoryPgpKeys(
        providers.gradleProperty("signingKey").orNull,
        providers.gradleProperty("signingPassword").orNull,
    )
    sign(publishing.publications)
}
