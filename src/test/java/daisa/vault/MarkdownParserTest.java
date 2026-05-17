package daisa.vault;

import daisa.TestSupport;
import daisa.study.MarkdownNote;

import java.nio.file.Paths;

public final class MarkdownParserTest {
    private MarkdownParserTest() {
    }

    public static void run() {
        String content = "# Distributed Systems #exam\n"
                + "\n"
                + "## Replication\n"
                + "- [ ] Read CAP theorem paper #todo\n"
                + "- [x] Finish lecture questions\n"
                + "Plain #summarize text\n";

        MarkdownNote note = new MarkdownParser().parse(Paths.get("lecture.md"), content);

        TestSupport.assertEquals("Distributed Systems #exam", note.title());
        TestSupport.assertEquals(2, note.headings().size());
        TestSupport.assertTrue(note.hasTag("exam"), "Expected #exam tag");
        TestSupport.assertTrue(note.hasTag("todo"), "Expected #todo tag");
        TestSupport.assertTrue(note.hasTag("summarize"), "Expected #summarize tag");
        TestSupport.assertEquals(2, note.todos().size());
        TestSupport.assertEquals("Read CAP theorem paper #todo", note.todos().get(0).text());
        TestSupport.assertEquals(4, note.todos().get(0).lineNumber());
        TestSupport.assertEquals(false, note.todos().get(0).complete());
        TestSupport.assertEquals(true, note.todos().get(1).complete());

        frontmatterFlowStyleTagsAreExtracted();
        frontmatterBlockStyleTagsAreExtracted();
        frontmatterDoesNotPolluteHeadingsOrShiftTodoLineNumbers();
        frontmatterAndInlineTagsBothLand();
        unclosedFrontmatterIsTreatedAsBody();
    }

    private static void frontmatterFlowStyleTagsAreExtracted() {
        String content = "---\n"
                + "tags: [exam, oop, \"foo-bar\"]\n"
                + "---\n"
                + "# Polymorphism\n";
        MarkdownNote note = new MarkdownParser().parse(Paths.get("n.md"), content);
        TestSupport.assertTrue(note.hasTag("exam"), "flow-style exam tag");
        TestSupport.assertTrue(note.hasTag("oop"), "flow-style oop tag");
        TestSupport.assertTrue(note.hasTag("foo-bar"), "quoted flow-style tag");
    }

    private static void frontmatterBlockStyleTagsAreExtracted() {
        String content = "---\n"
                + "title: Lecture\n"
                + "tags:\n"
                + "  - exam\n"
                + "  - 'oop'\n"
                + "---\n"
                + "# Body\n";
        MarkdownNote note = new MarkdownParser().parse(Paths.get("n.md"), content);
        TestSupport.assertTrue(note.hasTag("exam"), "block-style exam tag");
        TestSupport.assertTrue(note.hasTag("oop"), "block-style quoted oop tag");
    }

    private static void frontmatterDoesNotPolluteHeadingsOrShiftTodoLineNumbers() {
        String content = "---\n"
                + "tags: [exam]\n"
                + "---\n"
                + "# Real Title\n"
                + "- [ ] do it\n";
        MarkdownNote note = new MarkdownParser().parse(Paths.get("n.md"), content);
        TestSupport.assertEquals("Real Title", note.title());
        TestSupport.assertEquals(1, note.headings().size());
        TestSupport.assertEquals(1, note.todos().size());
        TestSupport.assertEquals(5, note.todos().get(0).lineNumber());
    }

    private static void frontmatterAndInlineTagsBothLand() {
        String content = "---\n"
                + "tags: [exam]\n"
                + "---\n"
                + "Body with #summarize tag\n";
        MarkdownNote note = new MarkdownParser().parse(Paths.get("n.md"), content);
        TestSupport.assertTrue(note.hasTag("exam"), "frontmatter exam");
        TestSupport.assertTrue(note.hasTag("summarize"), "inline summarize");
    }

    private static void unclosedFrontmatterIsTreatedAsBody() {
        String content = "---\n"
                + "tags: [exam]\n"
                + "# Title\n";
        MarkdownNote note = new MarkdownParser().parse(Paths.get("n.md"), content);
        TestSupport.assertEquals(false, note.hasTag("exam"));
        TestSupport.assertEquals(1, note.headings().size());
    }
}

