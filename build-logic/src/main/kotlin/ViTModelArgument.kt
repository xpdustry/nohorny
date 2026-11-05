import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

class ViTModelArgument(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val model: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dnohorny.test.vit-model=${model.singleFile.absolutePath}")
}
