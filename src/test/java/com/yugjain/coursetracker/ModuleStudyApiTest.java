package com.yugjain.coursetracker;

import com.jayway.jsonpath.JsonPath;
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
class ModuleStudyApiTest {

    @Autowired
    MockMvc mvc;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private MockHttpSession registerAndLogin() throws Exception {
        String u = "mod" + System.nanoTime();
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(JSON)
                .content("{\"username\":\"" + u + "\",\"password\":\"secret1\",\"name\":\"Yug\",\"subject\":\"CS\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void modulesStudyLogsAndCourseEditing() throws Exception {
        MockHttpSession s = registerAndLogin();

        String res = mvc.perform(post("/api/courses").session(s).contentType(JSON)
                .content("{\"course_name\":\"Java\",\"platform\":\"Udemy\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long cid = ((Number) JsonPath.read(res, "$.course_id")).longValue();

        // create module with a reference link
        res = mvc.perform(post("/api/courses/" + cid + "/modules").session(s).contentType(JSON)
                .content("{\"title\":\"Intro\",\"is_weak\":true,"
                    + "\"reference_links\":[{\"label\":\"Docs\",\"url\":\"https://example.com\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.module.title").value("Intro"))
            .andExpect(jsonPath("$.module.is_weak").value(1))
            .andExpect(jsonPath("$.module.reference_links[0].label").value("Docs"))
            .andExpect(jsonPath("$.course.total_modules").value(1))
            .andReturn().getResponse().getContentAsString();
        long mid = ((Number) JsonPath.read(res, "$.module.module_id")).longValue();

        mvc.perform(get("/api/courses/" + cid + "/modules").session(s))
            .andExpect(jsonPath("$.modules[0].title").value("Intro"));

        // completing the only module makes the course 100% / Completed
        mvc.perform(post("/api/modules/" + mid + "/toggle").session(s).contentType(JSON)
                .content("{\"is_completed\":true}"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.course_progress.progress_percent").value(100))
            .andExpect(jsonPath("$.course_progress.status").value("Completed"));

        mvc.perform(put("/api/modules/" + mid).session(s).contentType(JSON).content("{\"title\":\"Intro v2\"}"))
            .andExpect(jsonPath("$.module.title").value("Intro v2"));

        mvc.perform(get("/api/modules/999999").session(s)).andExpect(status().isNotFound());

        // study logs
        String log = "{\"course_id\":" + cid + ",\"study_date\":\"2026-10-20\",\"hours_spent\":1.5,\"topic_covered\":\"Intro\"}";
        mvc.perform(post("/api/study-logs").session(s).contentType(JSON).content(log))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/study-logs").session(s).contentType(JSON).content(log))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/study-logs").session(s).contentType(JSON)
                .content("{\"course_id\":" + cid + ",\"hours_spent\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/study-logs").session(s))
            .andExpect(jsonPath("$[0].hours_spent").value(1.5));

        // edit course
        mvc.perform(put("/api/courses/" + cid).session(s).contentType(JSON)
                .content("{\"course_name\":\"Java 2\",\"platform\":\"Udemy\"}"))
            .andExpect(jsonPath("$.course.course_name").value("Java 2"));

        // delete module, then course
        mvc.perform(delete("/api/modules/" + mid).session(s))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.course.total_modules").value(0));

        mvc.perform(delete("/api/courses/" + cid).session(s)).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/courses/" + cid).session(s)).andExpect(status().isNotFound());
    }
}
