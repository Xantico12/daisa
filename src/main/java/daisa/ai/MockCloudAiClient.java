package daisa.ai;

public final class MockCloudAiClient implements AiClient {
    @Override
    public AiResponse complete(AiRequest request) {
        return new AiResponse(AiEngineType.CLOUD, "Cloud draft for " + request.taskType() + ": " + firstLine(request.prompt()));
    }

    private static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline) : value;
    }
}

