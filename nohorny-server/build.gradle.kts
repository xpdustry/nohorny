plugins {
    id("nohorny.java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

val vitModel = configurations.create("vitModel") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// The pages are bundled as resources, either built here by nohorny-frontend
//   or taken from -Pprebuilt_frontend=<dir> with a static/ directory. The Docker build passes the latter
val frontend = configurations.create("frontend") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    api(project(":nohorny-common"))

    implementation(libs.nohorny.natives)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation(libs.sqlite.jdbc)
    // Provides the SQLite dialect
    implementation("org.hibernate.orm:hibernate-community-dialects")
    // Required by the Argon2 password encoder
    implementation(libs.bouncycastle)
    // Token buckets of the rate limits, held in a cache that forgets the idle ones
    implementation(libs.bucket4j)
    implementation("com.github.ben-manes.caffeine:caffeine")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    vitModel(libs.vit.model) {
        artifact {
            name = "model_fp16"
            type = "onnx"
            extension = "onnx"
        }
    }

    frontend(project(path = ":nohorny-frontend", configuration = "frontend"))
}

val prebuiltFrontend = providers.gradleProperty("prebuilt_frontend").map { rootProject.file(it) }
sourceSets.main {
    resources.srcDir(if (prebuiltFrontend.isPresent) prebuiltFrontend else frontend)
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    jvmArgumentProviders.add(ViTModelArgument(vitModel))
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.bootJar {
    archiveClassifier = "boot"
    archiveFileName = "${project.name}.jar"
    // The classifier natives and SQLite need native access, which the launcher reads from the manifest
    manifest.attributes("Enable-Native-Access" to "ALL-UNNAMED")
}

tasks.bootRun {
    workingDir = temporaryDir
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
