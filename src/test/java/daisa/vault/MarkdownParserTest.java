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
    }
}

