package kz.medbox.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextNormTest {

    @Test
    void strengthIsCanonical() {
        assertEquals("500mg", TextNorm.normStrength("500 мг"));
        assertEquals("500mg", TextNorm.normStrength("500mg"));
        assertEquals("100mg/5ml", TextNorm.normStrength("100 мг / 5 мл"));
        assertEquals("0.5g", TextNorm.normStrength("0,50 г"));
        assertEquals("325mg+10mg", TextNorm.normStrength("325 мг + 10 мг"));
        assertEquals("", TextNorm.normStrength(null));
        assertEquals("", TextNorm.normStrength("таблетки"));
    }

    @Test
    void formIsCategorised() {
        assertEquals("tablet", TextNorm.normForm("Таблетки, покрытые пленочной оболочкой"));
        assertEquals("effervescent_tablet", TextNorm.normForm("таблетки шипучие"));
        assertEquals("suspension", TextNorm.normForm("суспензия для приема внутрь"));
        assertEquals("syrup", TextNorm.normForm("Сироп"));
        assertEquals("", TextNorm.normForm(null));
    }

    @Test
    void russianStemmingMergesWordForms() {
        assertEquals(TextNorm.stemRu("дети"), TextNorm.stemRu("детям"));
        assertEquals(TextNorm.stemRu("детей"), TextNorm.stemRu("детям"));
        assertEquals(TextNorm.stemRu("беременность"), TextNorm.stemRu("беременности"));
        assertEquals("500", TextNorm.stemRu("500"));
    }

    @Test
    void nameSimilarityToleratesOcrTypos() {
        assertTrue(TextNorm.nameSimilarity("Парацетамол-0бразец", "Парацетамол-Образец") > 0.9);
        assertTrue(TextNorm.nameSimilarity("Ибупрофен", "Парацетамол") < 0.5);
    }
}
