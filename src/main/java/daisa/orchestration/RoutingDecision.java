package daisa.orchestration;

import daisa.ai.AiEngineType;

import java.util.Objects;

public final class RoutingDecision {
    private final AiEngineType engineType;
    private final String reason;

    public RoutingDecision(AiEngineType engineType, String reason) {
        this.engineType = Objects.requireNonNull(engineType, "engineType");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public AiEngineType engineType() {
        return engineType;
    }

    public String reason() {
        return reason;
    }
}

