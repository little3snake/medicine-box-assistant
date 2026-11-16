package kz.medbox.answers;

import kz.medbox.core.Catalogue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring wiring and repository catalogue; these checks do not call an external model. */
@SpringBootTest
@AutoConfigureMockMvc
class AnswerApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Catalogue catalogue;

    @Test void identificationAndProductUseTheSameVariant() throws Exception {
        mvc.perform(post("/api/identify/fields").contentType("application/json")
                .content("{\"name\":\"Анальгин\",\"strength\":\"500 мг\",\"form\":\"таблетки\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].variantId").value("analgin-500-tab-bzmp"));
        mvc.perform(get("/api/products/analgin-500-tab-bzmp"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variant.sample").value(false));
    }

    @Test void realLeafletHasIdsAndReadableCard() throws Exception {
        mvc.perform(get("/api/products/analgin-500-tab-bzmp/card"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].passageId").isNotEmpty());
        var leaflet = catalogue.leaflet("analgin-500-tab-bzmp", "ru").orElseThrow();
        assertTrue(leaflet.sections().stream().allMatch(section -> section.passageId() != null));
        assertEquals(leaflet.sections().size(), leaflet.sections().stream().map(section -> section.passageId()).distinct().count());
    }

    @Test void individualAdviceReturnsReferralWithoutCallingProvider() throws Exception {
        mvc.perform(post("/api/products/analgin-500-tab-bzmp/ask").contentType("application/json")
                .content("{\"question\":\"Что мне принять?\",\"lang\":\"ru\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFER_TO_PHARMACIST"));
    }

    @Test void missingProductAndSampleDoNotYieldMedicalAnswers() throws Exception {
        mvc.perform(get("/api/products/does-not-exist/card")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/sample-paracetamol-500-tab/card")).andExpect(status().isServiceUnavailable());
    }
}
