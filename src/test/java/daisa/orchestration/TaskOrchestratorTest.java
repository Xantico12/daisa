package daisa.orchestration;

import daisa.TestSupport;
import daisa.agent.AgentSupervisor;
import daisa.ai.AiEngineType;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;
import daisa.vault.CourseResolver;
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
        runTodosNoteWritesPerCourseTaskArtifactWithoutAiCall();
        runExamTagTriggersSummaryThroughRouter();
        runPrivateNoteForcesLocalEngine();
        runGeneratedArtifactsAreSkippedBySuffix();
        runNoteOutsideScopeRootIsIgnored();
        runNoteInsideScopeButOutsideAnyCourseIsIgnored();
        runTwoCoursesWriteToSeparateArtifactFiles();
    }

    private static void runTodosNoteWritesPerCourseTaskArtifactWithoutAiCall() throws Exception {
        Fixture fx = new Fixture();
        Path note = fx.courseNote("OOP", "lecture.md");
        MarkdownNote parsed = noteOf(note, "Lecture", Collections.emptyList(),
                Collections.singletonList(new TodoItem("Read chapter 4", 3, false)));

        fx.orchestrator.handleNote(parsed);

        String tasks = readArtifact(fx.course("OOP").resolve("OOP — Tasks.md"));
        TestSupport.assertTrue(tasks.contains("- [ ] Read chapter 4"),
                "Per-course Tasks artifact should contain the open todo");
        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "Todo extraction is deterministic; no AI client should be consulted");
        TestSupport.assertTrue(!Files.exists(fx.course("OOP").resolve("OOP — Summaries.md")),
                "Notes without summarize/exam tag should not produce a summary artifact");
    }

    private static void runExamTagTriggersSummaryThroughRouter() throws Exception {
        Fixture fx = new Fixture();
        Path note = fx.courseNote("OOP", "exam-notes.md");
        MarkdownNote parsed = noteOf(note, "Exam Notes",
                Collections.singletonList("exam"), Collections.emptyList());

        fx.orchestrator.handleNote(parsed);

        String summaries = readArtifact(fx.course("OOP").resolve("OOP — Summaries.md"));
        TestSupport.assertTrue(summaries.contains("LOCAL-canned"),
                "Summary artifact should contain the canned local AI response");
        TestSupport.assertTrue(fx.localAi.callCount() == 1,
                "Exam-tagged note should route to local engine for a short prompt");
        TestSupport.assertTrue(fx.cloudAi.callCount() == 0,
                "Cloud engine should not be consulted for a short exam-tagged note");
    }

    private static void runPrivateNoteForcesLocalEngine() throws Exception {
        Fixture fx = new Fixture();
        Path note = fx.courseNote("PLA", "private-notes.md");
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            longContent.append("This is a long line about distributed systems and supervision. ");
        }
        MarkdownNote parsed = new MarkdownNote(note, "Private",
                Collections.singletonList("Private"),
                Arrays.asList("summarize", "private"),
                Collections.emptyList(),
                longContent.toString());

        fx.orchestrator.handleNote(parsed);

        TestSupport.assertTrue(fx.localAi.callCount() == 1, "Private-tagged note must hit local engine");
        TestSupport.assertTrue(fx.cloudAi.callCount() == 0,
                "Private-tagged note must never reach cloud engine, even with long content");
        TestSupport.assertTrue(fx.localAi.requests().get(0).privacySensitive(),
                "AiRequest reaching local client should carry the privacy-sensitive flag");
    }

    private static void runGeneratedArtifactsAreSkippedBySuffix() throws Exception {
        Fixture fx = new Fixture();
        // Loop guard now matches by suffix, not a fixed filename, because
        // per-course artifacts vary by course name. Both forms must skip.
        Path tasks = fx.course("OOP").resolve("OOP — Tasks.md");
        Path summaries = fx.course("OOP").resolve("OOP — Summaries.md");
        Files.write(tasks, "## existing\n".getBytes(StandardCharsets.UTF_8));
        Files.write(summaries, "## existing\n".getBytes(StandardCharsets.UTF_8));
        long tasksSize = Files.size(tasks);
        long summariesSize = Files.size(summaries);

        fx.orchestrator.onMarkdownChanged(tasks);
        fx.orchestrator.onMarkdownChanged(summaries);

        TestSupport.assertTrue(Files.size(tasks) == tasksSize,
                "Per-course Tasks artifact must not be re-processed by the orchestrator");
        TestSupport.assertTrue(Files.size(summaries) == summariesSize,
                "Per-course Summaries artifact must not be re-processed by the orchestrator");
        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "No AI calls should happen for generated-artifact paths");
    }

    private static void runNoteOutsideScopeRootIsIgnored() throws Exception {
        Fixture fx = new Fixture();
        Path outsideScope = fx.vault.resolve("Inbox/random.md");
        Files.createDirectories(outsideScope.getParent());
        MarkdownNote parsed = noteOf(outsideScope, "Random",
                Collections.singletonList("exam"),
                Collections.singletonList(new TodoItem("do it", 1, false)));

        fx.orchestrator.handleNote(parsed);

        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "Out-of-scope notes must not trigger AI calls");
        try (java.util.stream.Stream<Path> walk = Files.walk(fx.vault)) {
            boolean anyArtifact = walk.anyMatch(p ->
                    p.getFileName().toString().endsWith(" — Tasks.md")
                    || p.getFileName().toString().endsWith(" — Summaries.md"));
            TestSupport.assertTrue(!anyArtifact,
                    "Out-of-scope notes must not produce any artifact files");
        }
    }

    private static void runNoteInsideScopeButOutsideAnyCourseIsIgnored() throws Exception {
        Fixture fx = new Fixture();
        // Inside Study/AU but not under a semester directory: no course owns this.
        Path orphan = fx.vault.resolve("Study/AU/orphan.md");
        Files.createDirectories(orphan.getParent());
        MarkdownNote parsed = noteOf(orphan, "Orphan",
                Collections.singletonList("exam"),
                Collections.singletonList(new TodoItem("do it", 1, false)));

        fx.orchestrator.handleNote(parsed);

        TestSupport.assertTrue(fx.localAi.callCount() == 0 && fx.cloudAi.callCount() == 0,
                "Scope-root notes without a course should not call AI");
        TestSupport.assertTrue(!Files.exists(fx.vault.resolve("Study/AU/orphan — Tasks.md")),
                "No fallback artifact should be created for orphan notes");
    }

    private static void runTwoCoursesWriteToSeparateArtifactFiles() throws Exception {
        Fixture fx = new Fixture();
        Path oop = fx.courseNote("OOP", "lecture.md");
        Path pla = fx.courseNote("PLA", "tutorial.md");

        fx.orchestrator.handleNote(noteOf(oop, "OOP Lecture",
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("oop task", 1, false))));
        fx.orchestrator.handleNote(noteOf(pla, "PLA Tutorial",
                Collections.emptyList(),
                Collections.singletonList(new TodoItem("pla task", 1, false))));

        String oopTasks = readArtifact(fx.course("OOP").resolve("OOP — Tasks.md"));
        String plaTasks = readArtifact(fx.course("PLA").resolve("PLA — Tasks.md"));
        TestSupport.assertTrue(oopTasks.contains("oop task") && !oopTasks.contains("pla task"),
                "OOP artifact should only contain OOP tasks");
        TestSupport.assertTrue(plaTasks.contains("pla task") && !plaTasks.contains("oop task"),
                "PLA artifact should only contain PLA tasks");
    }

    private static MarkdownNote noteOf(Path path, String title,
                                       java.util.List<String> tags,
                                       java.util.List<TodoItem> todos) {
        return new MarkdownNote(path, title, Collections.singletonList(title), tags, todos, "# " + title);
    }

    private static String readArtifact(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
            CourseResolver resolver = new CourseResolver(vault, CourseResolver.DEFAULT_SCOPE_ROOT);
            this.orchestrator = new TaskOrchestrator(supervisor, router, writer, resolver);
        }

        Path course(String name) throws Exception {
            Path dir = vault.resolve("Study/AU/S2/" + name);
            Files.createDirectories(dir);
            return dir;
        }

        Path courseNote(String courseName, String fileName) throws Exception {
            return course(courseName).resolve(fileName);
        }
    }
}
