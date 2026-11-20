package com.ipp.policy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through HTTP and a real Mongo (Testcontainers). Seed policies are loaded on startup.
 * NOTE: written in the cloud sandbox without Maven access; first executed by the VS Code agent.
 */
@Testcontainers
@SpringBootTest(properties = "ipp.security.enabled=false")
@AutoConfigureMockMvc
class PolicyApiIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0.12");

    @Autowired
    MockMvc mvc;

    @Test
    void seededPoliciesAreListedAndActive() throws Exception {
        mvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void evaluateApprovesEligibleConstructionWorker() throws Exception {
        String body = """
                {"facts": {
                  "person": {"age": 31, "securityStatus": "CLEAR", "magneticCardValid": true},
                  "employer": {"sector": "CONSTRUCTION"},
                  "permit": {"type": "WORK_ENTRY"},
                  "quotas": {"CONSTRUCTION": {"remaining": 5, "total": 100}},
                  "sanctions": {"active": false},
                  "form": {"idNumber": "900000001", "photo": "s3://x", "magneticCard": "MC-1",
                           "employerId": "EMP-1", "workRegion": "HEBRON"}
                }}""";
        mvc.perform(post("/api/policies/evaluate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.outcome").value("APPROVE"))
                .andExpect(jsonPath("$.decision.validityDays").value(90))
                .andExpect(jsonPath("$.decision.policyName").value("work-permit-construction"));
    }

    @Test
    void invalidPolicyReturns422WithLineNumbers() throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"RULE \\\"x\\\" VERSION 1\\nWHEN person.age >=\\nTHEN APPROVE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POLICY_INVALID"))
                .andExpect(jsonPath("$.errors[0].line").value(3));
    }

    @Test
    void draftPublishAndDiffLifecycle() throws Exception {
        String v2 = "RULE \\\"exit-abroad\\\" VERSION 2\\nAPPLIES TO permit.type == EXIT_ABROAD\\n"
                + "WHEN person.securityStatus == CLEAR\\nTHEN APPROVE VALIDITY 7d";
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"" + v2 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("DRAFT"));
        mvc.perform(post("/api/policies/exit-abroad/versions/2/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/policies/exit-abroad/versions"))
                .andExpect(jsonPath("$[1].status").value("RETIRED"));
        mvc.perform(get("/api/policies/exit-abroad/diff").param("from", "1").param("to", "2"))
                .andExpect(status().isOk());
        // same version again → 409
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("{\"source\": \"" + v2 + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void simulateReturnsCounts() throws Exception {
        String draft = "RULE \\\"work-permit-agriculture\\\" VERSION 9\\nAPPLIES TO permit.type == WORK_ENTRY AND employer.sector == AGRICULTURE\\n"
                + "WHEN person.age >= 40\\nTHEN APPROVE VALIDITY 60d";
        mvc.perform(post("/api/policies/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\": \"" + draft + "\", \"caseCount\": 500, \"seed\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(500));
    }
}
