package daisa;

import daisa.agent.AgentSupervisor;
import daisa.ai.MockCloudAiClient;
import daisa.ai.OllamaClient;
import daisa.ai.OllamaConfig;
import daisa.orchestration.AiRouter;
import daisa.orchestration.TaskOrchestrator;
import daisa.vault.CourseResolver;
import daisa.vault.StudyArtifactWriter;
import daisa.vault.VaultWatcher;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ./scripts/run.sh /path/to/obsidian-vault");
            System.exit(2);
        }

        Path vaultPath = Paths.get(args[0]).toAbsolutePath().normalize();
        AgentSupervisor supervisor = AgentSupervisor.withDefaultAgents();
        OllamaConfig ollamaConfig = OllamaConfig.fromEnv();
        System.out.println("Local AI: Ollama at " + ollamaConfig.generateUrl() + " (model: " + ollamaConfig.model() + ")");
        AiRouter router = new AiRouter(new OllamaClient(ollamaConfig), new MockCloudAiClient());
        StudyArtifactWriter writer = new StudyArtifactWriter(vaultPath);
        CourseResolver courseResolver = CourseResolver.fromEnv(vaultPath);
        System.out.println("Scope root: " + courseResolver.scopeRoot());
        TaskOrchestrator orchestrator = new TaskOrchestrator(supervisor, router, writer, courseResolver);

        supervisor.start();
        Runtime.getRuntime().addShutdownHook(new Thread(supervisor::stop, "daisa-shutdown"));

        System.out.println("DAISA watching vault: " + vaultPath);
        try (VaultWatcher watcher = new VaultWatcher(vaultPath, orchestrator::onMarkdownChanged)) {
            watcher.watchForever();
        } finally {
            supervisor.stop();
        }
    }
}
