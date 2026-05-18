package daisa.ai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

// Real local AI client backed by Ollama's /api/generate endpoint.
// Uses java.net.http.HttpClient (stdlib, no deps) and a small hand-rolled
// JSON encoder/decoder scoped to Ollama's known schema.
//
// Failure mode: this client never throws back into the orchestrator. If
// Ollama is unreachable, slow, or returns a bad response, we return a
// diagnostic AiResponse so the surrounding pipeline can still write a
// summary artifact carrying the error text instead of crashing.
public final class OllamaClient implements AiClient {
    private final OllamaConfig config;
    private final HttpClient httpClient;

    public OllamaClient(OllamaConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    OllamaClient(OllamaConfig config, HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public AiResponse complete(AiRequest request) {
        Objects.requireNonNull(request, "request");
        String body = buildRequestBody(config.model(), request.prompt());
        HttpRequest httpRequest = HttpRequest.newBuilder(config.generateUrl())
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return unavailable("HTTP " + response.statusCode());
            }
            String text = extractResponseField(response.body());
            if (text == null) {
                return unavailable("malformed response");
            }
            return new AiResponse(AiEngineType.LOCAL, text);
        } catch (HttpTimeoutException timeout) {
            return unavailable("timeout");
        } catch (ConnectException refused) {
            return unavailable("connection refused");
        } catch (IOException io) {
            return unavailable("io error: " + io.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        }
    }

    private static AiResponse unavailable(String reason) {
        System.err.println("Ollama unavailable: " + reason);
        return new AiResponse(AiEngineType.LOCAL, "[Ollama unavailable: " + reason + "]");
    }

    // Build the request JSON for /api/generate. Only three fields, all flat.
    // Keeping it as a string template (with one escaped field) is clearer
    // than wrapping a single-call helper around a fake JSON object.
    static String buildRequestBody(String model, String prompt) {
        // think:false skips visible chain-of-thought on thinking-capable models
        // (e.g. qwen3.5) so summaries arrive in seconds instead of minutes.
        // Non-thinking models silently ignore the field.
        return "{\"model\":\"" + escapeJsonString(model)
                + "\",\"prompt\":\"" + escapeJsonString(prompt)
                + "\",\"stream\":false,\"think\":false}";
    }

    // Escape a Java string into the contents of a JSON string literal.
    // Spec: RFC 8259 section 7. We don't emit unicode escapes for high code points; UTF-8
    // in the request body carries them faithfully — only for control chars
    // below 0x20 that lack a shorthand escape.
    static String escapeJsonString(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    // Pull the top-level "response" field out of Ollama's JSON body.
    // Walks the body looking for `"response"` preceded by `{` or `,` (to
    // avoid matches inside string values), then parses the string that
    // follows. Returns null on any structural problem.
    static String extractResponseField(String body) {
        if (body == null) return null;
        int searchFrom = 0;
        final String key = "\"response\"";
        while (true) {
            int keyAt = body.indexOf(key, searchFrom);
            if (keyAt < 0) return null;

            int prev = keyAt - 1;
            while (prev >= 0 && Character.isWhitespace(body.charAt(prev))) prev--;
            boolean isJsonKey = prev >= 0 && (body.charAt(prev) == '{' || body.charAt(prev) == ',');
            if (isJsonKey) {
                int after = keyAt + key.length();
                while (after < body.length() && Character.isWhitespace(body.charAt(after))) after++;
                if (after < body.length() && body.charAt(after) == ':') {
                    return readJsonString(body, after + 1);
                }
            }
            searchFrom = keyAt + 1;
        }
    }

    // Read a JSON string starting at or after `from`. Decodes the standard
    // escape sequences. Returns null if the value isn't actually a string
    // or if the input ends mid-string.
    private static String readJsonString(String body, int from) {
        int i = from;
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) i++;
        if (i >= body.length() || body.charAt(i) != '"') return null;
        i++;
        StringBuilder out = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') return out.toString();
            if (c == '\\') {
                if (i + 1 >= body.length()) return null;
                char esc = body.charAt(i + 1);
                switch (esc) {
                    case '"':  out.append('"');  i += 2; break;
                    case '\\': out.append('\\'); i += 2; break;
                    case '/':  out.append('/');  i += 2; break;
                    case 'b':  out.append('\b'); i += 2; break;
                    case 'f':  out.append('\f'); i += 2; break;
                    case 'n':  out.append('\n'); i += 2; break;
                    case 'r':  out.append('\r'); i += 2; break;
                    case 't':  out.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 6 > body.length()) return null;
                        try {
                            int code = Integer.parseInt(body.substring(i + 2, i + 6), 16);
                            out.append((char) code);
                        } catch (NumberFormatException bad) {
                            return null;
                        }
                        i += 6;
                        break;
                    default:
                        return null;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return null;
    }
}
