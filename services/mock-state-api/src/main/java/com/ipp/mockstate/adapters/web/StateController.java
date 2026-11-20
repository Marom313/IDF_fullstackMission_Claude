package com.ipp.mockstate.adapters.web;

import com.ipp.mockstate.domain.Chaos;
import com.ipp.mockstate.domain.IdNumbers;
import com.ipp.mockstate.domain.MockPopulation;
import com.ipp.mockstate.domain.MockRecords.CriminalRecord;
import com.ipp.mockstate.domain.MockRecords.PersonRecord;
import com.ipp.mockstate.domain.MockRecords.PersonSummary;
import com.ipp.mockstate.domain.MockRecords.SecurityRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/state")
@Validated
@Tag(name = "Mock State", description = "דמה של ממשקי המדינה: מרשם, סטטוס ביטחוני, רישום פלילי. כל הנתונים סינתטיים")
class StateController {

    private final MockPopulation population;
    private final Chaos chaos;
    private final Clock clock;

    StateController(MockPopulation population, Chaos chaos, Clock clock) {
        this.population = population;
        this.chaos = chaos;
        this.clock = clock;
    }

    @Operation(summary = "רשומת מרשם אוכלוסין")
    @GetMapping("/registry/persons/{id}")
    PersonRecord person(@PathVariable String id) {
        return lookup(id, PersonSummary::person);
    }

    @Operation(summary = "סטטוס ביטחוני (קוד סיבה בלבד, בלי טקסט חופשי)")
    @GetMapping("/security/{id}")
    SecurityRecord security(@PathVariable String id) {
        return lookup(id, PersonSummary::security);
    }

    @Operation(summary = "רישום פלילי ותיקים פתוחים")
    @GetMapping("/criminal/{id}")
    CriminalRecord criminal(@PathVariable String id) {
        return lookup(id, PersonSummary::criminal);
    }

    @Operation(summary = "שלושת המקורות בקריאה אחת (לשימוש eligibility-service)")
    @GetMapping("/persons/{id}/summary")
    PersonSummary summary(@PathVariable String id) {
        return lookup(id, Function.identity());
    }

    @Operation(summary = "רשימת מספרי זהות באוכלוסיית הדמו (ל-seed של שירותים אחרים)")
    @GetMapping("/registry/ids")
    Map<String, Object> ids(@RequestParam(defaultValue = "0") @Min(0) int offset,
                            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        List<String> ids = population.ids(offset, limit);
        return Map.of("offset", offset, "limit", limit, "total", population.size(), "ids", ids);
    }

    @Operation(summary = "בדיקת ספרת ביקורת של מספר זהות. מזהי הדמו (90…, 98…) הם טווח שמור ללא ספרת ביקורת")
    @GetMapping("/validate-id/{id}")
    Map<String, Object> validateId(@PathVariable String id) {
        return Map.of("idNumber", id, "wellFormed", IdNumbers.isValid(id));
    }

    // ------------------------------------------------------------------

    private <T> T lookup(String id, Function<PersonSummary, T> pick) {
        applyChaos();
        if (id == null || !id.matches("\\d{9}")) throw new BadId(id);
        LocalDate today = LocalDate.now(clock);
        return population.find(id, today).map(pick).orElseThrow(() -> new NotFound(id));
    }

    private void applyChaos() {
        long delay = chaos.delayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);              // virtual threads: a sleeping request costs almost nothing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (chaos.roll() == Chaos.Effect.FAIL) throw new InjectedFailure();
    }

    static final class BadId extends RuntimeException { BadId(String id) { super("מספר זהות לא תקין: " + id); } }

    static final class NotFound extends RuntimeException { NotFound(String id) { super("לא נמצא אדם עם מספר זהות " + id); } }

    static final class InjectedFailure extends RuntimeException { InjectedFailure() { super("כשל מוזרק (chaos)"); } }

    @ExceptionHandler(BadId.class)
    ProblemDetail badId(BadId e) { return problem(HttpStatus.BAD_REQUEST, "INVALID_ID", e.getMessage()); }

    @ExceptionHandler(NotFound.class)
    ProblemDetail notFound(NotFound e) { return problem(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", e.getMessage()); }

    @ExceptionHandler(InjectedFailure.class)
    ProblemDetail injected(InjectedFailure e) { return problem(HttpStatus.SERVICE_UNAVAILABLE, "STATE_UNAVAILABLE", e.getMessage()); }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:ipp:error:" + code.toLowerCase()));
        pd.setProperty("code", code);
        return pd;
    }
}
