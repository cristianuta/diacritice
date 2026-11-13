package ro.diacritice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.diacritice.DiacriticsRestorer;

/**
 * API REST: {@code POST /api/diacritice} cu {@code {"text": "..."}}
 * → {@code {"text": "...", "modificari": n}}.
 */
@RestController
@RequestMapping("/api")
public class DiacriticsController {

    public static final int MAX_TEXT_LENGTH = 200_000;

    private final DiacriticsRestorer restorer;

    public DiacriticsController(DiacriticsRestorer restorer) {
        this.restorer = restorer;
    }

    @PostMapping("/diacritice")
    public DiacriticsResponse addDiacritics(@Valid @RequestBody DiacriticsRequest request) {
        DiacriticsRestorer.Result result = restorer.restoreWithStats(request.text());
        return new DiacriticsResponse(result.text(), result.changedWords());
    }

    public record DiacriticsRequest(
            @NotNull(message = "Câmpul „text” este obligatoriu")
            @Size(max = MAX_TEXT_LENGTH, message = "Textul poate avea cel mult {max} de caractere")
            String text) {
    }

    public record DiacriticsResponse(String text, int modificari) {
    }
}
