package com.ict.workday.repository;

import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HolidayH2RepositoryTest {

    private HikariDataSource dataSource;
    private HolidayH2Repository repository;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:testdb_repo;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("sql/schema-h2.sql"));
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        try (var conn = dataSource.getConnection()) {
            populator.populate(conn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        repository = new HolidayH2Repository(new JdbcTemplate(dataSource));
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void upsertAndFindByDate() {
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        Optional<HolidayRecord> found = repository.findByDate(LocalDate.of(2026, 1, 1));
        assertTrue(found.isPresent());
        assertEquals("元旦", found.get().getHolidayName());
        assertEquals(DayType.HOLIDAY, found.get().getDayType());
    }

    @Test
    void upsert_idempotent() {
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        repository.upsert(rec(2026, 1, 1, "元旦更新", DayType.WORKDAY));
        Optional<HolidayRecord> found = repository.findByDate(LocalDate.of(2026, 1, 1));
        assertTrue(found.isPresent());
        assertEquals("元旦更新", found.get().getHolidayName());
        assertEquals(DayType.WORKDAY, found.get().getDayType());
    }

    @Test
    void findByYear() {
        repository.upsert(rec(2025, 10, 1, "国庆", DayType.HOLIDAY));
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        assertEquals(1, repository.findByYear(2026).size());
    }

    @Test
    void truncate() {
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        repository.truncate();
        assertTrue(repository.findByYear(2026).isEmpty());
    }

    private HolidayRecord rec(int y, int m, int d, String name, DayType type) {
        HolidayRecord r = new HolidayRecord(LocalDate.of(y, m, d), name, type, y, "test");
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }
}
