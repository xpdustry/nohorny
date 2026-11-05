plugins {
    base
}

val pnpmInstall = tasks.register<Pnpm>("pnpmInstall") {
    description = "Install the dependencies with pnpm."
    arguments = listOf("install", "--frozen-lockfile")
    inputs.files("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml")
    outputs.dir("node_modules")
}

tasks.withType<Pnpm>().configureEach {
    workingDirectory.convention(layout.projectDirectory)
    if (name != pnpmInstall.name) dependsOn(pnpmInstall)
}
