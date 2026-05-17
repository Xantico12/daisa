package daisa.vault;

import daisa.TestSupport;
import daisa.ai.AiEngineType;
import daisa.ai.AiResponse;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

public final class StudyArtifactWriterTest {
    private StudyArtifactWriterTest() {
    }

    public static void run() throws Exception {
        runFirstWriteCreatesArtifacts();
        runTodosRewriteReplacesPreviousSection();
        runTodosForDifferentSourcesCoexist();
        runSummaryRewriteReplacesPreviousSection();
        runEmptyTodosRemovesStaleSection();
    }

    private static void runFirstWriteCreatesArtifacts() throws Exception {
        Path vault = Files.createTempDirectory("daisa-vault-test");
        Path notePath = vault.resolve("lecture.md");
        MarkdownNote note = new MarkdownNote(
                notePath,
                "Lecture",
                Collections.singletonList("Lecture"),
                Collections.singletonList("exam"),
                Arrays.asList(
                        new TodoItem("Read chapter 4", 3, false),
                        new TodoItem("Done item", 4, true)
                ),
                "# Lecture"
        );

        StudyArtifactWriter writer = new StudyArtifactWriter(vault);
        writer.writeTodos(note);
        writer.writeSummary(note, new AiResponse(AiEngineType.LOCAL, "Short summary"));

        String tasks = readArtifact(vault, "DAISA Tasks.md");
        String summaries = readArtifact(vault, "DAISA Summaries.md");

        TestSupport.assertTrue(tasks.contains("- [ ] Read chapter 4"), "Expected open todo in task artifact");
        TestSupport.assertTrue(!tasks.contains("Done item"), "Completed todos should not be copied");
        TestSupport.assertTrue(summaries.contains("Short summary"), "Expected summary text");
        TestSupport.assertTrue(summaries.contains("Engine: `LOCAL`"), "Expected engine marker");
    }

    private static void runTodosRewriteReplacesPreviousSection() throws Exception {
        Path vault = Files.createTempDirectory("daisa-vault-test");
        Path notePath = vault.resolve("lecture.md");

        MarkdownNote first = new MarkdownNote(
                notePath, "Lecture",
                Collections.singletonList("Lecture"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Read chapter 4", 3, false)),
                "# Lecture"
        );
        MarkdownNote second = new MarkdownNote(
                notePath, "Lecture",
                Collections.singletonList("Lecture"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Read chapter 5", 3, false)),
                "# Lecture"
        );

        StudyArtifactWriter writer = new StudyArtifactWriter(vault);
        writer.writeTodos(first);
        writer.writeTodos(second);

        String tasks = readArtifact(vault, "DAISA Tasks.md");
        TestSupport.assertTrue(tasks.contains("Read chapter 5"), "Latest todo should be present");
        TestSupport.assertTrue(!tasks.contains("Read chapter 4"), "Stale todo from previous write should be gone");
        TestSupport.assertTrue(countOccurrences(tasks, "## Lecture") == 1,
                "Exactly one section per source note expected, got: " + tasks);
    }

    private static void runTodosForDifferentSourcesCoexist() throws Exception {
        Path vault = Files.createTempDirectory("daisa-vault-test");

        MarkdownNote noteA = new MarkdownNote(
                vault.resolve("a.md"), "Note A",
                Collections.singletonList("Note A"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task A", 1, false)),
                "# Note A"
        );
        MarkdownNote noteB = new MarkdownNote(
                vault.resolve("b.md"), "Note B",
                Collections.singletonList("Note B"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task B", 1, false)),
                "# Note B"
        );

        StudyArtifactWriter writer = new StudyArtifactWriter(vault);
        writer.writeTodos(noteA);
        writer.writeTodos(noteB);

        String tasks = readArtifact(vault, "DAISA Tasks.md");
        TestSupport.assertTrue(tasks.contains("Task A") && tasks.contains("Task B"),
                "Both sources should have sections after their first writes");

        MarkdownNote noteAv2 = new MarkdownNote(
                vault.resolve("a.md"), "Note A",
                Collections.singletonList("Note A"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task A2", 1, false)),
                "# Note A"
        );
        writer.writeTodos(noteAv2);

        tasks = readArtifact(vault, "DAISA Tasks.md");
        TestSupport.assertTrue(tasks.contains("Task A2"), "Rewritten section should appear");
        TestSupport.assertTrue(!tasks.contains("Task A "), "Old Task A wording should be gone");
        TestSupport.assertTrue(tasks.contains("Task B"), "Untouched source should still be present");
        TestSupport.assertTrue(countOccurrences(tasks, "- Source: [[a]]") == 1, "One section for a.md");
        TestSupport.assertTrue(countOccurrences(tasks, "- Source: [[b]]") == 1, "One section for b.md");
    }

    private static void runSummaryRewriteReplacesPreviousSection() throws Exception {
        Path vault = Files.createTempDirectory("daisa-vault-test");
        Path notePath = vault.resolve("lecture.md");
        MarkdownNote note = new MarkdownNote(
                notePath, "Lecture",
                Collections.singletonList("Lecture"),
                Collections.singletonList("exam"),
                Collections.emptyList(),
                "# Lecture"
        );

        StudyArtifactWriter writer = new StudyArtifactWriter(vault);
        writer.writeSummary(note, new AiResponse(AiEngineType.LOCAL, "First summary"));
        writer.writeSummary(note, new AiResponse(AiEngineType.LOCAL, "Second summary"));

        String summaries = readArtifact(vault, "DAISA Summaries.md");
        TestSupport.assertTrue(summaries.contains("Second summary"), "Latest summary should be present");
        TestSupport.assertTrue(!summaries.contains("First summary"), "Stale summary should be gone");
        TestSupport.assertTrue(countOccurrences(summaries, "- Source: [[lecture]]") == 1,
                "One summary section per source");
    }

    private static void runEmptyTodosRemovesStaleSection() throws Exception {
        Path vault = Files.createTempDirectory("daisa-vault-test");
        Path aPath = vault.resolve("a.md");
        Path bPath = vault.resolve("b.md");

        MarkdownNote noteA = new MarkdownNote(
                aPath, "Note A",
                Collections.singletonList("Note A"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task A", 1, false)),
                "# Note A"
        );
        MarkdownNote noteB = new MarkdownNote(
                bPath, "Note B",
                Collections.singletonList("Note B"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task B", 1, false)),
                "# Note B"
        );

        StudyArtifactWriter writer = new StudyArtifactWriter(vault);
        writer.writeTodos(noteA);
        writer.writeTodos(noteB);

        // Note A's task gets completed: writer is called again with no open todos.
        MarkdownNote noteAdone = new MarkdownNote(
                aPath, "Note A",
                Collections.singletonList("Note A"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Task A", 1, true)),
                "# Note A"
        );
        writer.writeTodos(noteAdone);

        String tasks = readArtifact(vault, "DAISA Tasks.md");
        TestSupport.assertTrue(!tasks.contains("Task A"), "Stale section for completed source should be removed");
        TestSupport.assertTrue(tasks.contains("Task B"), "Other sources should be untouched");
        TestSupport.assertTrue(countOccurrences(tasks, "- Source: [[a]]") == 0, "No section for a.md should remain");
        TestSupport.assertTrue(countOccurrences(tasks, "- Source: [[b]]") == 1, "Section for b.md should remain");

        // Also covers the no-todos-at-all case: empty todos list, no prior section, no file created.
        Path freshVault = Files.createTempDirectory("daisa-vault-test");
        MarkdownNote emptyNote = new MarkdownNote(
                freshVault.resolve("empty.md"), "Empty",
                Collections.singletonList("Empty"),
                Collections.emptyList(),
                Collections.emptyList(),
                "# Empty"
        );
        new StudyArtifactWriter(freshVault).writeTodos(emptyNote);
        TestSupport.assertTrue(!Files.exists(freshVault.resolve("DAISA Tasks.md")),
                "Artifact must not be created when there's nothing to remove");
    }

    private static String readArtifact(Path vault, String name) throws Exception {
        return new String(Files.readAllBytes(vault.resolve(name)), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) return count;
            count++;
            from = idx + needle.length();
        }
    }
}
