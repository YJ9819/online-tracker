package com.yugjain.coursetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:target/test_course_tracker.db")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    private String registerJson(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"secret1\",\"name\":\"Yug\",\"subject\":\"CS\"}";
    }

    private String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerThenLogin() throws Exception {
        String u = "user" + System.nanoTime();

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(u)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.user.username").value(u))
            .andExpect(jsonPath("$.user.password_hash").doesNotExist());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(u)))
            .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(u, "wrong")))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(u, "secret1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void registerValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"password\":\"secret1\",\"name\":\"Yug\",\"subject\":\"CS\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Username must be at least 3 characters."));
    }
}
