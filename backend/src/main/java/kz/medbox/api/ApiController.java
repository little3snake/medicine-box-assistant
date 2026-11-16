package kz.medbox.api;

import kz.medbox.core.Catalogue;
import kz.medbox.core.Model.ExtractedFields;
import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.MatchResult;
import kz.medbox.core.Model.RetrievalResult;
import kz.medbox.core.Model.Variant;
import kz.medbox.core.ProductMatcher;
import kz.medbox.core.Retriever;
import kz.medbox.vision.GeminiVisionClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Backend REST API (backend part of PLAN.md §3).
 * Answer generation (/ask) is built on top of /retrieve by the answer-generation module.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final Catalogue catalogue;
    private final ProductMatcher matcher;
    private final Retriever retriever;
    private final GeminiVisionClient vision;

    public ApiController(Catalogue catalogue, ProductMatcher matcher, Retriever retriever, GeminiVisionClient vision) {
        this.catalogue = catalogue;
        this.matcher = matcher;
        this.retriever = retriever;
        this.vision = vision;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "variants", catalogue.all().size(), "visionConfigured", vision.configured());
    }

    /** List / search products (for the manual "choose your product" screen). */
    @GetMapping("/products")
    public List<Variant> products(@RequestParam(required = false) String q) {
        return catalogue.search(q);
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable String id) {
        Variant v = catalogue.get(id).orElseThrow(() -> new NoSuchElementException("Unknown product: " + id));
        return Map.of("variant", v, "languages", catalogue.languages(id));
    }

    /** Full leaflet sections (verbatim) — used by the readable card and the leaflet viewer. */
    @GetMapping("/products/{id}/leaflet")
    public Leaflet leaflet(@PathVariable String id, @RequestParam(defaultValue = "ru") String lang) {
        catalogue.get(id).orElseThrow(() -> new NoSuchElementException("Unknown product: " + id));
        return catalogue.leaflet(id, lang)
                .or(() -> catalogue.leaflet(id, "ru"))
                .orElseThrow(() -> new NoSuchElementException("No leaflet for product: " + id));
    }

    /** Photo → vision model → fields → catalogue match. */
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MatchResult identify(@RequestPart("image") MultipartFile image) throws IOException, InterruptedException {
        if (image.isEmpty()) throw new IllegalArgumentException("Empty image");
        ExtractedFields fields = vision.extract(image.getBytes(), image.getContentType());
        return matcher.match(fields);
    }

    /** Fields typed or corrected by the user (and used by the evaluation) → catalogue match, no vision call. */
    @PostMapping("/identify/fields")
    public MatchResult identifyFields(@RequestBody ExtractedFields fields) {
        return matcher.match(fields);
    }

    public record RetrieveRequest(String question, String lang, Integer k) {}

    /** Question → top-k passages from the confirmed product's leaflet only. */
    @PostMapping("/products/{id}/retrieve")
    public RetrievalResult retrieve(@PathVariable String id, @RequestBody RetrieveRequest req) {
        if (req.question() == null || req.question().isBlank()) throw new IllegalArgumentException("Empty question");
        int k = req.k() == null ? 3 : Math.min(Math.max(req.k(), 1), 10);
        return retriever.retrieve(id, req.lang(), req.question(), k);
    }
}
