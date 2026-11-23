package ru.mephi.learnflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PostgresConnectionTest {

    @Test
    void connectsToPostgres(@Autowired JdbcTemplate jdbc) {
        String version = jdbc.queryForObject("select version()", String.class);

        assertThat(version).startsWith("PostgreSQL 16.");
    }
}
