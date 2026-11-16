package kz.medbox.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.medbox.core.Model.ExtractedFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Parses canned Gemini responses; no network call. */
class GeminiParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String wrap(String text) throws Exception {
        String quoted = new ObjectMapper().writeValueAsString(text); // JSON string literal with escapes
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quoted + "}]}}]}";
    }

    @Test
    void parsesJsonFields() throws Exception {
        ExtractedFields f = GeminiVisionClient.parse(wrap(
                "{\"name\":\"Парацетамол\",\"strength\":\"500 мг\",\"form\":\"таблетки\",\"manufacturer\":null}"), mapper);
        assertEquals("Парацетамол", f.name());
        assertEquals("500 мг", f.strength());
        assertNull(f.manufacturer());
    }

    @Test
    void stripsMarkdownFence() throws Exception {
        ExtractedFields f = GeminiVisionClient.parse(wrap("```json\n{\"name\":\"X\",\"strength\":\"\"}\n```"), mapper);
        assertEquals("X", f.name());
        assertNull(f.strength(), "blank becomes null");
    }

    @Test
    void emptyResponseGivesEmptyFields() throws Exception {
        ExtractedFields f = GeminiVisionClient.parse("{\"candidates\":[]}", mapper);
        assertNull(f.name());
    }

    @Test
    void invalidJsonMeansNothingWasRead() throws Exception {
        ExtractedFields f = GeminiVisionClient.parse(wrap("I cannot read this package"), mapper);
        assertNull(f.name());
        assertNull(f.strength());
    }

    @Test
    void literalNullStringBecomesNull() throws Exception {
        ExtractedFields f = GeminiVisionClient.parse(wrap("{\"name\":\"X\",\"strength\":\"null\"}"), mapper);
        assertNull(f.strength());
    }
}
