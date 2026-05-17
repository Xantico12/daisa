package daisa.ai;

import daisa.TestSupport;
import daisa.study.StudyTaskType;

import java.net.URI;
import java.time.Duration;

public final class OllamaClientTest {
    private OllamaClientTest() {
    }

    public static void run() throws Exception {
        runEscapeBasic();
        runEscapeControlCharacters();
        runBuildRequestBodyEmbedsModelAndPrompt();
        runExtractResponseFieldHappyPath();
        runExtractResponseFieldWithEscapes();
        runExtractResponseFieldFieldNotFirst();
        runExtractResponseFieldIgnoresKeyInsideValue();
        runExtractResponseFieldReturnsNullWhenMissing();
        runConfigGenerateUrlNormalizesTrailingSlash();
        runClientReturnsGracefulDiagnosticOnConnectionRefused();
    }

    private static void runEscapeBasic() {
        TestSupport.assertEquals("hello", OllamaClient.escapeJsonString("hello"));
        TestSupport.assertEquals("a\\\"b", OllamaClient.escapeJsonString("a\"b"));
        TestSupport.assertEquals("a\\\\b", OllamaClient.escapeJsonString("a\\b"));
    }

    private static void runEscapeControlCharacters() {
        TestSupport.assertEquals("line1\\nline2", OllamaClient.escapeJsonString("line1\nline2"));
        TestSupport.assertEquals("col1\\tcol2", OllamaClient.escapeJsonString("col1\tcol2"));
        TestSupport.assertEquals("crlf\\r\\n", OllamaClient.escapeJsonString("crlf\r\n"));
        TestSupport.assertEquals("\\u0001", OllamaClient.escapeJsonString(""));
        // Non-ASCII pass-through — UTF-8 in the body handles it.
        TestSupport.assertEquals("Aarhus æøå", OllamaClient.escapeJsonString("Aarhus æøå"));
    }

    private static void runBuildRequestBodyEmbedsModelAndPrompt() {
        String body = OllamaClient.buildRequestBody("llama3.2", "Tell me about \"defer\"");
        TestSupport.assertTrue(body.contains("\"model\":\"llama3.2\""), "model field present: " + body);
        TestSupport.assertTrue(body.contains("\"prompt\":\"Tell me about \\\"defer\\\"\""),
                "prompt field escaped correctly: " + body);
        TestSupport.assertTrue(body.contains("\"stream\":false"), "stream=false present: " + body);
    }

    private static void runExtractResponseFieldHappyPath() {
        String body = "{\"model\":\"llama3.2\",\"response\":\"hello world\",\"done\":true}";
        TestSupport.assertEquals("hello world", OllamaClient.extractResponseField(body));
    }

    private static void runExtractResponseFieldWithEscapes() {
        // Quote-in-value, backslash, newline, tab, unicode escape
        String body = "{\"response\":\"line1\\nline2 \\\"q\\\" path\\\\foo \\u00e6\"}";
        TestSupport.assertEquals("line1\nline2 \"q\" path\\foo æ", OllamaClient.extractResponseField(body));
    }

    private static void runExtractResponseFieldFieldNotFirst() {
        String body = "{ \"model\": \"llama3.2\", \"created_at\": \"2026-05-16\", \"response\": \"ok\", \"done\": true }";
        TestSupport.assertEquals("ok", OllamaClient.extractResponseField(body));
    }

    private static void runExtractResponseFieldIgnoresKeyInsideValue() {
        // If some prior field's value happens to contain the literal characters
        // "response", the extractor must skip it and find the real key.
        String body = "{\"model\":\"misleading-\\\"response\\\"-model\",\"response\":\"real\"}";
        TestSupport.assertEquals("real", OllamaClient.extractResponseField(body));
    }

    private static void runExtractResponseFieldReturnsNullWhenMissing() {
        String body = "{\"model\":\"llama3.2\",\"done\":true}";
        TestSupport.assertTrue(OllamaClient.extractResponseField(body) == null,
                "Missing field should yield null");
    }

    private static void runConfigGenerateUrlNormalizesTrailingSlash() {
        OllamaConfig a = new OllamaConfig(URI.create("http://localhost:11434"), "llama3.2", Duration.ofSeconds(10));
        OllamaConfig b = new OllamaConfig(URI.create("http://localhost:11434/"), "llama3.2", Duration.ofSeconds(10));
        TestSupport.assertEquals(URI.create("http://localhost:11434/api/generate"), a.generateUrl());
        TestSupport.assertEquals(URI.create("http://localhost:11434/api/generate"), b.generateUrl());
    }

    // End-to-end-ish: point the client at a port nothing listens on. We
    // expect the graceful diagnostic AiResponse, not an exception. Confirms
    // the orchestrator can safely keep running when Ollama is down.
    private static void runClientReturnsGracefulDiagnosticOnConnectionRefused() {
        OllamaConfig config = new OllamaConfig(
                URI.create("http://127.0.0.1:1"),  // port 1 — never listens
                "llama3.2",
                Duration.ofMillis(500));
        OllamaClient client = new OllamaClient(config);
        AiResponse response = client.complete(new AiRequest(StudyTaskType.SUMMARIZE_NOTE, "anything", false));
        TestSupport.assertEquals(AiEngineType.LOCAL, response.engineType());
        TestSupport.assertTrue(response.text().startsWith("[Ollama unavailable:"),
                "Expected graceful diagnostic, got: " + response.text());
    }
}
