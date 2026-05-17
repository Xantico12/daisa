package daisa.orchestration;

import daisa.ai.AiClient;
import daisa.ai.AiEngineType;
import daisa.ai.AiRequest;
import daisa.ai.AiResponse;

import java.util.ArrayList;
import java.util.List;

// Test double: returns a canned response and records every request it sees.
// Lets orchestrator/router tests assert which engine was consulted and what
// payload reached the AI client, without running real network calls.
final class RecordingAiClient implements AiClient {
    private final AiEngineType engineType;
    private final String cannedText;
    private final List<AiRequest> requests = new ArrayList<>();

    RecordingAiClient(AiEngineType engineType, String cannedText) {
        this.engineType = engineType;
        this.cannedText = cannedText;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        requests.add(request);
        return new AiResponse(engineType, cannedText);
    }

    List<AiRequest> requests() {
        return requests;
    }

    int callCount() {
        return requests.size();
    }
}
