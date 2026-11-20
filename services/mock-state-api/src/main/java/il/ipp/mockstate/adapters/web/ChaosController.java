package il.ipp.mockstate.adapters.web;

import il.ipp.mockstate.domain.Chaos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo control panel for fault injection. Exposed only inside the compose network / localhost;
 * the gateway must NOT route /admin/** from the browser (see README).
 */
@RestController
@RequestMapping("/admin/chaos")
@Tag(name = "Chaos", description = "הזרקת השהיה וכשלים להדגמת עמידות (circuit breaker)")
class ChaosController {

    private final Chaos chaos;

    ChaosController(Chaos chaos) {
        this.chaos = chaos;
    }

    @GetMapping
    Chaos.Settings get() { return chaos.get(); }

    @Operation(summary = "דוגמה: {\"latencyMs\":2000,\"jitterMs\":500,\"failureRate\":0.3,\"outage\":false}")
    @PutMapping
    Chaos.Settings set(@RequestBody Chaos.Settings settings) {
        chaos.set(settings);
        return chaos.get();
    }

    @Operation(summary = "חזרה למצב תקין")
    @DeleteMapping
    Chaos.Settings reset() {
        chaos.reset();
        return chaos.get();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
