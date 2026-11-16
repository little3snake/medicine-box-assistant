package kz.medbox.answers;

import kz.medbox.core.Catalogue;
import kz.medbox.core.Retriever;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/products/{id}")
public final class AnswerController {
    private final ObjectProvider<Catalogue> catalogues;
    private final ObjectProvider<Retriever> retrievers;
    private final GeminiAnswerClient gateway;

    public AnswerController(ObjectProvider<Catalogue> catalogues, ObjectProvider<Retriever> retrievers,
                            GeminiAnswerClient gateway) {
        this.catalogues = catalogues;
        this.retrievers = retrievers;
        this.gateway = gateway;
    }

    @PostMapping("/ask")
    public AnswerModel.Answer ask(@PathVariable String id, @RequestBody AnswerModel.Question request) throws Exception {
        return service().ask(id, request);
    }

    @GetMapping("/card")
    public AnswerModel.Card card(@PathVariable String id, @RequestParam(defaultValue = "ru") String lang) {
        return service().card(id, lang);
    }

    private AnswerService service() {
        Catalogue catalogue = catalogues.getIfAvailable();
        Retriever retriever = retrievers.getIfAvailable();
        if (catalogue == null || retriever == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue and Retriever beans are not configured yet.");
        }
        return new AnswerService(catalogue, retriever, gateway);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public java.util.Map<String, String> missing(NoSuchElementException exception) {
        return java.util.Map.of("error", "Препарат или инструкция не найдены.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalid(IllegalArgumentException exception) {
        return java.util.Map.of("error", "Некорректный вопрос или язык.");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public java.util.Map<String, String> unavailable(IllegalStateException exception) {
        return java.util.Map.of("error", "Источник данных или модель пока не готовы.");
    }

    @ExceptionHandler(java.io.IOException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public java.util.Map<String, String> providerUnavailable(java.io.IOException exception) {
        return java.util.Map.of("error", "Модель не ответила. Попробуйте ещё раз позже.");
    }
}
