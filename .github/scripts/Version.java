// SPDX-License-Identifier: MIT
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/// Reads, bumps and validates the project version of the version catalog,
/// run with `java .github/scripts/Version.java <get|bump|validate>`.
/// Bumping also points the dependencies of README.md to the released version, the one before the bump.
public final class Version {

    private static final Path CATALOG_FILE = Path.of("gradle", "libs.versions.toml");
    private static final Path README_FILE = Path.of("README.md");
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^nohorny = \"(?<version>(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(?:-(?<label>[a-zA-Z]+)\\.(?<number>\\d+))?)\"$");
    private static final Pattern DEPENDENCY = Pattern.compile("(com\\.xpdustry:nohorny-(?:common|plugin):)[^\"]+");

    public static void main(final String[] args) throws Exception {
        try {
            run(args);
        } catch (final Failure failure) {
            System.err.println("::error::" + failure.getMessage());
            System.exit(1);
        }
    }

    private static void run(final String[] args) throws IOException, InterruptedException {
        if (args.length != 1 || !(args[0].equals("get") || args[0].equals("bump") || args[0].equals("validate"))) {
            throw new Failure("Usage: Version.java <get|bump|validate>");
        }

        final var content = Files.readString(CATALOG_FILE);
        final var matches = DECLARATION.matcher(content).results().toList();
        if (matches.size() != 1) {
            throw new Failure("Expected exactly one project version matching '" + DECLARATION + "' in " + CATALOG_FILE);
        }

        final var declaration = matches.getFirst();
        switch (args[0]) {
            case "get" -> System.out.println(declaration.group("version"));
            case "validate" -> validate(declaration.group("version"));
            default -> bump(content, declaration);
        }
    }

    /// A release must be tagged with the project version, a draft must not reuse an existing tag.
    /// Other builds only warn, commits predating the version bump of a release still have to build
    private static void validate(final String version) throws IOException, InterruptedException {
        final var tag = "v" + version;
        if ("release".equals(System.getenv("GITHUB_EVENT_NAME"))) {
            final var ref = System.getenv("GITHUB_REF_NAME");
            if (!tag.equals(ref)) {
                throw new Failure("Release tag '" + ref + "' does not match the project version '" + tag + "'");
            }
        } else if (tagExists(tag)) {
            if ("true".equals(System.getenv("IS_DRAFT"))) {
                throw new Failure("'" + tag + "' already exists as a git tag");
            }
            System.out.println("::warning::'" + tag
                    + "' already exists as a git tag, the version needs a bump before the next release");
        }
    }

    private static boolean tagExists(final String tag) throws IOException, InterruptedException {
        final var git = new ProcessBuilder("git", "tag", "-l", tag)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        final var output = new String(git.getInputStream().readAllBytes());
        if (git.waitFor() != 0) {
            throw new Failure("Failed to list the git tags");
        }
        return !output.isBlank();
    }

    private static void bump(final String catalog, final MatchResult declaration) throws IOException {
        // A prerelease bumps its number, a release bumps the patch
        final var prefix = declaration.group("major") + "." + declaration.group("minor") + ".";
        final var next = declaration.group("label") == null
                ? prefix + (Integer.parseInt(declaration.group("patch")) + 1)
                : prefix + declaration.group("patch") + "-" + declaration.group("label") + "."
                        + (Integer.parseInt(declaration.group("number")) + 1);

        final var readme = Files.readString(README_FILE);
        if (!DEPENDENCY.matcher(readme).find()) {
            throw new Failure("Expected the dependencies matching '" + DEPENDENCY + "' in " + README_FILE);
        }

        final var newCatalog = catalog.replace(declaration.group(), "nohorny = \"" + next + "\"");
        final var newReadme = DEPENDENCY.matcher(readme).replaceAll("$1" + declaration.group("version"));
        Files.writeString(CATALOG_FILE, newCatalog);
        Files.writeString(README_FILE, newReadme);
        System.out.println(next);
    }

    private static final class Failure extends RuntimeException {

        Failure(final String message) {
            super(message);
        }
    }
}
