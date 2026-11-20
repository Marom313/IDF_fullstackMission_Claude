package com.ipp.policy.adapters.web;

import com.ipp.policy.adapters.web.PolicyDtos.DecisionDto;
import com.ipp.policy.adapters.web.PolicyDtos.EvaluationResponse;
import com.ipp.policy.adapters.web.PolicyDtos.FactsRequest;
import com.ipp.policy.adapters.web.PolicyDtos.PolicyDetail;
import com.ipp.policy.adapters.web.PolicyDtos.PolicySummary;
import com.ipp.policy.adapters.web.PolicyDtos.RequiredFieldsResponse;
import com.ipp.policy.adapters.web.PolicyDtos.SimulateRequest;
import com.ipp.policy.adapters.web.PolicyDtos.SourceRequest;
import com.ipp.policy.application.PolicyCatalogService;
import com.ipp.policy.domain.dsl.LineDiff;
import com.ipp.policy.domain.dsl.PolicyValidator;
import com.ipp.policy.domain.eval.Facts;
import com.ipp.policy.domain.eval.FunctionRegistry;
import com.ipp.policy.domain.eval.Simulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/policies")
@Tag(name = "Policies", description = "ניהול חוקות: כתיבה ב-DSL, גרסאות, פרסום, סימולציה והערכה")
class PolicyController {

    private final PolicyCatalogService service;
    private final FunctionRegistry functions;
    private final Clock clock;

    PolicyController(PolicyCatalogService service, FunctionRegistry functions, Clock clock) {
        this.service = service;
        this.functions = functions;
        this.clock = clock;
    }

    @Operation(summary = "בדיקת תקינות של טקסט חוקה (ללא שמירה). מחזיר שגיאות עם שורה ועמודה, צורה קנונית ו-AST")
    @PostMapping("/validate")
    PolicyCatalogService.ValidationReport validate(@Valid @RequestBody SourceRequest body) {
        return service.validate(body.source());
    }

    @Operation(summary = "שמירת טיוטה חדשה. ה-VERSION בטקסט חייב להיות גבוה מכל גרסה קיימת")
    @PostMapping
    ResponseEntity<PolicyDetail> createDraft(@Valid @RequestBody SourceRequest body, Authentication auth) {
        var rec = service.createDraft(body.source(), user(auth));
        return ResponseEntity.created(URI.create("/api/policies/" + rec.name() + "/versions/" + rec.version()))
                .body(PolicyDetail.of(rec));
    }

    @Operation(summary = "רשימת החוקות (הגרסה האחרונה של כל אחת)")
    @GetMapping
    List<PolicySummary> list() {
        return service.listLatest().stream().map(PolicySummary::of).toList();
    }

    @GetMapping("/{name}/versions")
    List<PolicySummary> versions(@PathVariable String name) {
        return service.versions(name).stream().map(PolicySummary::of).toList();
    }

    @GetMapping("/{name}/versions/{version}")
    PolicyDetail get(@PathVariable String name, @PathVariable int version) {
        return PolicyDetail.of(service.get(name, version));
    }

    @Operation(summary = "פרסום גרסה: הופכת ל-ACTIVE והגרסה הקודמת ל-RETIRED. אידמפוטנטי")
    @PostMapping("/{name}/versions/{version}/publish")
    PolicySummary publish(@PathVariable String name, @PathVariable int version, Authentication auth) {
        return PolicySummary.of(service.publish(name, version, user(auth)));
    }

    @Operation(summary = "השוואה בין שתי גרסאות (diff לפי שורות על הצורה הקנונית)")
    @GetMapping("/{name}/diff")
    List<LineDiff.Line> diff(@PathVariable String name, @RequestParam int from, @RequestParam int to) {
        return service.diff(name, from, to);
    }

    @Operation(summary = "סימולציה: איך טיוטה הייתה מחליטה על N בקשות סינתטיות, לעומת הגרסה הפעילה")
    @PostMapping("/simulate")
    Simulator.Result simulate(@Valid @RequestBody SimulateRequest body) {
        return service.simulate(body.source(),
                body.caseCount() == null ? 1_000 : body.caseCount(),
                body.seed() == null ? 42L : body.seed());
    }

    @Operation(summary = "הערכת מקרה מול כל החוקות הפעילות. אם כמה חלות, התוצאה המחמירה גוברת")
    @PostMapping("/evaluate")
    EvaluationResponse evaluate(@Valid @RequestBody FactsRequest body) {
        var e = service.evaluate(new Facts(body.facts()));
        return new EvaluationResponse(DecisionDto.of(e.decision()),
                e.applicable().stream().map(DecisionDto::of).toList(), Instant.now(clock));
    }

    @Operation(summary = "שדות חובה לטופס דינמי לפי מה שידוע (למשל רק סוג ההיתר)")
    @PostMapping("/required-fields")
    RequiredFieldsResponse requiredFields(@Valid @RequestBody FactsRequest body) {
        return new RequiredFieldsResponse(service.requiredFields(new Facts(body.facts())));
    }

    @Operation(summary = "אוצר המילים של ה-DSL: פונקציות ומקורות נתונים מותרים (להשלמה אוטומטית בעורך)")
    @GetMapping("/vocabulary")
    Map<String, Set<String>> vocabulary() {
        return Map.of("functions", functions.names(), "factRoots", PolicyValidator.KNOWN_ROOTS);
    }

    private static String user(Authentication auth) {
        return auth == null ? "anonymous" : auth.getName();
    }
}
