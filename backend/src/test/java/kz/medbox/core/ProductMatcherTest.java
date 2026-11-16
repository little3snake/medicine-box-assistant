package kz.medbox.core;

import kz.medbox.core.Model.ExtractedFields;
import kz.medbox.core.Model.MatchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductMatcherTest {

    private final ProductMatcher matcher = new ProductMatcher(TestData.catalogue());

    private MatchResult m(String name, String strength, String form, String manufacturer) {
        return matcher.match(new ExtractedFields(name, strength, form, manufacturer));
    }

    @Test
    void exactFieldsGiveConfidentMatch() {
        MatchResult r = m("Парацетамол-Образец", "500 мг", "таблетки", "Образец Фарм");
        assertEquals("para-500", r.candidates().get(0).variantId());
        assertFalse(r.needsClarification(), r.reasons().toString());
    }

    @Test
    void lookAlikeByStrengthIsSeparated() {
        MatchResult r = m("Парацетамол-Образец", "200 мг", "таблетки", "Образец Фарм");
        assertEquals("para-200", r.candidates().get(0).variantId());
        assertFalse(r.needsClarification());
    }

    @Test
    void missingStrengthAsksUser() {
        MatchResult r = m("Парацетамол-Образец", null, "таблетки", "Образец Фарм");
        assertTrue(r.needsClarification());
        assertTrue(r.reasons().contains("STRENGTH_NOT_READ"));
        assertEquals(2, r.candidates().size(), "both strengths should be offered");
    }

    @Test
    void lookAlikeByFormIsSeparated() {
        MatchResult r = m("Ибупрофен-Образец", "100 мг/5 мл", "суспензия", "Образец Фарм");
        assertEquals("ibu-susp", r.candidates().get(0).variantId());
        assertFalse(r.needsClarification());
    }

    @Test
    void ocrTypoStillMatches() {
        MatchResult r = m("Парацетамол-0бразец", "500мг", "таблетки", "Образец Фарм");
        assertEquals("para-500", r.candidates().get(0).variantId());
        assertFalse(r.needsClarification());
    }

    @Test
    void manufacturerOnlyDifferenceIsRankedButConfirmed() {
        // Same name, strength and form; only the manufacturer differs (weight 0.10).
        // The right product is ranked first, but the margin is small, so we ask the user. Safe by design.
        MatchResult r = m("Ибупрофен-Образец", "200 мг", "таблетки", "Другой Завод");
        assertEquals("ibu-200-tab-other", r.candidates().get(0).variantId());
        assertTrue(r.needsClarification());
        assertTrue(r.reasons().contains("AMBIGUOUS"));
    }

    @Test
    void strengthMismatchIsNeverConfident() {
        MatchResult r = m("Парацетамол-Образец", "1000 мг", "таблетки", "Образец Фарм");
        assertTrue(r.needsClarification());
        assertTrue(r.reasons().contains("STRENGTH_MISMATCH"));
    }

    @Test
    void outOfCatalogueProductHasNoCandidates() {
        MatchResult r = m("Аспирин Кардио", "100 мг", "таблетки", "Байер");
        assertTrue(r.candidates().isEmpty());
        assertTrue(r.needsClarification());
        assertTrue(r.reasons().contains("NO_CANDIDATES"));
    }

    @Test
    void emptyPhotoReadingHasNoCandidates() {
        MatchResult r = m(null, null, null, null);
        assertTrue(r.needsClarification());
        assertTrue(r.candidates().isEmpty());
    }
}
