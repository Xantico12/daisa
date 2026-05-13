package daisa.orchestration;

import daisa.ai.AiClient;
import daisa.ai.AiEngineType;
import daisa.ai.AiRequest;
import daisa.ai.AiResponse;
import daisa.study.StudyTask;
import daisa.study.StudyTaskType;

import java.util.Objects;

public final class AiRouter {
    private static final int CLOUD_REASONING_THRESHOLD = 2_000;

    private final AiClient localClient;
    private final AiClient cloudClient;

    public AiRouter(AiClient localClient, AiClient cloudClient) {
        this.localClient = Objects.requireNonNull(localClient, "localClient");
        this.cloudClient = Objects.requireNonNull(cloudClient, "cloudClient");
    }

    public RoutingDecision decide(StudyTask task) {
        Objects.requireNonNull(task, "task");
        if (task.privacySensitive()) {
            return new RoutingDecision(AiEngineType.LOCAL, "privacy-sensitive task");
        }
        if (task.type() == StudyTaskType.EXTRACT_TODOS) {
            return new RoutingDecision(AiEngineType.LOCAL, "deterministic/lightweight markdown task");
        }
        if (task.prompt().length() > CLOUD_REASONING_THRESHOLD || task.type() == StudyTaskType.EXPLAIN_TOPIC) {
            return new RoutingDecision(AiEngineType.CLOUD, "heavy reasoning or long-context task");
        }
        return new RoutingDecision(AiEngineType.LOCAL, "short study task");
    }

    public AiResponse complete(StudyTask task) {
        RoutingDecision decision = decide(task);
        System.out.println("AI route: " + decision.engineType() + " for " + task.type() + " (" + decision.reason() + ")");
        AiRequest request = new AiRequest(task.type(), task.prompt(), task.privacySensitive());
        return decision.engineType() == AiEngineType.LOCAL
                ? localClient.complete(request)
                : cloudClient.complete(request);
    }
}

