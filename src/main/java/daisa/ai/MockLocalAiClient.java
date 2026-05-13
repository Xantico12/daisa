package daisa.ai;

public final class MockLocalAiClient implements AiClient {
    @Override
    public AiResponse complete(AiRequest request) {
        return new AiResponse(AiEngineType.LOCAL, "Local draft for " + request.taskType() + ": " + firstLine(request.prompt()));
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline) : value;
    }
}

