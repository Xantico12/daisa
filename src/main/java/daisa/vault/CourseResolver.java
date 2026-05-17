package daisa.vault;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

// Resolves which course a markdown note belongs to.
//
// DAISA's vault layout is Study/AU/S<n>/<COURSE>/<...>/note.md, e.g.
// Study/AU/S2/OOP/lecture3.md. The course directory is the one whose parent
// matches the semester pattern (S1, S2, ...). All notes under that course
// directory (at any depth) share the same per-course artifact files.
//
// resolve() returns Optional.empty() in two cases — out of scope, or in
// scope but not under a recognized course. Both should be ignored by the
// orchestrator.
public final class CourseResolver {
    public static final String DEFAULT_SCOPE_ROOT = "Study/AU";
    private static final Pattern SEMESTER = Pattern.compile("S\\d+");
    private static final String TASKS_SUFFIX = " — Tasks.md";
    private static final String SUMMARIES_SUFFIX = " — Summaries.md";

    private final Path vaultRoot;
    private final Path scopeRoot;

    public CourseResolver(Path vaultRoot, String scopeRoot) {
        this.vaultRoot = Objects.requireNonNull(vaultRoot, "vaultRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(scopeRoot, "scopeRoot");
        this.scopeRoot = this.vaultRoot.resolve(scopeRoot).toAbsolutePath().normalize();
    }

    public static CourseResolver fromEnv(Path vaultRoot) {
        String scope = System.getenv("DAISA_SCOPE_ROOT");
        return new CourseResolver(vaultRoot, scope == null || scope.isEmpty() ? DEFAULT_SCOPE_ROOT : scope);
    }

    public Optional<Course> resolve(Path notePath) {
        Path absolute = notePath.toAbsolutePath().normalize();
        if (!absolute.startsWith(scopeRoot)) {
            return Optional.empty();
        }
        Path cursor = absolute.getParent();
        while (cursor != null && cursor.startsWith(scopeRoot) && !cursor.equals(scopeRoot)) {
            Path parent = cursor.getParent();
            if (parent != null && SEMESTER.matcher(parent.getFileName().toString()).matches()) {
                return Optional.of(new Course(cursor, cursor.getFileName().toString()));
            }
            cursor = parent;
        }
        return Optional.empty();
    }

    // Loop guard: a generated artifact is any file ending in the per-course
    // tasks or summaries suffix. We check by suffix (not equality) because
    // the filename is built from the course name, which varies.
    public static boolean isGeneratedArtifact(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(TASKS_SUFFIX) || fileName.endsWith(SUMMARIES_SUFFIX);
    }

    public Path vaultRoot() {
        return vaultRoot;
    }

    public Path scopeRoot() {
        return scopeRoot;
    }

    public static final class Course {
        private final Path directory;
        private final String name;

        public Course(Path directory, String name) {
            this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
            this.name = Objects.requireNonNull(name, "name");
        }

        public Path directory() {
            return directory;
        }

        public String name() {
            return name;
        }

        public Path tasksFile() {
            return directory.resolve(name + TASKS_SUFFIX);
        }

        public Path summariesFile() {
            return directory.resolve(name + SUMMARIES_SUFFIX);
        }
    }
}
