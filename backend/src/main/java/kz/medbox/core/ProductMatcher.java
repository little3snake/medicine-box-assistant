package kz.medbox.core;

import kz.medbox.core.Model.Candidate;
import kz.medbox.core.Model.ExtractedFields;
import kz.medbox.core.Model.MatchResult;
import kz.medbox.core.Model.ScoreBreakdown;
import kz.medbox.core.Model.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches fields read from a package photo against the catalogue.
 *
 * Score = 0.50·name + 0.25·strength + 0.15·form + 0.10·manufacturer, each part in [0, 1].
 * A match is only "confident" if:
 *   - top score ≥ CONFIDENT_SCORE,
 *   - the gap to the second candidate ≥ MIN_MARGIN,
 *   - strength and form were read from the photo AND both agree with the top candidate.
 * Otherwise needsClarification = true and the app must ask the user.
 * Reason: a wrong confident match is the main safety metric in our proposal,
 * so we prefer asking over guessing (look-alike products differ only in strength or form).
 */
public final class ProductMatcher {

    public static final double W_NAME = 0.50, W_STRENGTH = 0.25, W_FORM = 0.15, W_MANUFACTURER = 0.10;
    public static final double CONFIDENT_SCORE = 0.85;
    public static final double MIN_MARGIN = 0.10;
    public static final double MIN_CANDIDATE_SCORE = 0.35;
    public static final double MIN_NAME_SIMILARITY = 0.6;
    public static final int MAX_CANDIDATES = 5;

    private final Catalogue catalogue;

    public ProductMatcher(Catalogue catalogue) { this.catalogue = catalogue; }

    public MatchResult match(ExtractedFields f) {
        String strength = TextNorm.normStrength(f.strength());
        String form = TextNorm.normForm(f.form());
        Map<String, String> normalized = new LinkedHashMap<>();
        normalized.put("name", TextNorm.norm(f.name()));
        normalized.put("strength", strength);
        normalized.put("form", form);
        normalized.put("manufacturer", TextNorm.norm(f.manufacturer()));

        List<Candidate> scored = new ArrayList<>();
        for (Variant v : catalogue.all()) {
            double name = nameScore(f.name(), v);
            if (name < MIN_NAME_SIMILARITY) continue; // different product entirely
            double s = strength.isEmpty() ? 0.0 : (strength.equals(TextNorm.normStrength(v.strength())) ? 1.0 : 0.0);
            double fo = form.isEmpty() ? 0.0 : (form.equals(TextNorm.normForm(v.form())) ? 1.0 : 0.0);
            double m = isBlank(f.manufacturer()) ? 0.0 : TextNorm.nameSimilarity(f.manufacturer(), v.manufacturer());
            double total = W_NAME * name + W_STRENGTH * s + W_FORM * fo + W_MANUFACTURER * m;
            if (total >= MIN_CANDIDATE_SCORE) {
                scored.add(new Candidate(v.id(), Catalogue.label(v), round(total),
                        new ScoreBreakdown(round(name), s, fo, round(m))));
            }
        }
        scored.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<Candidate> top = scored.subList(0, Math.min(MAX_CANDIDATES, scored.size()));

        List<String> reasons = new ArrayList<>();
        if (top.isEmpty()) {
            reasons.add("NO_CANDIDATES");
        } else {
            Candidate best = top.get(0);
            if (best.score() < CONFIDENT_SCORE) reasons.add("LOW_SCORE");
            if (top.size() > 1 && best.score() - top.get(1).score() < MIN_MARGIN) reasons.add("AMBIGUOUS");
            if (strength.isEmpty()) reasons.add("STRENGTH_NOT_READ");
            else if (best.breakdown().strength() < 1.0) reasons.add("STRENGTH_MISMATCH");
            if (form.isEmpty()) reasons.add("FORM_NOT_READ");
            else if (best.breakdown().form() < 1.0) reasons.add("FORM_MISMATCH");
        }
        return new MatchResult(f, normalized, List.copyOf(top), !reasons.isEmpty(), reasons);
    }

    /** Trade name similarity; INN (generic name) counts less, because many brands share one INN. */
    private static double nameScore(String extracted, Variant v) {
        if (isBlank(extracted)) return 0.0;
        double trade = TextNorm.nameSimilarity(extracted, v.tradeName());
        double inn = isBlank(v.inn()) ? 0.0 : 0.8 * TextNorm.nameSimilarity(extracted, v.inn());
        return Math.max(trade, inn);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static double round(double x) { return Math.round(x * 1000.0) / 1000.0; }
}
