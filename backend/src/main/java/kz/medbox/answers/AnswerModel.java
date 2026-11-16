package kz.medbox.answers;

import java.util.List;

public final class AnswerModel {
    private AnswerModel() {}

    public enum Status { ANSWERED, CLARIFY, INSUFFICIENT, REFER_TO_PHARMACIST }
    public record Question(String question, String lang) {}
    public record Quote(String passageId, String section, String text, String sourceUrl) {}
    public record Answer(Status status, String answer, List<Quote> quotes) {}
    public record CardLine(String field, String text, String passageId) {}
    public record Card(List<CardLine> lines) {}
    public record SelectedQuote(String passageId, String text) {}
    public record Selection(Status status, List<SelectedQuote> quotes) {}
}
