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
    private static final Pattern FRONTMATTER_FENCE = Pattern.compile("^---\\s*$");
    // YAML "tags:" or "tag:" key. Captures the rest of the line (may be empty for block-list form).
    private static final Pattern FRONTMATTER_TAGS_KEY = Pattern.compile("^tags?:\\s*(.*)$");
    // Block-list entry under a tags: key, e.g. "  - exam".
    private static final Pattern FRONTMATTER_LIST_ITEM = Pattern.compile("^\\s*-\\s*(.+?)\\s*$");

    public MarkdownNote parse(Path path, String content) {
        String[] lines = content.split("\\R", -1);
        List<String> headings = new ArrayList<>();
        List<TodoItem> todos = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();

        // Frontmatter spans lines [0, frontmatterEnd]; -1 if absent. We skip
        // these lines from heading/todo/inline-tag parsing so YAML keys like
        // "title:" don't get mistaken for body content, and so todo line
        // numbers still refer to the original file.
        int frontmatterEnd = parseFrontmatterTags(lines, tags);

        for (int index = 0; index < lines.length; index++) {
            if (index <= frontmatterEnd) {
                continue;
            }
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

    // Recognize YAML frontmatter (fenced by --- on the first line and a later line)
    // and extract any "tags:" / "tag:" entries from it. Supports both YAML flow
    // ("tags: [exam, oop]") and block-list ("tags:\n  - exam") styles. Returns
    // the line index of the closing fence, or -1 if no frontmatter is present.
    // Intentionally not a full YAML parser — Obsidian's tag conventions are the
    // only thing we need here.
    private static int parseFrontmatterTags(String[] lines, Set<String> tags) {
        if (lines.length == 0 || !FRONTMATTER_FENCE.matcher(lines[0]).matches()) {
            return -1;
        }
        int closingFence = -1;
        for (int i = 1; i < lines.length; i++) {
            if (FRONTMATTER_FENCE.matcher(lines[i]).matches()) {
                closingFence = i;
                break;
            }
        }
        if (closingFence == -1) {
            return -1;
        }

        for (int i = 1; i < closingFence; i++) {
            Matcher keyMatcher = FRONTMATTER_TAGS_KEY.matcher(lines[i]);
            if (!keyMatcher.matches()) {
                continue;
            }
            String rest = keyMatcher.group(1).trim();
            if (rest.startsWith("[") && rest.endsWith("]")) {
                // Flow style: tags: [a, b, "c"]
                String inner = rest.substring(1, rest.length() - 1);
                for (String token : inner.split(",")) {
                    String cleaned = stripQuotes(token.trim());
                    if (!cleaned.isEmpty()) {
                        tags.add(cleaned);
                    }
                }
            } else if (!rest.isEmpty()) {
                // Inline scalar: tags: exam   (single value, no brackets)
                tags.add(stripQuotes(rest));
            } else {
                // Block list: subsequent indented "- value" lines until a non-list line.
                for (int j = i + 1; j < closingFence; j++) {
                    Matcher itemMatcher = FRONTMATTER_LIST_ITEM.matcher(lines[j]);
                    if (!itemMatcher.matches()) {
                        break;
                    }
                    String cleaned = stripQuotes(itemMatcher.group(1));
                    if (!cleaned.isEmpty()) {
                        tags.add(cleaned);
                    }
                }
            }
        }
        return closingFence;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                        || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripMarkdownExtension(String fileName) {
        return fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
    }
}
