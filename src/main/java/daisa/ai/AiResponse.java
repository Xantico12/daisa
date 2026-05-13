package daisa.ai;

import java.util.Objects;

public final class AiResponse {
    private final AiEngineType engineType;
    private final String text;

    public AiResponse(AiEngineType engineType, String text) {
        this.engineType = Objects.requireNonNull(engineType, "engineType");
        this.text = Objects.requireNonNull(text, "text");
    }

    public AiEngineType engineType() {
        return engineType;
    }

    public String text() {
        return text;
    }
}

