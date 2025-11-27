package com.example.authapp.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        type VARCHAR(32) NOT NULL,
                        message VARCHAR(255) NOT NULL,
                        target_id BIGINT NOT NULL,
                        is_read BIT(1) NOT NULL DEFAULT 0,
                        created_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("Verified notifications table existence.");
        } catch (Exception ex) {
            log.error("Failed to ensure notifications table exists", ex);
        }
    }
}
