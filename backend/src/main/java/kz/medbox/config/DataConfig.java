package kz.medbox.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.medbox.core.Catalogue;
import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.Section;
import kz.medbox.core.Model.Variant;
import kz.medbox.core.ProductMatcher;
import kz.medbox.core.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads data/catalogue.json and data/leaflets/<variant-id>/<lang>.json at startup.
 * Passage ids are generated as "<variant>:<lang>:<type>:<index>" so they are stable and readable.
 */
@Configuration
public class DataConfig {

    private static final Logger log = LoggerFactory.getLogger(DataConfig.class);

    @Bean
    public Catalogue catalogue(@Value("${medbox.data-dir}") String dataDir, ObjectMapper mapper) throws IOException {
        return load(Path.of(dataDir), mapper);
    }

    @Bean
    public ProductMatcher productMatcher(Catalogue catalogue) { return new ProductMatcher(catalogue); }

    @Bean
    public Retriever retriever(Catalogue catalogue) { return new Retriever(catalogue); }

    public static Catalogue load(Path dir, ObjectMapper mapper) throws IOException {
        Path cat = dir.resolve("catalogue.json");
        if (!Files.exists(cat)) throw new IllegalStateException("catalogue.json not found in " + dir.toAbsolutePath());
        List<Variant> variants = List.of(mapper.readValue(cat.toFile(), Variant[].class));

        List<Leaflet> leaflets = new ArrayList<>();
        Path ldir = dir.resolve("leaflets");
        if (Files.isDirectory(ldir)) {
            try (Stream<Path> files = Files.walk(ldir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                    leaflets.add(readLeaflet(p, mapper));
                }
            }
        }
        long samples = variants.stream().filter(Variant::sample).count();
        log.info("Loaded {} variants ({} SAMPLE), {} leaflets from {}", variants.size(), samples, leaflets.size(), dir.toAbsolutePath());
        return new Catalogue(variants, leaflets);
    }

    private static Leaflet readLeaflet(Path p, ObjectMapper mapper) throws IOException {
        JsonNode n = mapper.readTree(p.toFile());
        String variantId = n.path("variantId").asText();
        String lang = n.path("lang").asText();
        List<Section> sections = new ArrayList<>();
        int i = 0;
        for (JsonNode s : n.path("sections")) {
            String type = s.path("type").asText();
            sections.add(new Section(variantId + ":" + lang + ":" + type + ":" + i++,
                    type, s.path("title").asText(), s.path("text").asText()));
        }
        return new Leaflet(variantId, lang, n.path("sourceUrl").asText(), n.path("retrievedAt").asText(), sections);
    }
}
