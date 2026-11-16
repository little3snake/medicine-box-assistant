package kz.medbox.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.medbox.api.ErrorHandler;
import kz.medbox.core.Model.ExtractedFields;
import kz.medbox.vision.GeminiVisionClient.Reply;
import kz.medbox.vision.GeminiVisionClient.VisionUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Retry, schema-fallback, error-logging and redaction decisions of GeminiVisionClient. No network, no real sleeping. */
class GeminiRetryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // Fake keys are built at runtime so no key-shaped literal is in the source (tools/check_secrets.py).
    private static final String AQ_KEY = "AQ." + "Ab1_-".repeat(6);
    private static final String AIZA_KEY = "AIza" + "B".repeat(35);
    private static final String PLAIN_KEY = "test-literal-key-123";

    @TempDir
    Path logDir;

    // ---------- canned Gemini bodies ----------

    private static String ok() throws Exception {
        String fields = "{\"name\":\"Анальгин\",\"strength\":\"500 мг\",\"form\":\"таблетки\",\"manufacturer\":null}";
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + new ObjectMapper().writeValueAsString(fields)
                + "}]}}],\"usageMetadata\":{\"totalTokenCount\":42}}";
    }

    private static String error(int code, String status, String message, String reason) throws Exception {
        String msg = new ObjectMapper().writeValueAsString(message);
        String details = reason == null ? "[]"
                : "[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"" + reason + "\"}]";
        return "{\"error\":{\"code\":" + code + ",\"message\":" + msg + ",\"status\":\"" + status
                + "\",\"details\":" + details + "}}";
    }

    private static final String SCHEMA_MSG =
            "Invalid JSON payload received. Unknown name \"responseSchema\" at 'generation_config': Cannot find field.";

    private static String schemaError() throws Exception {
        return error(400, "INVALID_ARGUMENT", SCHEMA_MSG, null);
    }

    private static String keyError() throws Exception {
        return error(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.", "API_KEY_INVALID");
    }

    // ---------- fake sender ----------

    /** Returns queued replies (or throws queued exceptions) and records each call's withSchema flag. */
    private static final class FakeSender implements GeminiVisionClient.Sender {
        final Deque<Object> script = new ArrayDeque<>();
        final List<Boolean> withSchema = new ArrayList<>();

        FakeSender then(int status, String body) { script.add(new Reply(status, body)); return this; }
        FakeSender thenThrow(Exception e) { script.add(e); return this; }

        @Override
        public Reply send(byte[] image, String mime, boolean schema) throws IOException, InterruptedException {
            withSchema.add(schema);
            Object next = script.poll();
            if (next == null) throw new AssertionError("unexpected extra call");
            if (next instanceof IOException e) throw e;
            if (next instanceof InterruptedException e) throw e;
            return (Reply) next;
        }
    }

    private final List<Duration> sleeps = new ArrayList<>();

    private GeminiVisionClient client(FakeSender sender, String key) {
        return new GeminiVisionClient(key, "test-model", "http://unused", 150, mapper,
                new VisionLog(logDir.toString(), mapper), sender, sleeps::add);
    }

    private GeminiVisionClient client(FakeSender sender) {
        return client(sender, PLAIN_KEY);
    }

    private String logText() throws IOException {
        return Files.readString(logDir.resolve("vision.jsonl"));
    }

    private JsonNode lastLog() throws IOException {
        List<String> lines = Files.readAllLines(logDir.resolve("vision.jsonl"));
        assertEquals(1, lines.size(), "one log line per extract() call");
        return mapper.readTree(lines.get(0));
    }

    private static final byte[] IMG = {1, 2, 3};

    // ---------- pure decisions ----------

    @Test
    void onlyOverloadAndRateLimitAreRetryable() {
        assertTrue(GeminiVisionClient.isRetryable(503));
        assertTrue(GeminiVisionClient.isRetryable(429));
        for (int s : new int[]{200, 400, 401, 403, 404, 500, 504}) {
            assertFalse(GeminiVisionClient.isRetryable(s), "status " + s);
        }
    }

    @Test
    void schemaRejectionDetectedOnlyForSchema400() throws Exception {
        assertTrue(GeminiVisionClient.isSchemaRejection(400, schemaError(), mapper));
        assertTrue(GeminiVisionClient.isSchemaRejection(400, error(400, "INVALID_ARGUMENT",
                "* GenerateContentRequest.generation_config.response_schema.properties[name].type: must be specified",
                null), mapper));

        assertFalse(GeminiVisionClient.isSchemaRejection(400, keyError(), mapper), "API_KEY_INVALID");
        assertFalse(GeminiVisionClient.isSchemaRejection(400, error(400, "INVALID_ARGUMENT",
                "Unable to process input image.", null), mapper), "other 400");
        assertFalse(GeminiVisionClient.isSchemaRejection(503, schemaError(), mapper), "not a 400");
        assertFalse(GeminiVisionClient.isSchemaRejection(400, "<html>Bad Request</html>", mapper), "not JSON");
        assertFalse(GeminiVisionClient.isSchemaRejection(400, null, mapper));
    }

    @Test
    void redactsLiteralKeyAndBothGoogleKeyFormats() {
        String s = GeminiVisionClient.redact(
                "a " + PLAIN_KEY + " b " + AIZA_KEY + " c " + AQ_KEY + " d", PLAIN_KEY);
        assertFalse(s.contains(PLAIN_KEY));
        assertFalse(s.contains(AIZA_KEY));
        assertFalse(s.contains(AQ_KEY));
        assertFalse(s.contains("AQ.Ab1"), "no fragment of the AQ. key left");
        assertEquals("a [redacted] b [redacted] c [redacted] d", s);
    }

    @Test
    void redactsAqKeyEvenWhenItIsNotTheConfiguredKey() {
        String s = GeminiVisionClient.redact("key=" + AQ_KEY, "some-other-key");
        assertEquals("key=[redacted]", s);
    }

    @Test
    void errorMessageIsCappedInLength() {
        String s = GeminiVisionClient.errorMessage("x".repeat(5000), PLAIN_KEY, mapper);
        assertTrue(s.length() <= 501, "capped at 500 chars + ellipsis");
    }

    // ---------- retry ----------

    @Test
    void retries503TwiceWithBackoffThenSucceeds() throws Exception {
        FakeSender s = new FakeSender().then(503, error(503, "UNAVAILABLE", "The model is overloaded.", null))
                .then(503, error(503, "UNAVAILABLE", "The model is overloaded.", null))
                .then(200, ok());
        ExtractedFields f = client(s).extract(IMG, "image/jpeg");

        assertEquals("Анальгин", f.name());
        assertEquals(3, s.withSchema.size());
        assertEquals(List.of(Duration.ofSeconds(2), Duration.ofSeconds(5)), sleeps);
        JsonNode log = lastLog();
        assertEquals(200, log.get("status").asInt());
        assertEquals(3, log.get("attempts").asInt());
        assertFalse(log.has("error"));
    }

    @Test
    void retries429ThenSucceeds() throws Exception {
        FakeSender s = new FakeSender().then(429, error(429, "RESOURCE_EXHAUSTED", "Quota exceeded.", null))
                .then(200, ok());
        client(s).extract(IMG, "image/jpeg");
        assertEquals(2, s.withSchema.size());
        assertEquals(List.of(Duration.ofSeconds(2)), sleeps);
    }

    @Test
    void gives503AfterThreeFailedAttemptsAndLogsGeminiMessage() throws Exception {
        String body = error(503, "UNAVAILABLE", "The model is overloaded. Please try again later.", null);
        FakeSender s = new FakeSender().then(503, body).then(503, body).then(503, body);

        VisionUnavailableException e = assertThrows(VisionUnavailableException.class,
                () -> client(s).extract(IMG, "image/jpeg"));
        assertTrue(e.getMessage().contains("HTTP 503 after 3 attempts"), e.getMessage());
        assertEquals(3, s.withSchema.size(), "no fourth attempt");
        assertEquals(2, sleeps.size());

        JsonNode log = lastLog();
        assertEquals(503, log.get("status").asInt());
        assertEquals(3, log.get("attempts").asInt());
        assertEquals("http", log.get("errorKind").asText());
        assertEquals("UNAVAILABLE: The model is overloaded. Please try again later.", log.get("error").asText());
    }

    @Test
    void doesNotRetryServerError500() throws Exception {
        FakeSender s = new FakeSender().then(500, error(500, "INTERNAL", "Internal error.", null));
        assertThrows(VisionUnavailableException.class, () -> client(s).extract(IMG, "image/jpeg"));
        assertEquals(1, s.withSchema.size());
        assertTrue(sleeps.isEmpty());
    }

    // ---------- schema fallback ----------

    @Test
    void schema400FallsBackToPlainJsonMode() throws Exception {
        FakeSender s = new FakeSender().then(400, schemaError()).then(200, ok());
        ExtractedFields f = client(s).extract(IMG, "image/jpeg");

        assertEquals("500 мг", f.strength());
        assertEquals(List.of(true, false), s.withSchema, "second call is without responseSchema");
        JsonNode log = lastLog();
        assertFalse(log.get("schemaUsed").asBoolean());
        assertEquals(2, log.get("attempts").asInt());
    }

    @Test
    void schemaFallbackIsAlsoRetriedOn503() throws Exception {
        FakeSender s = new FakeSender().then(400, schemaError())
                .then(503, error(503, "UNAVAILABLE", "overloaded", null)).then(200, ok());
        client(s).extract(IMG, "image/jpeg");
        assertEquals(List.of(true, false, false), s.withSchema);
        assertEquals(List.of(Duration.ofSeconds(2)), sleeps);
    }

    @Test
    void invalidApiKeyIsNotRetriedWithoutSchema() throws Exception {
        FakeSender s = new FakeSender().then(400, keyError());
        VisionUnavailableException e = assertThrows(VisionUnavailableException.class,
                () -> client(s).extract(IMG, "image/jpeg"));
        assertEquals("Gemini returned HTTP 400", e.getMessage());
        assertEquals(List.of(true), s.withSchema, "exactly one call, no fallback");

        JsonNode log = lastLog();
        assertTrue(log.get("schemaUsed").asBoolean());
        assertEquals("INVALID_ARGUMENT: API key not valid. Please pass a valid API key.", log.get("error").asText());
    }

    @Test
    void otherBadRequestIsNotRetriedWithoutSchema() throws Exception {
        FakeSender s = new FakeSender().then(400, error(400, "INVALID_ARGUMENT", "Unable to process input image.", null));
        assertThrows(VisionUnavailableException.class, () -> client(s).extract(IMG, "image/jpeg"));
        assertEquals(1, s.withSchema.size());
    }

    // ---------- the key never reaches the log or the client ----------

    @Test
    void echoedAqKeyIsRedactedInLogAndNotSentToClient() throws Exception {
        FakeSender s = new FakeSender().then(400, error(400, "INVALID_ARGUMENT",
                "API key not valid: " + AQ_KEY, "API_KEY_INVALID"));
        VisionUnavailableException e = assertThrows(VisionUnavailableException.class,
                () -> client(s, AQ_KEY).extract(IMG, "image/jpeg"));

        assertFalse(e.getMessage().contains(AQ_KEY));
        String text = logText();
        assertFalse(text.contains(AQ_KEY), "configured AQ. key must not be logged");
        assertFalse(text.contains("AQ.Ab1"), "no fragment of the AQ. key");
        assertTrue(text.contains("API key not valid: [redacted]"), text);
    }

    @Test
    void echoedAqKeyIsRedactedEvenIfDifferentFromConfiguredKey() throws Exception {
        FakeSender s = new FakeSender().then(400, error(400, "INVALID_ARGUMENT",
                "API key not valid: " + AQ_KEY, "API_KEY_INVALID"));
        assertThrows(VisionUnavailableException.class, () -> client(s, PLAIN_KEY).extract(IMG, "image/jpeg"));
        assertFalse(logText().contains(AQ_KEY));
    }

    @Test
    void echoedAizaAndLiteralKeysAreRedactedInLog() throws Exception {
        FakeSender s = new FakeSender().then(403, error(403, "PERMISSION_DENIED",
                "Denied for " + PLAIN_KEY + " and " + AIZA_KEY, null));
        assertThrows(VisionUnavailableException.class, () -> client(s, PLAIN_KEY).extract(IMG, "image/jpeg"));
        String text = logText();
        assertFalse(text.contains(PLAIN_KEY));
        assertFalse(text.contains(AIZA_KEY));
        assertTrue(text.contains("Denied for [redacted] and [redacted]"), text);
    }

    // ---------- timeouts and network errors ----------

    @Test
    void timeoutIsLoggedAndBecomesVisionUnavailable() throws Exception {
        FakeSender s = new FakeSender().thenThrow(new HttpTimeoutException("request timed out"));
        VisionUnavailableException e = assertThrows(VisionUnavailableException.class,
                () -> client(s).extract(IMG, "image/jpeg"));
        assertEquals("Gemini timed out after 150 s", e.getMessage());
        assertEquals(1, s.withSchema.size(), "timeouts are not retried");

        JsonNode log = lastLog();
        assertEquals(0, log.get("status").asInt());
        assertEquals("timeout", log.get("errorKind").asText());
        assertEquals(1, log.get("attempts").asInt());
    }

    @Test
    void timeoutDuringRetryKeepsAttemptCount() throws Exception {
        FakeSender s = new FakeSender().then(503, error(503, "UNAVAILABLE", "overloaded", null))
                .thenThrow(new HttpTimeoutException("request timed out"));
        assertThrows(VisionUnavailableException.class, () -> client(s).extract(IMG, "image/jpeg"));
        assertEquals(2, lastLog().get("attempts").asInt());
    }

    @Test
    void networkErrorIsLoggedAndBecomesVisionUnavailable() throws Exception {
        FakeSender s = new FakeSender().thenThrow(new ConnectException("Connection refused"));
        VisionUnavailableException e = assertThrows(VisionUnavailableException.class,
                () -> client(s).extract(IMG, "image/jpeg"));
        assertTrue(e.getMessage().contains("ConnectException"), e.getMessage());

        JsonNode log = lastLog();
        assertEquals("network", log.get("errorKind").asText());
        assertTrue(log.get("error").asText().contains("Connection refused"));
    }

    @Test
    void interruptionIsLoggedAndRestoresInterruptFlag() throws Exception {
        FakeSender s = new FakeSender().thenThrow(new InterruptedException());
        try {
            assertThrows(VisionUnavailableException.class, () -> client(s).extract(IMG, "image/jpeg"));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals("interrupted", lastLog().get("errorKind").asText());
        } finally {
            Thread.interrupted(); // clear for other tests
        }
    }

    @Test
    void visionFailuresMapToHttp503() {
        var resp = new ErrorHandler().vision(new VisionUnavailableException("Gemini timed out after 150 s"));
        assertEquals(503, resp.getStatusCode().value());
    }
}
