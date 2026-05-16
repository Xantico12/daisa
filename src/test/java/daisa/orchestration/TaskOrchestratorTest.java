package daisa.orchestration;

import daisa.TestSupport;
import daisa.ai.MockCloudAiClient;
import daisa.ai.MockLocalAiClient;
import daisa.agent.AgentSupervisor;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;
import daisa.vault.StudyArtifactWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public final class TaskOrchestratorTest {
    private TaskOrchestratorTest() {
    }

    public static void run() throws Exception {
        ignoresGeneratedArtifactFiles();
    }

    private static void ignoresGeneratedArtifactFiles() throws Exception {
        Path vault = Files.createTempDirectory("daisa-orchestrator-test");
        Path generatedTasksFile = vault.resolve("DAISA Tasks.md");
        String originalContent = "# Existing\n- [ ] Keep me\n";
        Files.write(generatedTasksFile, originalContent.getBytes(StandardCharsets.UTF_8));

        TaskOrchestrator orchestrator = new TaskOrchestrator(
                AgentSupervisor.withDefaultAgents(),
                new AiRouter(new MockLocalAiClient(), new MockCloudAiClient()),
                new StudyArtifactWriter(vault)
        );

        MarkdownNote generatedNote = new MarkdownNote(
                generatedTasksFile,
                "DAISA Tasks",
                Collections.singletonList("DAISA Tasks"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Ignore this", 2, false)),
                originalContent
        );

        orchestrator.onMarkdownChanged(generatedTasksFile);
        orchestrator.handleNote(generatedNote);

        String after = new String(Files.readAllBytes(generatedTasksFile), StandardCharsets.UTF_8);
        TestSupport.assertEquals(originalContent, after);
    }
}
