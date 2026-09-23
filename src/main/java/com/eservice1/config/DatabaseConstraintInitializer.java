package com.eservice1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConstraintInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConstraintInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseConstraintInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS uk_users_single_owner ON users (role) WHERE role = 'OWNER'"
            );
            log.info("Database constraint uk_users_single_owner verified.");
        } catch (Exception e) {
            log.warn("Database constraint uk_users_single_owner initialization skipped or failed: {}", e.getMessage());
        }
    }
}
