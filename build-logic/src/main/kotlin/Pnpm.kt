import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class Pnpm : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @get:Input
    abstract val arguments: ListProperty<String>

    @TaskAction
    fun run() {
        exec.exec {
            workingDir(this@Pnpm.workingDirectory)
            // pnpm 12 is a native executable. It switches to the version in the packageManager field of package.json
            commandLine(listOf("pnpm") + arguments.get())
        }
    }
}
