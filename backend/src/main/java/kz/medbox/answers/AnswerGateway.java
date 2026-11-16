package kz.medbox.answers;

import kz.medbox.core.Model.Passage;
import java.util.List;

@FunctionalInterface
public interface AnswerGateway {
    AnswerModel.Selection select(String question, List<Passage> passages) throws Exception;
}
