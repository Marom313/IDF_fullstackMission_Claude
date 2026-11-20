package il.ipp.mockstate;

import il.ipp.mockstate.domain.Chaos;
import il.ipp.mockstate.domain.SeedIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NOTE: written in the cloud sandbox without Maven access; first executed by the VS Code agent. */
@SpringBootTest
@AutoConfigureMockMvc
class StateApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    Chaos chaos;

    @AfterEach
    void healthy() { chaos.reset(); }

    @Test
    void summaryForKnownPerson() throws Exception {
        String id = SeedIds.nationalId(7);
        mvc.perform(get("/api/state/persons/{id}/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.person.idNumber").value(id))
                .andExpect(jsonPath("$.security.status").exists())
                .andExpect(jsonPath("$.person.firstName.ar").exists());
    }

    @Test
    void invalidAndUnknownIds() throws Exception {
        mvc.perform(get("/api/state/security/{id}", "12345")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/state/security/{id}", "123456789")).andExpect(status().isNotFound());
        mvc.perform(get("/api/state/security/{id}", "900000004"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void outageReturns503() throws Exception {
        chaos.set(new Chaos.Settings(0, 0, 0, true));
        mvc.perform(get("/api/state/registry/persons/{id}", SeedIds.nationalId(1)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STATE_UNAVAILABLE"));
    }

    @Test
    void idsPage() throws Exception {
        mvc.perform(get("/api/state/registry/ids").param("offset", "0").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids.length()").value(3))
                .andExpect(jsonPath("$.total").value(5000));
    }
}
