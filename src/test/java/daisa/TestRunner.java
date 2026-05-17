package daisa;

import daisa.ai.OllamaClientTest;
import daisa.orchestration.AiRouterTest;
import daisa.orchestration.TaskOrchestratorTest;
import daisa.vault.CourseResolverTest;
import daisa.vault.MarkdownParserTest;
import daisa.vault.StudyArtifactWriterTest;
import daisa.vault.VaultWatcherTest;

public final class TestRunner {
    private TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        MarkdownParserTest.run();
        AiRouterTest.run();
        CourseResolverTest.run();
        TaskOrchestratorTest.run();
        StudyArtifactWriterTest.run();
        VaultWatcherTest.run();
        OllamaClientTest.run();
        System.out.println("All DAISA tests passed.");
    }
}
