package daisa.study;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MarkdownNote {
    private final Path path;
    private final String title;
    private final List<String> headings;
    private final List<String> tags;
    private final List<TodoItem> todos;
    private final String content;

    public MarkdownNote(Path path, String title, List<String> headings, List<String> tags, List<TodoItem> todos, String content) {
        this.path = Objects.requireNonNull(path, "path");
        this.title = Objects.requireNonNull(title, "title");
        this.headings = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(headings, "headings")));
        this.tags = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(tags, "tags")));
        this.todos = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(todos, "todos")));
        this.content = Objects.requireNonNull(content, "content");
    }

    public Path path() {
        return path;
    }

    public String title() {
        return title;
    }

    public List<String> headings() {
        return headings;
    }

    public List<String> tags() {
        return tags;
    }

    public List<TodoItem> todos() {
        return todos;
    }

    public String content() {
        return content;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}

