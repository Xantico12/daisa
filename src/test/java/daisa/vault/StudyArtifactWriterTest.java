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

        String tasks = new String(Files.readAllBytes(vault.resolve("DAISA Tasks.md")), StandardCharsets.UTF_8);
        String summaries = new String(Files.readAllBytes(vault.resolve("DAISA Summaries.md")), StandardCharsets.UTF_8);

        TestSupport.assertTrue(tasks.contains("- [ ] Read chapter 4"), "Expected open todo in task artifact");
        TestSupport.assertTrue(!tasks.contains("Done item"), "Completed todos should not be copied");
        TestSupport.assertTrue(summaries.contains("Short summary"), "Expected summary text");
        TestSupport.assertTrue(summaries.contains("Engine: `LOCAL`"), "Expected engine marker");
    }
}

