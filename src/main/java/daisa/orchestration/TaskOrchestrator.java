package daisa.orchestration;

import daisa.agent.AgentMessage;
import daisa.agent.AgentSupervisor;
import daisa.ai.AiResponse;
import daisa.study.MarkdownNote;
import daisa.study.StudyTask;
import daisa.study.StudyTaskType;
import daisa.vault.CourseResolver;
import daisa.vault.CourseResolver.Course;
import daisa.vault.MarkdownParser;
import daisa.vault.StudyArtifactWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class TaskOrchestrator {
    private final AgentSupervisor supervisor;
    private final AiRouter aiRouter;
    private final StudyArtifactWriter artifactWriter;
    private final MarkdownParser markdownParser;
    private final CourseResolver courseResolver;

    public TaskOrchestrator(
            AgentSupervisor supervisor,
            AiRouter aiRouter,
            StudyArtifactWriter artifactWriter,
            CourseResolver courseResolver
    ) {
        this(supervisor, aiRouter, artifactWriter, new MarkdownParser(), courseResolver);
    }

    public TaskOrchestrator(
            AgentSupervisor supervisor,
            AiRouter aiRouter,
            StudyArtifactWriter artifactWriter,
            MarkdownParser markdownParser,
            CourseResolver courseResolver
    ) {
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.aiRouter = Objects.requireNonNull(aiRouter, "aiRouter");
        this.artifactWriter = Objects.requireNonNull(artifactWriter, "artifactWriter");
        this.markdownParser = Objects.requireNonNull(markdownParser, "markdownParser");
        this.courseResolver = Objects.requireNonNull(courseResolver, "courseResolver");
    }

    public void onMarkdownChanged(Path path) {
        try {
            if (CourseResolver.isGeneratedArtifact(path)) {
                return;
            }
            MarkdownNote note = markdownParser.parse(path, new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            handleNote(note);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to read changed markdown file: " + path, failure);
        }
    }

    public void handleNote(MarkdownNote note) {
        if (CourseResolver.isGeneratedArtifact(note.path())) {
            return;
        }
        // Notes outside the scope root, or inside the scope root but not
        // under any recognized course, are silently ignored. Per-course
        // scoping means a note has to have a course owner for its artifacts
        // to land somewhere sensible.
        Optional<Course> resolved = courseResolver.resolve(note.path());
        if (!resolved.isPresent()) {
            return;
        }
        Course course = resolved.get();

        supervisor.restartUnhealthyAgents();

        if (!note.todos().isEmpty()) {
            StudyTask task = new StudyTask(
                    StudyTaskType.EXTRACT_TODOS,
                    note.path(),
                    "Extract open todos from " + note.title(),
                    true
            );
            supervisor.dispatch(new AgentMessage(task));
            artifactWriter.writeTodos(course.tasksFile(), note);
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
            artifactWriter.writeSummary(course.summariesFile(), note, response);
        }
    }
}
