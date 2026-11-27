package ru.mephi.learnflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class ApiTestRequests {

    private ApiTestRequests() {
    }

    static HttpHeaders basic(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(email, password);
        return headers;
    }

    static RequestPostProcessor authenticatedWrite(MockMvc http, ObjectMapper json, String email, String password)
            throws Exception {
        HttpHeaders credentials = basic(email, password);
        MvcResult result = http.perform(get("/api/csrf").headers(credentials))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        JsonNode token = json.readTree(result.getResponse().getContentAsString());
        return request -> {
            request.setCookies(cookie);
            request.addHeader(HttpHeaders.AUTHORIZATION, credentials.getFirst(HttpHeaders.AUTHORIZATION));
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            return request;
        };
    }
}
