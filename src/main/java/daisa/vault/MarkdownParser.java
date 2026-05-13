package daisa.vault;

import daisa.study.MarkdownNote;
import daisa.study.TodoItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownParser {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern TODO = Pattern.compile("^\\s*[-*]\\s+\\[([ xX])\\]\\s+(.+?)\\s*$");
    private static final Pattern TAG = Pattern.compile("(?<!\\w)#([A-Za-z][A-Za-z0-9_/-]*)");

    public MarkdownNote parse(Path path, String content) {
        String[] lines = content.split("\\R", -1);
        List<String> headings = new ArrayList<>();
        List<TodoItem> todos = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];

            Matcher headingMatcher = HEADING.matcher(line);
            if (headingMatcher.matches()) {
                headings.add(headingMatcher.group(2).trim());
            }

            Matcher todoMatcher = TODO.matcher(line);
            if (todoMatcher.matches()) {
                boolean complete = "x".equalsIgnoreCase(todoMatcher.group(1));
                todos.add(new TodoItem(todoMatcher.group(2).trim(), index + 1, complete));
            }

            Matcher tagMatcher = TAG.matcher(line);
            while (tagMatcher.find()) {
                tags.add(tagMatcher.group(1));
            }
        }

        String title = headings.isEmpty() ? stripMarkdownExtension(path.getFileName().toString()) : headings.get(0);
        return new MarkdownNote(path, title, headings, new ArrayList<>(tags), todos, content);
    }

    private static String stripMarkdownExtension(String fileName) {
        return fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
    }
}
