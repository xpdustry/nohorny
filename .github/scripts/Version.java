// SPDX-License-Identifier: MIT
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/// Reads and bumps the project version of build.gradle.kts, run with `java .github/scripts/Version.java <get|bump>`.
/// Bumping also points the dependencies of README.md to the released version, the one before the bump.
public final class Version {

    private static final Path BUILD_FILE = Path.of("build.gradle.kts");
    private static final Path README_FILE = Path.of("README.md");
    // Other version assignments exist in the build file, only the project version is a bare semver
    private static final Pattern DECLARATION =
            Pattern.compile("version = \"((\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z]+)\\.(\\d+))?)\"");
    private static final Pattern DEPENDENCY = Pattern.compile("(com\\.xpdustry:nohorny-(?:common|plugin):)[^\"]+");

    public static void main(final String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("get") || args[0].equals("bump"))) {
            System.err.println("::error::Usage: Version.java <get|bump>");
            System.exit(1);
        }

        final var content = Files.readString(BUILD_FILE);
        final var matches = DECLARATION.matcher(content).results().toList();
        if (matches.size() != 1) {
            System.err.println(
                    "::error::Expected exactly one project version matching '" + DECLARATION + "' in " + BUILD_FILE);
            System.exit(1);
        }

        final var declaration = matches.getFirst();
        if (args[0].equals("get")) {
            System.out.println(declaration.group(1));
            return;
        }

        // A prerelease bumps its number, a release bumps the patch
        final var next = declaration.group(5) == null
                ? declaration.group(2) + "." + declaration.group(3) + "." + (Integer.parseInt(declaration.group(4)) + 1)
                : declaration.group(2) + "." + declaration.group(3) + "." + declaration.group(4) + "-"
                        + declaration.group(5) + "." + (Integer.parseInt(declaration.group(6)) + 1);

        // Checked before writing anything, a missing dependency must not leave a bumped build file behind
        final var readme = Files.readString(README_FILE);
        if (!DEPENDENCY.matcher(readme).find()) {
            System.err.println("::error::Expected the dependencies matching '" + DEPENDENCY + "' in " + README_FILE);
            System.exit(1);
        }

        Files.writeString(BUILD_FILE, content.replace(declaration.group(), "version = \"" + next + "\""));
        Files.writeString(README_FILE, DEPENDENCY.matcher(readme).replaceAll("$1" + declaration.group(1)));
        System.out.println(next);
    }
}
