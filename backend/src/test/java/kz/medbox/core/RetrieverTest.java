package kz.medbox.core;

import kz.medbox.core.Model.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieverTest {

    private final Retriever retriever = new Retriever(TestData.catalogue());

    private String top(String question) {
        return retriever.retrieve("para-500", "ru", question, 3).passages().get(0).sectionType();
    }

    @Test
    void typicalQuestionsReachTheRightSection() {
        assertEquals("children", top("Можно ли давать детям?"));
        assertEquals("storage", top("Как хранить?"));
        assertEquals("side_effects", top("Какие побочные эффекты?"));
        assertEquals("pregnancy", top("Можно при беременности?"));
        assertEquals("dosing", top("Сколько принимать в день?"));
        assertEquals("indications", top("От чего помогает?"));
        assertEquals("contraindications", top("Кому нельзя принимать?"));
        assertEquals("shelf_life", top("Какой срок годности?"));
        assertEquals("interactions", top("Можно вместе с алкоголем?"));
    }

    @Test
    void passagesComeOnlyFromTheConfirmedProduct() {
        RetrievalResult r = retriever.retrieve("ibu-susp", "ru", "Как хранить?", 5);
        assertTrue(r.passages().stream().allMatch(p -> p.variantId().equals("ibu-susp")));
        assertTrue(r.passages().stream().allMatch(p -> p.passageId().startsWith("ibu-susp:")));
    }

    @Test
    void missingKazakhLeafletFallsBackToRussian() {
        RetrievalResult r = retriever.retrieve("para-500", "kk", "Как хранить?", 3);
        assertTrue(r.langFallback());
        assertEquals("ru", r.usedLang());
    }

    @Test
    void unknownProductIsRejected() {
        assertThrows(NoSuchElementException.class, () -> retriever.retrieve("nope", "ru", "Как хранить?", 3));
    }

    @Test
    void currentKazakhstanHeadingFormatIsRetrieved() {
        // Section titles as in current leaflets ("Листок-вкладыш" format), see tools/ingest_leaflet.py.
        String[][] secs = {
                {"indications", "Показания к применению"}, {"contraindications", "Противопоказания"},
                {"interactions", "Взаимодействия с другими лекарственными препаратами"},
                {"children", "Применение в педиатрии"}, {"pregnancy", "Во время беременности или лактации"},
                {"driving", "Особенности влияния лекарственного средства на способность управлять транспортным средством"},
                {"dosing", "Режим дозирования"}, {"side_effects", "Описание нежелательных реакций"},
                {"storage", "Условия хранения"}, {"shelf_life", "Срок хранения"},
                {"manufacturer", "Сведения о производителе"}};
        java.util.List<Model.Section> list = new java.util.ArrayList<>();
        for (int i = 0; i < secs.length; i++)
            list.add(new Model.Section("x:ru:" + secs[i][0] + ":" + i, secs[i][0], secs[i][1], "Текст раздела."));
        Catalogue c = new Catalogue(
                List.of(new Model.Variant("x", "Икс", "икс", "1 мг", "таблетки", "Завод", "R", null, false)),
                List.of(new Model.Leaflet("x", "ru", "URL", "2026-11-16", list)));
        Retriever r = new Retriever(c);
        java.util.function.Function<String, String> top = q -> r.retrieve("x", "ru", q, 1).passages().get(0).sectionType();
        assertEquals("driving", top.apply("Можно ли водить машину?"));
        assertEquals("side_effects", top.apply("Какие побочные эффекты?"));
        assertEquals("dosing", top.apply("Как принимать, какая дозировка?"));
        assertEquals("children", top.apply("Можно ли детям?"));
        assertEquals("manufacturer", top.apply("Кто производитель?"));
    }

    @Test
    void bm25PrefersDocumentWithTheTerm() {
        Bm25Index idx = new Bm25Index(List.of("хранение температура", "дети дозы", "побочные действия"), "ru");
        assertEquals(0, (int) idx.search("температура", 1).get(0)[0]);
        assertTrue(idx.idf("температур") > 0);
        assertTrue(idx.search("космос", 3).isEmpty());
    }
}
