package com.yugjain.coursetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test_course_tracker.db")
@AutoConfigureMockMvc
class WebApiTest {

    @Autowired
    MockMvc mvc;

    private MockHttpSession registerAndLogin() throws Exception {
        String u = "web" + System.nanoTime();
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u + "\",\"password\":\"secret1\",\"name\":\"Yug\",\"subject\":\"CS\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void coursesKpiAndCharts() throws Exception {
        MockHttpSession s = registerAndLogin();
        String course = "{\"course_name\":\"Java\",\"platform\":\"Udemy\"}";

        mvc.perform(post("/api/courses").session(s).contentType(MediaType.APPLICATION_JSON).content(course))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.course_id").isNumber());

        mvc.perform(post("/api/courses").session(s).contentType(MediaType.APPLICATION_JSON).content(course))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/api/courses").session(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].course_name").value("Java"));

        mvc.perform(get("/api/kpi").session(s))
            .andExpect(jsonPath("$.total_courses").value(1))
            .andExpect(jsonPath("$.engine_name").exists());

        mvc.perform(get("/api/analytics/charts").session(s))
            .andExpect(jsonPath("$.status_distribution.labels[0]").value("Not Started"))
            .andExpect(jsonPath("$.platform_breakdown.values[0]").value(1));
    }

    @Test
    void authRequiredAndMe() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void profileAndLogout() throws Exception {
        MockHttpSession s = registerAndLogin();

        mvc.perform(put("/api/auth/profile").session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"subject\":\"Maths\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("New Name"));

        mvc.perform(get("/api/auth/me").session(s)).andExpect(jsonPath("$.authenticated").value(true));

        mvc.perform(post("/api/auth/logout").session(s)).andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/api/auth/me").session(s)).andExpect(jsonPath("$.authenticated").value(false));
    }
}
