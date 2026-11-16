package kz.medbox.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Okapi BM25 over a small set of documents (the sections of ONE leaflet).
 *
 * score(q, d) = Σ_t  idf(t) · tf(t,d)·(k1+1) / (tf(t,d) + k1·(1 − b + b·|d|/avgdl))
 * idf(t)      = ln(1 + (N − df(t) + 0.5) / (df(t) + 0.5))
 *
 * k1 controls how fast repeated terms stop adding score; b controls length normalisation.
 * Reference: Robertson & Zaragoza, "The Probabilistic Relevance Framework: BM25 and Beyond" (2009).
 */
public final class Bm25Index {

    public static final double K1 = 1.2, B = 0.75;

    private final List<Map<String, Integer>> tf = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private final String lang;
    private double avgdl;

    /** @param docs already-assembled document texts, in a fixed order (index = doc id) */
    public Bm25Index(List<String> docs, String lang) {
        this.lang = lang;
        long total = 0;
        for (String d : docs) {
            List<String> toks = TextNorm.indexTokens(d, lang);
            Map<String, Integer> counts = new HashMap<>();
            for (String t : toks) counts.merge(t, 1, Integer::sum);
            for (String t : counts.keySet()) df.merge(t, 1, Integer::sum);
            tf.add(counts);
            lengths.add(toks.size());
            total += toks.size();
        }
        avgdl = docs.isEmpty() ? 0 : (double) total / docs.size();
    }

    public int size() { return tf.size(); }

    public double idf(String term) {
        int n = size();
        int d = df.getOrDefault(term, 0);
        return Math.log(1.0 + (n - d + 0.5) / (d + 0.5));
    }

    public double score(List<String> queryTerms, int doc) {
        Map<String, Integer> counts = tf.get(doc);
        double norm = K1 * (1 - B + B * lengths.get(doc) / (avgdl == 0 ? 1 : avgdl));
        double s = 0;
        for (String q : queryTerms) {
            int f = counts.getOrDefault(q, 0);
            if (f == 0) continue;
            s += idf(q) * f * (K1 + 1) / (f + norm);
        }
        return s;
    }

    /** Top-k (docIndex, score) pairs with score > 0, best first. */
    public List<double[]> search(String query, int k) {
        List<String> q = TextNorm.indexTokens(query, lang);
        List<double[]> hits = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            double s = score(q, i);
            if (s > 0) hits.add(new double[]{i, s});
        }
        hits.sort(Comparator.comparingDouble((double[] h) -> h[1]).reversed());
        return hits.subList(0, Math.min(k, hits.size()));
    }
}
