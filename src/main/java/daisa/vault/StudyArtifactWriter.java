package daisa.vault;

import daisa.ai.AiResponse;
import daisa.study.MarkdownNote;
import daisa.study.TodoItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StudyArtifactWriter {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Path vaultRoot;

    public StudyArtifactWriter(Path vaultRoot) {
        this.vaultRoot = Objects.requireNonNull(vaultRoot, "vaultRoot");
    }

    public void writeSummary(Path targetFile, MarkdownNote note, AiResponse response) {
        String sourceLink = toWikilink(note.path());
        String section = "## " + note.title() + "\n"
                + "- Source: " + sourceLink + "\n"
                + "- Engine: `" + response.engineType() + "`\n"
                + "- Generated: " + TIMESTAMP.format(LocalDateTime.now()) + "\n\n"
                + response.text() + "\n";
        upsertSection(targetFile, sourceLink, section);
    }

    public void writeTodos(Path tasksFile, MarkdownNote note) {
        String sourceLink = toWikilink(note.path());

        List<TodoItem> openTodos = new ArrayList<>();
        for (TodoItem todo : note.todos()) {
            if (!todo.complete()) {
                openTodos.add(todo);
            }
        }

        // No open todos for this source: any prior section for it is now
        // stale, so remove it. This keeps the artifact in sync when a
        // user completes the last remaining task in a note.
        if (openTodos.isEmpty()) {
            removeSectionForSource(tasksFile, sourceLink);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(note.title()).append("\n");
        builder.append("- Source: ").append(sourceLink).append("\n");
        for (TodoItem todo : openTodos) {
            builder.append("- [ ] ").append(todo.text()).append(" (line ").append(todo.lineNumber()).append(")\n");
        }
        upsertSection(tasksFile, sourceLink, builder.toString());
    }

    // Build an Obsidian wikilink for the note, relative to the vault root.
    // Obsidian resolves `[[notes/lecture]]` to `notes/lecture.md`, so we
    // strip the trailing ".md" and normalize separators to forward slashes
    // (Windows paths would otherwise break the link).
    private String toWikilink(Path notePath) {
        String rel = vaultRoot.relativize(notePath).toString().replace('\\', '/');
        if (rel.endsWith(".md")) {
            rel = rel.substring(0, rel.length() - ".md".length());
        }
        return "[[" + rel + "]]";
    }

    // Remove any section for this source without writing a new one.
    // Used when a note no longer has open todos and its section is now stale.
    // No-op if the artifact file does not exist.
    private void removeSectionForSource(Path path, String sourceLink) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            String existing = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String marker = "- Source: " + sourceLink;
            String retained = removeSectionWithMarker(existing, marker);
            if (retained.equals(existing)) {
                return;
            }
            Files.write(path, retained.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to clean stale section in: " + path, failure);
        }
    }

    // Idempotent write: replace any existing top-level (##) section whose
    // "- Source: [[<wikilink>]]" marker matches, then append the new section.
    // The artifact is rewritten in full so the file ends up with exactly
    // one section per source note.
    private void upsertSection(Path path, String sourceLink, String newSection) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            String existing = Files.exists(path)
                    ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                    : "";

            String marker = "- Source: " + sourceLink;
            String retained = removeSectionWithMarker(existing, marker);

            StringBuilder out = new StringBuilder(retained);
            if (out.length() > 0 && !endsWithBlankLine(out)) {
                if (out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                out.append('\n');
            }
            out.append(newSection);

            Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to write study artifact: " + path, failure);
        }
    }

    private static String removeSectionWithMarker(String content, String marker) {
        if (content.isEmpty()) {
            return content;
        }
        String[] lines = content.split("\n", -1);
        List<String> kept = new ArrayList<>(lines.length);

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("## ")) {
                int sectionStart = i;
                int sectionEnd = i + 1;
                while (sectionEnd < lines.length && !lines[sectionEnd].startsWith("## ")) {
                    sectionEnd++;
                }
                boolean matches = false;
                for (int j = sectionStart; j < sectionEnd; j++) {
                    if (lines[j].equals(marker)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    for (int j = sectionStart; j < sectionEnd; j++) {
                        kept.add(lines[j]);
                    }
                }
                i = sectionEnd;
            } else {
                kept.add(line);
                i++;
            }
        }

        StringBuilder rebuilt = new StringBuilder();
        for (int k = 0; k < kept.size(); k++) {
            rebuilt.append(kept.get(k));
            if (k < kept.size() - 1) {
                rebuilt.append('\n');
            }
        }
        return rebuilt.toString();
    }

    private static boolean endsWithBlankLine(StringBuilder s) {
        int n = s.length();
        if (n < 2) return false;
        return s.charAt(n - 1) == '\n' && s.charAt(n - 2) == '\n';
    }
}
