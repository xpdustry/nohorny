plugins {
    id("nohorny.pnpm")
}

val sources = files(
    "src",
    "public",
    "package.json",
    "pnpm-lock.yaml",
    "pnpm-workspace.yaml",
    "file-routes.d.ts",
    "tsconfig.json",
    "vite.config.ts",
    "biome.json",
)

val build = tasks.register<Pnpm>("pnpmBuild") {
    description = "Build the frontend with Vite."
    arguments = listOf("run", "build")
    inputs.files(sources)
    outputs.dir("dist")
}

val lint = tasks.register<Pnpm>("pnpmLint") {
    description = "Lint and format check the frontend with Biome."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    arguments = listOf("run", "lint")
    inputs.files(sources)
}

val typecheck = tasks.register<Pnpm>("pnpmCheck") {
    description = "Type check the frontend."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    arguments = listOf("run", "check")
    inputs.files(sources, "file-routes.d.ts")
}

tasks.check { dependsOn(lint, typecheck) }

tasks.assemble { dependsOn(build) }

configurations.register("frontend") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(layout.projectDirectory.dir("dist/client")) { builtBy(build) }
}
