package kz.medbox.answers;

import kz.medbox.core.Catalogue;
import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.Passage;
import kz.medbox.core.Retriever;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import static kz.medbox.answers.AnswerModel.*;

/** Framework-free answer policy. Exact quotation is checked independently of the model. */
public final class AnswerService {
    private static final Set<String> CARD_SECTIONS = Set.of(
            "indications", "contraindications", "children", "side_effects", "storage");
    private static final Pattern PERSONAL = Pattern.compile(
            "(?iuU)(\\bмне\\b|у меня|моему|моей|мой ребён|мой ребен|моего ребён|моего ребен|"
            + "реб[её]нку\\s+\\d|что\\s+(выпить|принять)|посоветуй|подбери|"
            + "should i|can i take|my child)");
    private final Catalogue catalogue;
    private final Retriever retriever;
    private final AnswerGateway gateway;

    public AnswerService(Catalogue catalogue, Retriever retriever, AnswerGateway gateway) {
        this.catalogue = catalogue;
        this.retriever = retriever;
        this.gateway = gateway;
    }

    public Answer ask(String variantId, Question request) throws Exception {
        if (request == null || request.question() == null || request.question().isBlank()
                || request.question().length() > 2000) {
            throw new IllegalArgumentException("Вопрос должен содержать от 1 до 2000 символов.");
        }
        String question = request.question().strip();
        verifiedLeaflet(variantId, language(request.lang()));
        if (PERSONAL.matcher(question).find()) return referral();
        List<Passage> passages = retriever.retrieve(variantId, language(request.lang()), question, 5).passages();
        if (passages.isEmpty()) return insufficient();
        Map<String, Passage> allowed = new HashMap<>();
        for (Passage passage : passages) {
            if (!variantId.equals(passage.variantId()) || passage.passageId() == null
                    || passage.passageId().isBlank() || allowed.put(passage.passageId(), passage) != null) {
                return insufficient();
            }
        }
        Selection selection = gateway.select(question, List.copyOf(passages));
        if (selection == null || selection.status() == null) return insufficient();
        if (selection.status() == Status.REFER_TO_PHARMACIST) return referral();
        if (selection.status() == Status.CLARIFY) {
            return new Answer(Status.CLARIFY, "Уточните, какие сведения из инструкции вас интересуют.", List.of());
        }
        if (selection.status() != Status.ANSWERED || selection.quotes() == null
                || selection.quotes().isEmpty() || selection.quotes().size() > 5) return insufficient();

        List<Quote> verified = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SelectedQuote quote : selection.quotes()) {
            if (quote == null) return insufficient();
            Passage passage = allowed.get(quote.passageId());
            if (passage == null || quote.text() == null || quote.text().isBlank()
                    || !passage.text().contains(quote.text()) || !https(passage.sourceUrl())) return insufficient();
            if (seen.add(quote.passageId() + "\n" + quote.text())) {
                verified.add(new Quote(passage.passageId(), passage.title(), quote.text(), passage.sourceUrl()));
            }
        }
        // The response is assembled from verified quotations. Model-written medical prose is never emitted.
        String answer = String.join("\n\n", verified.stream().map(Quote::text).toList());
        return new Answer(Status.ANSWERED, answer, List.copyOf(verified));
    }

    public Card card(String variantId, String lang) {
        Leaflet leaflet = verifiedLeaflet(variantId, language(lang));
        List<CardLine> lines = leaflet.sections().stream()
                .filter(section -> CARD_SECTIONS.contains(section.type()))
                .filter(section -> section.passageId() != null && !section.passageId().isBlank())
                .filter(section -> section.text() != null && !section.text().isBlank())
                .map(section -> new CardLine(section.title(), section.text(), section.passageId()))
                .toList();
        return new Card(lines);
    }

    private Leaflet verifiedLeaflet(String variantId, String lang) {
        var variant = catalogue.get(variantId).orElseThrow(() -> new NoSuchElementException("Препарат не найден."));
        if (variant.sample()) throw new IllegalStateException("Для учебного образца требуется официальная инструкция.");
        Leaflet leaflet = catalogue.leaflet(variantId, lang).or(() -> catalogue.leaflet(variantId, "ru"))
                .orElseThrow(() -> new NoSuchElementException("Инструкция не найдена."));
        if (!https(leaflet.sourceUrl())) throw new IllegalStateException("Источник инструкции ещё не настроен.");
        return leaflet;
    }

    private static String language(String lang) {
        String value = lang == null || lang.isBlank() ? "ru" : lang.toLowerCase(Locale.ROOT);
        if (!Set.of("ru", "kk").contains(value)) throw new IllegalArgumentException("Язык должен быть ru или kk.");
        return value;
    }

    private static boolean https(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (RuntimeException exception) { return false; }
    }

    private static Answer insufficient() {
        return new Answer(Status.INSUFFICIENT,
                "В найденных разделах инструкции недостаточно подтверждённых сведений для ответа.", List.of());
    }

    private static Answer referral() {
        return new Answer(Status.REFER_TO_PHARMACIST,
                "Индивидуальный выбор препарата и лечения обсудите с врачом или фармацевтом. Можно спросить о конкретном разделе инструкции.", List.of());
    }
}
