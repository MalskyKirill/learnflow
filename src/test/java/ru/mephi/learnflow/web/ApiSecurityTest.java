package ru.mephi.learnflow.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.mephi.learnflow.entity.Profile;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.ProfileRepository;
import ru.mephi.learnflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApiSecurityTest {

    private static final String EMAIL = "security-test@example.org";
    private static final String PASSWORD = "test-only-password";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private ObjectMapper json;

    private MockMvc http;

    @BeforeEach
    void prepareHttpClient() {
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilters).build();
    }

    @Test
    void courseListIsPublicAndCsrfEndpointRequiresAuthentication() throws Exception {
        http.perform(get("/api/courses")).andExpect(status().isOk());

        http.perform(get("/api/csrf"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"LearnFlow\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void onlyValidCredentialsAuthenticate() throws Exception {
        users.save(new User("Анна", EMAIL, UserRole.STUDENT, passwords.encode(PASSWORD)));
        String legacyEmail = "legacy-security-test@example.org";
        users.save(new User("Иван", legacyEmail, UserRole.STUDENT));

        http.perform(get("/api/csrf").headers(basic(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        http.perform(get("/api/csrf").headers(basic(legacyEmail, PASSWORD)))
                .andExpect(status().isUnauthorized());
        http.perform(get("/api/csrf").headers(basic(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void writeRequestsRequireCsrfCookieAndToken() throws Exception {
        users.save(new User("Мария", EMAIL, UserRole.TEACHER, passwords.encode(PASSWORD)));
        HttpHeaders credentials = basic(EMAIL, PASSWORD);
        MvcResult tokenResponse = http.perform(get("/api/csrf").headers(credentials))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = tokenResponse.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(tokenResponse.getRequest().getSession(false)).isNull();
        JsonNode token = json.readTree(tokenResponse.getResponse().getContentAsString());

        http.perform(post("/api/csrf").headers(credentials).cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        http.perform(post("/api/csrf").headers(credentials).cookie(cookie)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returnsProfileForAuthenticatedUser() throws Exception {
        User student = users.save(new User("Анна", EMAIL, UserRole.STUDENT, passwords.encode(PASSWORD)));
        profiles.save(new Profile(student, "Изучаю JPA", "https://example.org/avatar.png"));

        http.perform(get("/api/me").headers(basic(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(is(student.getId()), Long.class))
                .andExpect(jsonPath("$.name").value("Анна"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.bio").value("Изучаю JPA"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.org/avatar.png"))
                .andExpect(jsonPath("$.passwordHash").doesNotHaveJsonPath());
    }

    @Test
    void profileRequiresAuthenticationAndSupportsMissingOptionalDetails() throws Exception {
        users.save(new User("Мария", EMAIL, UserRole.TEACHER, passwords.encode(PASSWORD)));

        http.perform(get("/api/me")).andExpect(status().isUnauthorized());
        http.perform(get("/api/me").headers(basic(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.bio").value(nullValue()))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
    }

    private static HttpHeaders basic(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(email, password);
        return headers;
    }
}
