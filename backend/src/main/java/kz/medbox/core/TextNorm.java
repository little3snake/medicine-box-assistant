package kz.medbox.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text normalisation shared by the product matcher and the retriever.
 * Framework-free so it can be unit-tested without Spring.
 */
public final class TextNorm {

    private TextNorm() {}

    /** Lowercase, ё→е, drop trademark signs and punctuation, collapse spaces. */
    public static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
        t = t.replaceAll("[®™©]", "");
        t = t.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
        return t.trim().replaceAll("\\s+", " ");
    }

    /** Raw tokens (letters/digits only). */
    public static List<String> tokens(String s) {
        String n = norm(s);
        List<String> out = new ArrayList<>();
        if (n.isEmpty()) return out;
        for (String t : n.split(" ")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    // Very small stop-word list for questions. Kept short on purpose: BM25 IDF handles the rest.
    private static final Set<String> STOP = Set.of(
            "и", "в", "во", "не", "на", "с", "со", "по", "к", "ко", "ли", "можно", "что", "как",
            "это", "а", "или", "у", "о", "об", "из", "от", "до", "за", "же", "бы", "мне", "я",
            "его", "ее", "их", "при", "то", "этот", "эти", "есть");

    // Russian endings, longest first. A light "suffix stripping" stemmer, not a full Snowball stemmer.
    private static final String[] RU_SUFFIXES = {
            "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими", "иях", "ией",
            "ах", "ях", "ей", "ий", "ый", "ой", "ая", "яя", "ое", "ее", "ые", "ие", "ов", "ев",
            "ам", "ям", "ом", "ем", "ую", "юю", "ия", "ье",
            "а", "я", "ы", "и", "у", "ю", "е", "о", "ь", "й"};

    /** Strip one common Russian ending if at least 3 characters remain. */
    public static String stemRu(String token) {
        if (token.length() <= 3 || token.chars().anyMatch(Character::isDigit)) return token;
        for (String suf : RU_SUFFIXES) {
            if (token.endsWith(suf) && token.length() - suf.length() >= 3) {
                return token.substring(0, token.length() - suf.length());
            }
        }
        return token;
    }

    /**
     * Tokens prepared for BM25: normalised, stop words removed, stemmed for Russian.
     * Kazakh ("kk") is not stemmed yet — TODO once a native speaker checks the approach.
     */
    public static List<String> indexTokens(String s, String lang) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(s)) {
            if (STOP.contains(t)) continue;
            out.add("ru".equals(lang) ? stemRu(t) : t);
        }
        return out;
    }

    private static final Pattern STRENGTH = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(мкг|mcg|µg|мг|mg|г|g|мл|ml|%|ме|iu|ед)"
                    + "(?:\\s*/\\s*(\\d+(?:[.,]\\d+)?)?\\s*(мл|ml|г|g|доз[аы]?|dose))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Canonical strength string, e.g. "500 мг" → "500mg", "100 мг/5 мл" → "100mg/5ml",
     * combination products joined with "+". Returns "" if nothing recognisable.
     */
    public static String normStrength(String s) {
        if (s == null) return "";
        Matcher m = STRENGTH.matcher(s.toLowerCase(Locale.ROOT).replace('ё', 'е'));
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String p = num(m.group(1)) + unit(m.group(2));
            if (m.group(4) != null) {
                String denom = m.group(3) == null ? "1" : num(m.group(3));
                p += "/" + denom + unit(m.group(4));
            }
            parts.add(p);
        }
        return String.join("+", parts);
    }

    private static String num(String n) {
        String x = n.replace(',', '.');
        if (x.contains(".")) x = x.replaceAll("0+$", "").replaceAll("\\.$", "");
        return x;
    }

    private static String unit(String u) {
        String x = u.toLowerCase(Locale.ROOT);
        return switch (x) {
            case "мкг", "mcg", "µg" -> "mcg";
            case "мг", "mg" -> "mg";
            case "г", "g" -> "g";
            case "мл", "ml" -> "ml";
            case "ме", "iu", "ед" -> "iu";
            case "%" -> "%";
            default -> x.startsWith("доз") || x.equals("dose") ? "dose" : x;
        };
    }

    /** Coarse dosage-form category. Order matters: "шипучие таблетки" must not become plain "tablet". */
    public static String normForm(String s) {
        String t = norm(s);
        if (t.isEmpty()) return "";
        if (has(t, "шипуч", "effervesc")) return "effervescent_tablet";
        if (has(t, "таблет", "tablet")) return "tablet";
        if (has(t, "капсул", "capsul")) return "capsule";
        if (has(t, "сироп", "syrup")) return "syrup";
        if (has(t, "суспенз", "suspens")) return "suspension";
        if (has(t, "свеч", "суппоз", "suppos")) return "suppository";
        if (has(t, "капли", "капель", "drops")) return "drops";
        if (has(t, "спрей", "spray")) return "spray";
        if (has(t, "мазь", "ointment")) return "ointment";
        if (has(t, "гель", "gel")) return "gel";
        if (has(t, "порош", "powder", "гранул")) return "powder";
        if (has(t, "раствор", "solution")) return "solution";
        return t;
    }

    private static boolean has(String t, String... keys) {
        for (String k : keys) if (t.contains(k)) return true;
        return false;
    }

    /** 1 - Levenshtein distance / longer length, on normalised strings without spaces. */
    public static double editSimilarity(String a, String b) {
        String x = norm(a).replace(" ", ""), y = norm(b).replace(" ", "");
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        int[] prev = new int[y.length() + 1], cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) prev[j] = j;
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int c = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return 1.0 - (double) prev[y.length()] / Math.max(x.length(), y.length());
    }

    /** Jaccard similarity of token sets. */
    public static double tokenJaccard(String a, String b) {
        Set<String> x = new java.util.HashSet<>(tokens(a)), y = new java.util.HashSet<>(tokens(b));
        if (x.isEmpty() || y.isEmpty()) return 0.0;
        Set<String> inter = new java.util.HashSet<>(x);
        inter.retainAll(y);
        Set<String> union = new java.util.HashSet<>(x);
        union.addAll(y);
        return (double) inter.size() / union.size();
    }

    /** Best of token overlap and edit similarity — robust to OCR typos and extra words. */
    public static double nameSimilarity(String a, String b) {
        return Math.max(tokenJaccard(a, b), editSimilarity(a, b));
    }
}
