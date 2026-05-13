package daisa;

import daisa.agent.AgentSupervisor;
import daisa.ai.MockCloudAiClient;
import daisa.ai.MockLocalAiClient;
import daisa.orchestration.AiRouter;
import daisa.orchestration.TaskOrchestrator;
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
        AiRouter router = new AiRouter(new MockLocalAiClient(), new MockCloudAiClient());
        StudyArtifactWriter writer = new StudyArtifactWriter(vaultPath);
        TaskOrchestrator orchestrator = new TaskOrchestrator(supervisor, router, writer);

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

