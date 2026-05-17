package daisa.ai;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

// Immutable configuration for OllamaClient. Reads from environment variables
// when constructed via fromEnv(), so swapping models or hosts doesn't require
// a code change.
public final class OllamaConfig {
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3.2";
    public static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final URI baseUrl;
    private final String model;
    private final Duration timeout;

    public OllamaConfig(URI baseUrl, String model, Duration timeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (model.isEmpty()) {
            throw new IllegalArgumentException("model must not be empty");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static OllamaConfig fromEnv() {
        String baseUrl = envOrDefault("DAISA_OLLAMA_URL", DEFAULT_BASE_URL);
        String model = envOrDefault("DAISA_OLLAMA_MODEL", DEFAULT_MODEL);
        int timeoutSeconds = parsePositiveInt(
                envOrDefault("DAISA_OLLAMA_TIMEOUT_SECONDS", String.valueOf(DEFAULT_TIMEOUT_SECONDS)),
                DEFAULT_TIMEOUT_SECONDS);
        return new OllamaConfig(URI.create(baseUrl), model, Duration.ofSeconds(timeoutSeconds));
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public URI generateUrl() {
        String base = baseUrl.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/api/generate");
    }

    public String model() {
        return model;
    }

    public Duration timeout() {
        return timeout;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
