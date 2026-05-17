package daisa.orchestration;

import daisa.TestSupport;
import daisa.agent.AgentSupervisor;
import daisa.ai.AiEngineType;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;
import daisa.vault.StudyArtifactWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

public final class TaskOrchestratorTest {
    private TaskOrchestratorTest() {
    }

    public static void run() throws Exception {
        runTodosNoteWritesTaskArtifactWithoutAiCall();
        runExamTagTriggersSummaryThroughRouter();
        runPrivateNoteForcesLocalEngine();
        runGeneratedArtifactsAreSkipped();
    }

    private static void runTodosNoteWritesTaskArtifactWithoutAiCall() throws Exception {
        Fixture fx = new Fixture();
        MarkdownNote note = new MarkdownNote(
                fx.vault.resolve("lecture.md"),
                "Lecture",
                Collections.singletonList("Lecture"),
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("Read chapter 4", 3, false)),
                "# Lecture"
        );

        fx.orchestrator.handleNote(note);

        String tasks = readArtifact(fx.vault, "DAISA Tasks.md");
        TestSupport.assertTrue(tasks.contains("- [ ] Read chapter 4"),
                "Tasks artifact should contain the open todo");
        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "Todo extraction is deterministic; no AI client should be consulted");
        TestSupport.assertTrue(!Files.exists(fx.vault.resolve("DAISA Summaries.md")),
                "Notes without summarize/exam tag should not produce a summary artifact");
    }

    private static void runExamTagTriggersSummaryThroughRouter() throws Exception {
        Fixture fx = new Fixture();
        MarkdownNote note = new MarkdownNote(
                fx.vault.resolve("exam-notes.md"),
                "Exam Notes",
                Collections.singletonList("Exam Notes"),
                Collections.singletonList("exam"),
                Collections.emptyList(),
                "Some study content"
        );

        fx.orchestrator.handleNote(note);

        String summaries = readArtifact(fx.vault, "DAISA Summaries.md");
        TestSupport.assertTrue(summaries.contains("LOCAL-canned"),
                "Summary artifact should contain the canned local AI response");
        TestSupport.assertTrue(fx.localAi.callCount() == 1,
                "Exam-tagged note should route to local engine for a short prompt");
        TestSupport.assertTrue(fx.cloudAi.callCount() == 0,
                "Cloud engine should not be consulted for a short exam-tagged note");
    }

    private static void runPrivateNoteForcesLocalEngine() throws Exception {
        Fixture fx = new Fixture();
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longContent.append("This is a long line about distributed systems and supervision. ");
        }
        MarkdownNote note = new MarkdownNote(
                fx.vault.resolve("private-notes.md"),
                "Private",
                Collections.singletonList("Private"),
                Arrays.asList("summarize", "private"),
                Collections.emptyList(),
                longContent.toString()
        );

        fx.orchestrator.handleNote(note);

        TestSupport.assertTrue(fx.localAi.callCount() == 1, "Private-tagged note must hit local engine");
        TestSupport.assertTrue(fx.cloudAi.callCount() == 0,
                "Private-tagged note must never reach cloud engine, even with long content");
        TestSupport.assertTrue(fx.localAi.requests().get(0).privacySensitive(),
                "AiRequest reaching local client should carry the privacy-sensitive flag");
    }

    private static void runGeneratedArtifactsAreSkipped() throws Exception {
        Fixture fx = new Fixture();
        Path tasksFile = fx.vault.resolve("DAISA Tasks.md");
        Files.write(tasksFile, "## existing\n- Source: [[other]]\n".getBytes(StandardCharsets.UTF_8));
        long sizeBefore = Files.size(tasksFile);

        fx.orchestrator.onMarkdownChanged(tasksFile);

        TestSupport.assertTrue(Files.size(tasksFile) == sizeBefore,
                "Generated artifact must not be re-processed by the orchestrator");
        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "No AI calls should happen for a generated-artifact path");
    }

    private static String readArtifact(Path vault, String name) throws Exception {
        return new String(Files.readAllBytes(vault.resolve(name)), StandardCharsets.UTF_8);
    }

    // Common collaborators wired the same way every test case uses them.
    // The real AgentSupervisor is constructed but never started: dispatch
    // enqueues messages into the agents' mailboxes, but no consumer thread
    // runs, which is exactly what we want for fast deterministic tests.
    private static final class Fixture {
        final Path vault;
        final RecordingAiClient localAi;
        final RecordingAiClient cloudAi;
        final TaskOrchestrator orchestrator;

        Fixture() throws Exception {
            this.vault = Files.createTempDirectory("daisa-orchestrator-test");
            this.localAi = new RecordingAiClient(AiEngineType.LOCAL, "LOCAL-canned");
            this.cloudAi = new RecordingAiClient(AiEngineType.CLOUD, "CLOUD-canned");
            AgentSupervisor supervisor = AgentSupervisor.withDefaultAgents();
            AiRouter router = new AiRouter(localAi, cloudAi);
            StudyArtifactWriter writer = new StudyArtifactWriter(vault);
            this.orchestrator = new TaskOrchestrator(supervisor, router, writer);
        }
    }
}
