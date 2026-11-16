package kz.medbox.core;

import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.Passage;
import kz.medbox.core.Model.RetrievalResult;
import kz.medbox.core.Model.Section;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Retrieval restricted to the CONFIRMED product's leaflet (never across products),
 * so an answer can only be supported by that product's own text.
 *
 * Language: use the requested leaflet; if it does not exist (e.g. no Kazakh leaflet),
 * fall back to Russian and set langFallback=true so the app can say so.
 *
 * Checkpoint version: BM25 only. Hybrid BM25 + multilingual embeddings is planned for 23 Nov.
 */
public final class Retriever {

    private record Entry(Leaflet leaflet, Bm25Index index) {}

    private final Catalogue catalogue;
    private final Map<String, Entry> indexes = new HashMap<>();

    public Retriever(Catalogue catalogue) {
        this.catalogue = catalogue;
        for (Leaflet l : catalogue.allLeaflets()) {
            List<String> docs = new ArrayList<>();
            for (Section s : l.sections()) {
                docs.add(s.title() + " " + SectionTypes.keywords(s.type(), l.lang()) + " " + s.text());
            }
            indexes.put(key(l.variantId(), l.lang()), new Entry(l, new Bm25Index(docs, l.lang())));
        }
    }

    public RetrievalResult retrieve(String variantId, String lang, String question, int k) {
        if (catalogue.get(variantId).isEmpty()) throw new NoSuchElementException("Unknown product: " + variantId);
        String requested = lang == null || lang.isBlank() ? "ru" : lang;
        Optional<Entry> e = Optional.ofNullable(indexes.get(key(variantId, requested)));
        boolean fallback = false;
        if (e.isEmpty()) {
            e = Optional.ofNullable(indexes.get(key(variantId, "ru")));
            fallback = true;
        }
        if (e.isEmpty()) throw new NoSuchElementException("No leaflet for product: " + variantId);

        Entry entry = e.get();
        List<Passage> out = new ArrayList<>();
        for (double[] hit : entry.index().search(question, Math.max(1, k))) {
            Section s = entry.leaflet().sections().get((int) hit[0]);
            out.add(new Passage(s.passageId(), variantId, entry.leaflet().lang(), s.type(), s.title(),
                    s.text(), entry.leaflet().sourceUrl(), Math.round(hit[1] * 1000.0) / 1000.0));
        }
        return new RetrievalResult(variantId, requested, entry.leaflet().lang(), fallback, out);
    }

    private static String key(String id, String lang) { return id + "|" + lang; }
}
