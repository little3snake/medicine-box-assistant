package kz.medbox.core;

import java.util.List;
import java.util.Map;

/** Plain data records used by the core. Jackson can (de)serialise records directly. */
public final class Model {

    private Model() {}

    /** One product variant = one trade name + strength + form + manufacturer = one leaflet. */
    public record Variant(
            String id,
            String tradeName,
            String inn,
            String strength,
            String form,
            String manufacturer,
            String registrationNumber,
            String lookAlikeGroup,
            boolean sample) {}

    /** One leaflet section; the unit of retrieval ("passage"). */
    public record Section(String passageId, String type, String title, String text) {}

    /** A leaflet in one language. */
    public record Leaflet(String variantId, String lang, String sourceUrl, String retrievedAt, List<Section> sections) {}

    /** Fields the vision model read from the package photo (any may be null). */
    public record ExtractedFields(String name, String strength, String form, String manufacturer) {}

    public record ScoreBreakdown(double name, double strength, double form, double manufacturer) {}

    public record Candidate(String variantId, String label, double score, ScoreBreakdown breakdown) {}

    public record MatchResult(
            ExtractedFields extracted,
            Map<String, String> normalized,
            List<Candidate> candidates,
            boolean needsClarification,
            List<String> reasons) {}

    public record Passage(
            String passageId, String variantId, String lang, String sectionType,
            String title, String text, String sourceUrl, double score) {}

    public record RetrievalResult(String variantId, String requestedLang, String usedLang,
                                  boolean langFallback, List<Passage> passages) {}
}
