package daisa.vault;

import daisa.ai.AiResponse;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class StudyArtifactWriter {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Path vaultRoot;

    public StudyArtifactWriter(Path vaultRoot) {
        this.vaultRoot = Objects.requireNonNull(vaultRoot, "vaultRoot");
    }

    public void writeSummary(MarkdownNote note, AiResponse response) {
        Path output = vaultRoot.resolve("DAISA Summaries.md");
        String section = "\n## " + note.title() + "\n"
                + "- Source: `" + vaultRoot.relativize(note.path()) + "`\n"
                + "- Engine: `" + response.engineType() + "`\n"
                + "- Generated: " + TIMESTAMP.format(LocalDateTime.now()) + "\n\n"
                + response.text() + "\n";
        append(output, section);
    }

    public void writeTodos(MarkdownNote note) {
        List<TodoItem> todos = note.todos();
        if (todos.isEmpty()) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n## ").append(note.title()).append("\n");
        builder.append("- Source: `").append(vaultRoot.relativize(note.path())).append("`\n");
        for (TodoItem todo : todos) {
            if (!todo.complete()) {
                builder.append("- [ ] ").append(todo.text()).append(" (line ").append(todo.lineNumber()).append(")\n");
            }
        }
        append(vaultRoot.resolve("DAISA Tasks.md"), builder.toString());
    }

    private static void append(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, content.getBytes(StandardCharsets.UTF_8),
                    Files.exists(path)
                            ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.APPEND}
                            : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE_NEW});
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to write study artifact: " + path, failure);
        }
    }
}

