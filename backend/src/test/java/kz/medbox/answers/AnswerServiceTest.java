package kz.medbox.answers;

import kz.medbox.core.Catalogue;
import kz.medbox.core.Model.*;
import kz.medbox.core.Retriever;
import org.junit.jupiter.api.Test;
import java.util.List;
import static kz.medbox.answers.AnswerModel.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerServiceTest {
    // Synthetic policy fixtures; they are not medicine instructions or production data.
    private static final String BODY = "TEST ONLY: storage evidence A. Preserve this qualification.";

    private Catalogue catalogue(boolean sample, String passageId) {
        Variant variant = new Variant("test", "Test", "test", "1 mg", "test", "Factory", "TEST", null, sample);
        Leaflet leaflet = new Leaflet("test", "ru", "https://example.org/test-leaflet", "2026-11-16",
                List.of(new Section(passageId, "storage", "Условия хранения", BODY)));
        return new Catalogue(List.of(variant), List.of(leaflet));
    }

    private AnswerService service(AnswerGateway gateway) {
        Catalogue catalogue = catalogue(false, "test:ru:storage:0");
        return new AnswerService(catalogue, new Retriever(catalogue), gateway);
    }

    private Selection selection(String id, String text) {
        return new Selection(Status.ANSWERED, List.of(new SelectedQuote(id, text)));
    }

    @Test
    void returnsOnlyVerifiedSourceText() throws Exception {
        Answer result = service((q, p) -> selection(p.getFirst().passageId(), BODY))
                .ask("test", new Question("Условия хранения?", "ru"));
        assertEquals(Status.ANSWERED, result.status());
        assertEquals(BODY, result.answer());
        assertEquals("https://example.org/test-leaflet", result.quotes().getFirst().sourceUrl());
    }

    @Test
    void rejectsInventedQuotation() throws Exception {
        Answer result = service((q, p) -> selection(p.getFirst().passageId(), "Invented evidence"))
                .ask("test", new Question("Условия хранения?", "ru"));
        assertEquals(Status.INSUFFICIENT, result.status());
        assertTrue(result.quotes().isEmpty());
    }

    @Test
    void rejectsQuoteFromAnotherProductEvenWhenTextMatches() throws Exception {
        Answer result = service((q, p) -> selection("other:ru:storage:0", BODY))
                .ask("test", new Question("Условия хранения?", "ru"));
        assertEquals(Status.INSUFFICIENT, result.status());
    }

    @Test
    void personalQuestionDoesNotCallModel() throws Exception {
        Answer result = service((q, p) -> { fail("Personal question reached provider"); return null; })
                .ask("test", new Question("Можно мне принять это?", "ru"));
        assertEquals(Status.REFER_TO_PHARMACIST, result.status());
    }

    @Test
    void blankQuestionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service((q, p) -> null).ask("test", new Question(" ", "ru")));
    }

    @Test
    void sampleProductCannotBeUsedForMedicalAnswers() {
        Catalogue catalogue = catalogue(true, "s");
        var service = new AnswerService(catalogue, new Retriever(catalogue), (q, p) -> null);
        assertThrows(IllegalStateException.class,
                () -> service.ask("test", new Question("Условия хранения?", "ru")));
    }

    @Test
    void missingPassageIdFailsClosed() throws Exception {
        Catalogue catalogue = catalogue(false, null);
        var service = new AnswerService(catalogue, new Retriever(catalogue), (q, p) -> {
            fail("Evidence without IDs reached provider"); return null;
        });
        assertEquals(Status.INSUFFICIENT, service.ask("test", new Question("Условия хранения?", "ru")).status());
    }

    @Test
    void cardPreservesVerbatimSectionAndPassageId() {
        Card card = service((q, p) -> null).card("test", "ru");
        assertEquals(BODY, card.lines().getFirst().text());
        assertEquals("test:ru:storage:0", card.lines().getFirst().passageId());
    }

    @Test
    void providerAbstentionIsPreserved() throws Exception {
        Answer result = service((q, p) -> new Selection(Status.INSUFFICIENT, List.of()))
                .ask("test", new Question("Условия хранения?", "ru"));
        assertEquals(Status.INSUFFICIENT, result.status());
    }
}
