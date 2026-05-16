package daisa.orchestration;

import daisa.agent.AgentMessage;
import daisa.agent.AgentSupervisor;
import daisa.ai.AiResponse;
import daisa.study.MarkdownNote;
import daisa.study.StudyTask;
import daisa.study.StudyTaskType;
import daisa.vault.MarkdownParser;
import daisa.vault.StudyArtifactWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class TaskOrchestrator {
    private static final String TASKS_ARTIFACT_NAME = "DAISA Tasks.md";
    private static final String SUMMARIES_ARTIFACT_NAME = "DAISA Summaries.md";

    private final AgentSupervisor supervisor;
    private final AiRouter aiRouter;
    private final StudyArtifactWriter artifactWriter;
    private final MarkdownParser markdownParser;

    public TaskOrchestrator(AgentSupervisor supervisor, AiRouter aiRouter, StudyArtifactWriter artifactWriter) {
        this(supervisor, aiRouter, artifactWriter, new MarkdownParser());
    }

    public TaskOrchestrator(
            AgentSupervisor supervisor,
            AiRouter aiRouter,
            StudyArtifactWriter artifactWriter,
            MarkdownParser markdownParser
    ) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.aiRouter = Objects.requireNonNull(aiRouter, "aiRouter");
        this.artifactWriter = Objects.requireNonNull(artifactWriter, "artifactWriter");
        this.markdownParser = Objects.requireNonNull(markdownParser, "markdownParser");
    }

    public void onMarkdownChanged(Path path) {
        try {
            if (isGeneratedArtifact(path)) {
                return;
            }
            MarkdownNote note = markdownParser.parse(path, new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            handleNote(note);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read changed markdown file: " + path, failure);
        }
    }

    public void handleNote(MarkdownNote note) {
        if (isGeneratedArtifact(note.path())) {
            return;
        }
        supervisor.restartUnhealthyAgents();

        if (!note.todos().isEmpty()) {
            StudyTask task = new StudyTask(
                    StudyTaskType.EXTRACT_TODOS,
                    note.path(),
                    "Extract open todos from " + note.title(),
                    true
            );
            supervisor.dispatch(new AgentMessage(task));
            artifactWriter.writeTodos(note);
        }

        if (note.hasTag("summarize") || note.hasTag("exam")) {
            StudyTask task = new StudyTask(
                    StudyTaskType.SUMMARIZE_NOTE,
                    note.path(),
                    "Summarize this note for study revision:\n\n" + note.content(),
                    note.hasTag("private")
            );
            supervisor.dispatch(new AgentMessage(task));
            AiResponse response = aiRouter.complete(task);
            artifactWriter.writeSummary(note, response);
        }
    }

    private static boolean isGeneratedArtifact(Path path) {
        String fileName = path.getFileName().toString();
        return TASKS_ARTIFACT_NAME.equals(fileName) || SUMMARIES_ARTIFACT_NAME.equals(fileName);
    }
}
