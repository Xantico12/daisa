package daisa;

import daisa.orchestration.AiRouterTest;
import daisa.orchestration.TaskOrchestratorTest;
import daisa.vault.MarkdownParserTest;
import daisa.vault.StudyArtifactWriterTest;

public final class TestRunner {
    private TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        MarkdownParserTest.run();
        AiRouterTest.run();
        TaskOrchestratorTest.run();
        StudyArtifactWriterTest.run();
        System.out.println("All DAISA tests passed.");
    }
}
