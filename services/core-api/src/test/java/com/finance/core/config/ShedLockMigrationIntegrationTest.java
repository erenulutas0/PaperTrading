package com.finance.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ShedLockMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shedLockTableShouldExist() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'shedlock'
                """, Integer.class);

        assertEquals(1, tableCount);
    }

    @Test
    void shedLockTableShouldHaveExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'shedlock'
                ORDER BY ordinal_position
                """, String.class);

        assertEquals(List.of("name", "lock_until", "locked_at", "locked_by"), columns);
    }
}
