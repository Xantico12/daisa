package daisa.vault;

import daisa.TestSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class CourseResolverTest {
    private CourseResolverTest() {
    }

    public static void run() throws Exception {
        runResolvesCourseDirectAtCourseRoot();
        runResolvesCourseFromNestedSubdir();
        runReturnsEmptyForNoteOutsideScopeRoot();
        runReturnsEmptyForNoteInScopeButOutsideAnyCourse();
        runArtifactPathsUseEmDashConvention();
        runRecognizesGeneratedArtifactBySuffix();
        runSemesterNumberAcceptsAnyDigits();
    }

    private static void runResolvesCourseDirectAtCourseRoot() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path note = vault.resolve("Study/AU/S2/OOP/lecture.md");
        Files.createDirectories(note.getParent());

        Optional<CourseResolver.Course> course =
                new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT).resolve(note);

        TestSupport.assertTrue(course.isPresent(), "Expected to resolve OOP course");
        TestSupport.assertEquals("OOP", course.get().name());
        TestSupport.assertEquals(vault.resolve("Study/AU/S2/OOP").toAbsolutePath().normalize(),
                course.get().directory());
    }

    private static void runResolvesCourseFromNestedSubdir() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path note = vault.resolve("Study/AU/S2/OOP/exam-drills/2025/q3.md");
        Files.createDirectories(note.getParent());

        Optional<CourseResolver.Course> course =
                new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT).resolve(note);

        TestSupport.assertTrue(course.isPresent(), "Deeply nested note should still resolve to OOP");
        TestSupport.assertEquals("OOP", course.get().name());
    }

    private static void runReturnsEmptyForNoteOutsideScopeRoot() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path note = vault.resolve("Inbox/random.md");
        Files.createDirectories(note.getParent());

        Optional<CourseResolver.Course> course =
                new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT).resolve(note);

        TestSupport.assertTrue(!course.isPresent(), "Notes outside Study/AU should not resolve to a course");
    }

    private static void runReturnsEmptyForNoteInScopeButOutsideAnyCourse() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path note = vault.resolve("Study/AU/orphan.md");
        Files.createDirectories(note.getParent());

        Optional<CourseResolver.Course> course =
                new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT).resolve(note);

        TestSupport.assertTrue(!course.isPresent(), "Notes under Study/AU but not in S<n>/<COURSE>/ should not resolve");
    }

    private static void runArtifactPathsUseEmDashConvention() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path courseDir = vault.resolve("Study/AU/S2/OOP");
        Files.createDirectories(courseDir);

        CourseResolver.Course course = new CourseResolver.Course(courseDir, "OOP");

        TestSupport.assertEquals(courseDir.resolve("OOP — Tasks.md").toAbsolutePath().normalize(),
                course.tasksFile());
        TestSupport.assertEquals(courseDir.resolve("OOP — Summaries.md").toAbsolutePath().normalize(),
                course.summariesFile());
    }

    private static void runRecognizesGeneratedArtifactBySuffix() {
        TestSupport.assertTrue(CourseResolver.isGeneratedArtifact(Path.of("OOP — Tasks.md")),
                "Tasks suffix should be recognized");
        TestSupport.assertTrue(CourseResolver.isGeneratedArtifact(Path.of("foo/bar/PLA — Summaries.md")),
                "Summaries suffix should be recognized regardless of parent directory");
        TestSupport.assertTrue(!CourseResolver.isGeneratedArtifact(Path.of("regular-note.md")),
                "Regular notes should not be flagged as generated artifacts");
        TestSupport.assertTrue(!CourseResolver.isGeneratedArtifact(Path.of("Tasks.md")),
                "Loose 'Tasks.md' without the em-dash prefix should not be flagged");
    }

    private static void runSemesterNumberAcceptsAnyDigits() throws Exception {
        Path vault = Files.createTempDirectory("daisa-resolver-test");
        Path note = vault.resolve("Study/AU/S10/CRYPTO/lecture.md");
        Files.createDirectories(note.getParent());

        Optional<CourseResolver.Course> course =
                new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT).resolve(note);

        TestSupport.assertTrue(course.isPresent(), "S10 semester should be recognized");
        TestSupport.assertEquals("CRYPTO", course.get().name());
    }
}
