package kz.medbox.core;

import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory catalogue of variants and their leaflets (loaded once at startup). */
public final class Catalogue {

    private final Map<String, Variant> variants = new LinkedHashMap<>();
    private final Map<String, Map<String, Leaflet>> leaflets = new LinkedHashMap<>();

    public Catalogue(List<Variant> variantList, List<Leaflet> leafletList) {
        for (Variant v : variantList) {
            if (variants.put(v.id(), v) != null) throw new IllegalArgumentException("Duplicate variant id: " + v.id());
        }
        for (Leaflet l : leafletList) {
            if (!variants.containsKey(l.variantId()))
                throw new IllegalArgumentException("Leaflet for unknown variant: " + l.variantId());
            leaflets.computeIfAbsent(l.variantId(), k -> new LinkedHashMap<>()).put(l.lang(), l);
        }
    }

    public List<Variant> all() { return List.copyOf(variants.values()); }

    public Optional<Variant> get(String id) { return Optional.ofNullable(variants.get(id)); }

    public Optional<Leaflet> leaflet(String variantId, String lang) {
        return Optional.ofNullable(leaflets.getOrDefault(variantId, Map.of()).get(lang));
    }

    public List<String> languages(String variantId) {
        return List.copyOf(leaflets.getOrDefault(variantId, Map.of()).keySet());
    }

    public List<Leaflet> allLeaflets() {
        List<Leaflet> out = new ArrayList<>();
        leaflets.values().forEach(m -> out.addAll(m.values()));
        return out;
    }

    /** Simple text search for the manual "pick your product" screen. */
    public List<Variant> search(String q) {
        if (q == null || q.isBlank()) return all();
        String nq = TextNorm.norm(q);
        return variants.values().stream()
                .map(v -> Map.entry(v, Math.max(
                        TextNorm.nameSimilarity(nq, v.tradeName()),
                        TextNorm.norm(v.tradeName() + " " + v.inn()).contains(nq) ? 1.0 : 0.0)))
                .filter(e -> e.getValue() >= 0.5)
                .sorted(Map.Entry.<Variant, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static String label(Variant v) {
        return v.tradeName() + ", " + v.strength() + ", " + v.form() + " (" + v.manufacturer() + ")";
    }
}
