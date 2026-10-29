# Starter 重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 china-workday 重构为可被外部项目以 Maven 依赖方式引用的 Starter 包，引用方只需配置 DataSource 和添加 `@EnableChinaWorkday` 注解，即可 `@Autowired` 使用 `WorkdayJudgeService`。

**架构：** 保持单模块、不引入 ORM。按 DataSource JDBC URL 前缀自动分发到不同方言的 Repository 实现（PostgreSQL / H2 / MySQL），AutoConfiguration 自动装配 Bean 和初始化建表 DDL。

**Tech Stack:** Spring Boot 3.2.5, Java 17, JdbcTemplate, PostgreSQL / H2 / MySQL

---

### Task 1: 提取 common JDBC URL 检测工具方法

**Files:**
- Create: `src/main/java/com/ict/workday/config/JdbcUrlResolver.java`
- Test: `src/test/java/com/ict/workday/config/JdbcUrlResolverTest.java`

- [ ] **Step 1: 创建 JdbcUrlResolver**

```java
package com.ict.workday.config;

import javax.sql.DataSource;
import java.sql.SQLException;

public class JdbcUrlResolver {

    public enum DatabaseType {
        POSTGRESQL, H2, MYSQL, UNSUPPORTED
    }

    public static DatabaseType resolve(DataSource dataSource) {
        String url = getJdbcUrl(dataSource);
        if (url == null) return DatabaseType.UNSUPPORTED;
        if (url.contains(":postgresql:")) return DatabaseType.POSTGRESQL;
        if (url.contains(":h2:"))         return DatabaseType.H2;
        if (url.contains(":mysql:"))      return DatabaseType.MYSQL;
        if (url.contains(":mariadb:"))    return DatabaseType.MYSQL;
        return DatabaseType.UNSUPPORTED;
    }

    public static String getJdbcUrl(DataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot resolve DataSource JDBC URL", e);
        }
    }
}
```

- [ ] **Step 2: Create test JdbcUrlResolverTest.java at src/test/java/com/ict/workday/config/**

```java
package com.ict.workday.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcUrlResolverTest {

    @Test
    void resolve_postgresql() {
        try (var ds = new HikariDataSource(new HikariConfig() {{
            setJdbcUrl("jdbc:postgresql://localhost:5432/test");
            setUsername("sa");
            setPassword("");
        }})) {
            assertEquals(JdbcUrlResolver.DatabaseType.POSTGRESQL, JdbcUrlResolver.resolve(ds));
        }
    }

    @Test
    void resolve_h2() {
        try (var ds = new HikariDataSource(new HikariConfig() {{
            setJdbcUrl("jdbc:h2:mem:test");
            setUsername("sa");
            setPassword("");
        }})) {
            assertEquals(JdbcUrlResolver.DatabaseType.H2, JdbcUrlResolver.resolve(ds));
        }
    }

    @Test
    void resolve_mysql() {
        try (var ds = new HikariDataSource(new HikariConfig() {{
            setJdbcUrl("jdbc:mysql://localhost:3306/test");
            setUsername("root");
            setPassword("");
        }})) {
            assertEquals(JdbcUrlResolver.DatabaseType.MYSQL, JdbcUrlResolver.resolve(ds));
        }
    }

    @Test
    void resolve_unsupported() {
        try (var ds = new HikariDataSource(new HikariConfig() {{
            setJdbcUrl("jdbc:sqlserver://localhost:1433");
            setUsername("sa");
            setPassword("");
        }})) {
            assertEquals(JdbcUrlResolver.DatabaseType.UNSUPPORTED, JdbcUrlResolver.resolve(ds));
        }
    }
}
```

- [ ] **Step 3: Run test**

Run: `mvn test -Dtest=JdbcUrlResolverTest -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ict/workday/config/JdbcUrlResolver.java src/test/java/com/ict/workday/config/JdbcUrlResolverTest.java
git commit -m "feat: add JdbcUrlResolver for DataSource type detection"
```

---

### Task 2: 创建 H2/MySQL 方言的 holiday Repository + schema 文件

**Files:**
- Create: `src/main/java/com/ict/workday/repository/HolidayH2Repository.java`
- Create: `src/main/java/com/ict/workday/repository/HolidayMysqlRepository.java`
- Create: `src/main/resources/schema-h2.sql`
- Create: `src/main/resources/schema-mysql.sql`
- Test: `src/test/java/com/ict/workday/repository/HolidayH2RepositoryTest.java`

- [ ] **Step 1: 创建 schema-h2.sql**

```sql
CREATE TABLE IF NOT EXISTS holiday (
    id           IDENTITY PRIMARY KEY,
    holiday_date DATE        NOT NULL UNIQUE,
    holiday_name VARCHAR(64),
    day_type     VARCHAR(16) NOT NULL,
    year         INT         NOT NULL,
    source       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_holiday_year  ON holiday (year);
CREATE INDEX IF NOT EXISTS idx_holiday_date  ON holiday (holiday_date);

CREATE TABLE IF NOT EXISTS crawl_url (
    id           IDENTITY PRIMARY KEY,
    url          VARCHAR(512) NOT NULL UNIQUE,
    year         INT,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: 创建 schema-mysql.sql**

```sql
CREATE TABLE IF NOT EXISTS holiday (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE        NOT NULL UNIQUE,
    holiday_name VARCHAR(64),
    day_type     VARCHAR(16) NOT NULL,
    year         INT         NOT NULL,
    source       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_holiday_year (year),
    INDEX idx_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_url (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    url          VARCHAR(512) NOT NULL UNIQUE,
    year         INT,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 3: 创建 HolidayH2Repository**

```java
package com.ict.workday.repository;

import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HolidayH2Repository implements HolidayRepository {

    private static final Logger log = LoggerFactory.getLogger(HolidayH2Repository.class);
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<HolidayRecord> ROW_MAPPER = (rs, rowNum) -> {
        HolidayRecord r = new HolidayRecord();
        r.setId(rs.getLong("id"));
        r.setHolidayDate(rs.getObject("holiday_date", LocalDate.class));
        r.setHolidayName(rs.getString("holiday_name"));
        r.setDayType(DayType.valueOf(rs.getString("day_type")));
        r.setYear(rs.getInt("year"));
        r.setSource(rs.getString("source"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    };

    public HolidayH2Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(HolidayRecord record) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM holiday WHERE holiday_date = ?", Integer.class, record.getHolidayDate());
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE holiday SET holiday_name = ?, day_type = ?, year = ?, source = ?, created_at = ? WHERE holiday_date = ?",
                    record.getHolidayName(), record.getDayType().name(),
                    record.getYear(), record.getSource(), Timestamp.valueOf(record.getCreatedAt().withNano(0)),
                    record.getHolidayDate());
        } else {
            jdbcTemplate.update(
                    "INSERT INTO holiday (holiday_date, holiday_name, day_type, year, source, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    record.getHolidayDate(), record.getHolidayName(), record.getDayType().name(),
                    record.getYear(), record.getSource(), Timestamp.valueOf(record.getCreatedAt().withNano(0)));
        }
    }

    @Override
    public int upsertAll(List<HolidayRecord> records) {
        if (records == null || records.isEmpty()) return 0;
        int count = 0;
        for (HolidayRecord r : records) { upsert(r); count++; }
        return count;
    }

    @Override
    public Optional<HolidayRecord> findByDate(LocalDate date) {
        List<HolidayRecord> list = jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date = ?", ROW_MAPPER, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<HolidayRecord> findByMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate next = start.plusMonths(1);
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date >= ? AND holiday_date < ? ORDER BY holiday_date",
                ROW_MAPPER, start, next);
    }

    @Override
    public List<HolidayRecord> findByYear(int year) {
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE year = ? ORDER BY holiday_date", ROW_MAPPER, year);
    }

    @Override
    public long countBySource(String source) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM holiday WHERE source = ?", Long.class, source);
        return c == null ? 0L : c;
    }

    @Override
    public int deleteByYear(int year) {
        return jdbcTemplate.update("DELETE FROM holiday WHERE year = ?", year);
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM holiday");
    }
}
```

- [ ] **Step 4: 创建 HolidayMysqlRepository**

```java
package com.ict.workday.repository;

import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HolidayMysqlRepository implements HolidayRepository {

    private static final Logger log = LoggerFactory.getLogger(HolidayMysqlRepository.class);
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<HolidayRecord> ROW_MAPPER = (rs, rowNum) -> {
        HolidayRecord r = new HolidayRecord();
        r.setId(rs.getLong("id"));
        r.setHolidayDate(rs.getObject("holiday_date", LocalDate.class));
        r.setHolidayName(rs.getString("holiday_name"));
        r.setDayType(DayType.valueOf(rs.getString("day_type")));
        r.setYear(rs.getInt("year"));
        r.setSource(rs.getString("source"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    };

    public HolidayMysqlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(HolidayRecord record) {
        jdbcTemplate.update(
                "INSERT INTO holiday (holiday_date, holiday_name, day_type, year, source, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE holiday_name = VALUES(holiday_name), day_type = VALUES(day_type), "
                + "year = VALUES(year), source = VALUES(source), created_at = VALUES(created_at)",
                record.getHolidayDate(), record.getHolidayName(), record.getDayType().name(),
                record.getYear(), record.getSource(), Timestamp.valueOf(record.getCreatedAt().withNano(0)));
    }

    @Override
    public int upsertAll(List<HolidayRecord> records) {
        if (records == null || records.isEmpty()) return 0;
        int count = 0;
        for (HolidayRecord r : records) { upsert(r); count++; }
        return count;
    }

    @Override
    public Optional<HolidayRecord> findByDate(LocalDate date) {
        List<HolidayRecord> list = jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date = ?", ROW_MAPPER, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<HolidayRecord> findByMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate next = start.plusMonths(1);
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE holiday_date >= ? AND holiday_date < ? ORDER BY holiday_date",
                ROW_MAPPER, start, next);
    }

    @Override
    public List<HolidayRecord> findByYear(int year) {
        return jdbcTemplate.query(
                "SELECT * FROM holiday WHERE year = ? ORDER BY holiday_date", ROW_MAPPER, year);
    }

    @Override
    public long countBySource(String source) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM holiday WHERE source = ?", Long.class, source);
        return c == null ? 0L : c;
    }

    @Override
    public int deleteByYear(int year) {
        return jdbcTemplate.update("DELETE FROM holiday WHERE year = ?", year);
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM holiday");
    }
}
```

- [ ] **Step 5: 创建 HolidayH2RepositoryTest**

```java
package com.ict.workday.repository;

import com.ict.workday.domain.DayType;
import com.ict.workday.domain.HolidayRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class HolidayH2RepositoryTest {

    private HolidayH2Repository repository;

    @BeforeEach
    void setUp() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-h2.sql")
                .build();
        repository = new HolidayH2Repository(new JdbcTemplate(ds));
    }

    @Test
    void upsertAndFindByDate() {
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        Optional<HolidayRecord> found = repository.findByDate(LocalDate.of(2026, 1, 1));
        assertTrue(found.isPresent());
        assertEquals("元旦", found.get().getHolidayName());
    }

    @Test
    void upsert_idempotent() {
        repository.upsert(rec(2026, 1, 1, "元旦", DayType.HOLIDAY));
        repository.upsert(rec(2026, 1, 1, "元旦更新", DayType.WORKDAY));
        Optional<HolidayRecord> found = repository.findByDate(LocalDate.of(2026, 1, 1));
        assertEquals("元旦更新", found.get().getHolidayName());
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
```

- [ ] **Step 6: Run test**

Run: `mvn test -Dtest=HolidayH2RepositoryTest -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ict/workday/repository/HolidayH2Repository.java src/main/java/com/ict/workday/repository/HolidayMysqlRepository.java src/main/resources/schema-h2.sql src/main/resources/schema-mysql.sql src/test/java/com/ict/workday/repository/HolidayH2RepositoryTest.java
git commit -m "feat: add H2/MySQL holiday repository and schema files"
```

---

### Task 3: 创建 H2/MySQL 方言的 crawl_url Repository

**Files:**
- Create: `src/main/java/com/ict/workday/repository/CrawlUrlH2Repository.java`
- Create: `src/main/java/com/ict/workday/repository/CrawlUrlMysqlRepository.java`

- [ ] **Step 1: 创建 CrawlUrlH2Repository**

```java
package com.ict.workday.repository;

import org.springframework.jdbc.core.JdbcTemplate;

public class CrawlUrlH2Repository implements CrawlUrlRepository {

    private final JdbcTemplate jdbcTemplate;

    public CrawlUrlH2Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String url, int year) {
        try {
            jdbcTemplate.update("INSERT INTO crawl_url (url, year) VALUES (?, ?)", url, year);
        } catch (Exception e) {
            // unique constraint: idempotent ignore
        }
    }

    @Override
    public boolean exists(String url) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crawl_url WHERE url = ?", Integer.class, url);
        return c != null && c > 0;
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM crawl_url");
    }
}
```

- [ ] **Step 2: 创建 CrawlUrlMysqlRepository**

```java
package com.ict.workday.repository;

import org.springframework.jdbc.core.JdbcTemplate;

public class CrawlUrlMysqlRepository implements CrawlUrlRepository {

    private final JdbcTemplate jdbcTemplate;

    public CrawlUrlMysqlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String url, int year) {
        jdbcTemplate.update("INSERT IGNORE INTO crawl_url (url, year) VALUES (?, ?)", url, year);
    }

    @Override
    public boolean exists(String url) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crawl_url WHERE url = ?", Integer.class, url);
        return c != null && c > 0;
    }

    @Override
    public void truncate() {
        jdbcTemplate.update("DELETE FROM crawl_url");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/ict/workday/repository/CrawlUrlH2Repository.java src/main/java/com/ict/workday/repository/CrawlUrlMysqlRepository.java
git commit -m "feat: add H2/MySQL crawl_url repository implementations"
```

---

### Task 4: 重命名 PG Repository + schema.sql

**Files:**
- Create: `src/main/java/com/ict/workday/repository/HolidayPostgresRepository.java` (copy from HolidayJdbcRepository, remove @Repository)
- Create: `src/main/java/com/ict/workday/repository/CrawlUrlPostgresRepository.java` (copy from CrawlUrlJdbcRepository, remove @Repository)
- Delete: `src/main/java/com/ict/workday/repository/HolidayJdbcRepository.java`
- Delete: `src/main/java/com/ict/workday/repository/CrawlUrlJdbcRepository.java`
- Create: `src/main/resources/schema-postgres.sql` (copy from schema.sql)
- Delete: `src/main/resources/schema.sql`
- Modify: all files referencing old class names

- [ ] **Step 1: Find all references to old class names**

Run: Select-String -Path src/ -Pattern "HolidayJdbcRepository|CrawlUrlJdbcRepository" -Recurse

Expected: Lists all files that reference old class names.

- [ ] **Step 2: Create HolidayPostgresRepository** (same content as HolidayJdbcRepository but class renamed and @Repository removed)

- [ ] **Step 3: Create CrawlUrlPostgresRepository** (same content as CrawlUrlJdbcRepository but class renamed and @Repository removed)

- [ ] **Step 4: Create schema-postgres.sql** (same content as existing schema.sql with BIGSERIAL)

- [ ] **Step 5: Delete HolidayJdbcRepository.java, CrawlUrlJdbcRepository.java, schema.sql**

- [ ] **Step 6: Replace all references** — HolidayJdbcRepository -> HolidayPostgresRepository, CrawlUrlJdbcRepository -> CrawlUrlPostgresRepository

- [ ] **Step 7: Compile verify**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename HolidayJdbcRepository->HolidayPostgresRepository, schema.sql->schema-postgres.sql"
```

---

### Task 5: 创建 HolidayRepositoryFactory

**Files:**
- Create: `src/main/java/com/ict/workday/config/HolidayRepositoryFactory.java`
- Test: `src/test/java/com/ict/workday/config/HolidayRepositoryFactoryTest.java`

- [ ] **Step 1: 创建 HolidayRepositoryFactory**

```java
package com.ict.workday.config;

import com.ict.workday.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

public class HolidayRepositoryFactory {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public HolidayRepositoryFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public HolidayRepository createHolidayRepository() {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL: return new HolidayPostgresRepository(jdbcTemplate);
            case H2:         return new HolidayH2Repository(jdbcTemplate);
            case MYSQL:      return new HolidayMysqlRepository(jdbcTemplate);
            default:
                throw new IllegalStateException("Unsupported database type. Supported: PostgreSQL / H2 / MySQL.");
        }
    }

    public CrawlUrlRepository createCrawlUrlRepository() {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL: return new CrawlUrlPostgresRepository(jdbcTemplate);
            case H2:         return new CrawlUrlH2Repository(jdbcTemplate);
            case MYSQL:      return new CrawlUrlMysqlRepository(jdbcTemplate);
            default:
                throw new IllegalStateException("Unsupported database type. Supported: PostgreSQL / H2 / MySQL.");
        }
    }
}
```

- [ ] **Step 2: Create test at src/test/java/com/ict/workday/config/HolidayRepositoryFactoryTest.java**

```java
package com.ict.workday.config;

import com.ict.workday.repository.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HolidayRepositoryFactoryTest {

    @Test
    void createHolidayRepository_postgresql() {
        try (var ds = hikari("jdbc:postgresql://localhost:5432/test")) {
            assertInstanceOf(HolidayPostgresRepository.class,
                    new HolidayRepositoryFactory(ds).createHolidayRepository());
        }
    }

    @Test
    void createHolidayRepository_h2() {
        try (var ds = hikari("jdbc:h2:mem:test")) {
            assertInstanceOf(HolidayH2Repository.class,
                    new HolidayRepositoryFactory(ds).createHolidayRepository());
        }
    }

    @Test
    void createHolidayRepository_mysql() {
        try (var ds = hikari("jdbc:mysql://localhost:3306/test")) {
            assertInstanceOf(HolidayMysqlRepository.class,
                    new HolidayRepositoryFactory(ds).createHolidayRepository());
        }
    }

    @Test
    void createHolidayRepository_unsupported() {
        try (var ds = hikari("jdbc:sqlserver://localhost:1433")) {
            assertThrows(IllegalStateException.class,
                    () -> new HolidayRepositoryFactory(ds).createHolidayRepository());
        }
    }

    private HikariDataSource hikari(String url) {
        return new HikariDataSource(new HikariConfig() {{
            setJdbcUrl(url); setUsername("sa"); setPassword("");
        }});
    }
}
```

- [ ] **Step 3: Run test**

Run: `mvn test -Dtest=HolidayRepositoryFactoryTest -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/ict/workday/config/HolidayRepositoryFactory.java src/test/java/com/ict/workday/config/HolidayRepositoryFactoryTest.java
git commit -m "feat: add HolidayRepositoryFactory for DataSource-aware repository selection"
```

---

### Task 6: 创建 AutoConfiguration + @EnableChinaWorkday

**Files:**
- Create: `src/main/java/com/ict/workday/config/ChinaWorkdayAutoConfiguration.java`
- Create: `src/main/java/com/ict/workday/config/EnableChinaWorkday.java`
- Create: `src/main/java/com/ict/workday/config/ConditionalOnSchemaInit.java`
- Create: `src/main/java/com/ict/workday/config/ConditionalOnCrawlEnabled.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建 @EnableChinaWorkday**

```java
package com.ict.workday.config;

import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ChinaWorkdayAutoConfiguration.class)
public @interface EnableChinaWorkday {}
```

- [ ] **Step 2: 创建 @ConditionalOnSchemaInit**

```java
package com.ict.workday.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "china-workday", name = "schema-init", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnSchemaInit {}
```

- [ ] **Step 3: 创建 @ConditionalOnCrawlEnabled**

```java
package com.ict.workday.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "china-workday.crawl", name = "enabled", havingValue = "true", matchIfMissing = false)
public @interface ConditionalOnCrawlEnabled {}
```

- [ ] **Step 4: 创建 ChinaWorkdayAutoConfiguration**

```java
package com.ict.workday.config;

import com.ict.workday.repository.*;
import com.ict.workday.service.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(HolidayRepository.class)
@EnableConfigurationProperties({AppProperties.class, CrawlProperties.class})
public class ChinaWorkdayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HolidayRepository.class)
    public HolidayRepository holidayRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createHolidayRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CrawlUrlRepository.class)
    @ConditionalOnCrawlEnabled
    public CrawlUrlRepository crawlUrlRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createCrawlUrlRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkdayJudgeService.class)
    public WorkdayJudgeService workdayJudgeService(HolidayRepository repository) {
        return new WorkdayJudgeService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(HolidayService.class)
    public HolidayService holidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        return new HolidayService(repository, workdayJudgeService);
    }

    @Bean
    @ConditionalOnMissingBean(JsonExportService.class)
    public JsonExportService jsonExportService(HolidayRepository repository) {
        return new JsonExportService(repository);
    }

    @Bean
    @ConditionalOnSchemaInit
    public DataSourceInitializer chinaWorkdaySchemaInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setEnabled(true);

        String script = resolveSchemaScript(dataSource);
        if (script == null) return initializer;

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(script));
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    private String resolveSchemaScript(DataSource dataSource) {
        switch (JdbcUrlResolver.resolve(dataSource)) {
            case POSTGRESQL: return "schema-postgres.sql";
            case H2:         return "schema-h2.sql";
            case MYSQL:      return "schema-mysql.sql";
            default:         return null;
        }
    }
}
```

- [ ] **Step 5: 创建 AutoConfiguration.imports**

Create file `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.ict.workday.config.ChinaWorkdayAutoConfiguration
```

- [ ] **Step 6: Compile verify**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/ict/workday/config/ChinaWorkdayAutoConfiguration.java src/main/java/com/ict/workday/config/EnableChinaWorkday.java src/main/java/com/ict/workday/config/ConditionalOnSchemaInit.java src/main/java/com/ict/workday/config/ConditionalOnCrawlEnabled.java src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git commit -m "feat: add @EnableChinaWorkday auto-configuration with schema initialization"
```

---

### Task 7: 运行全部测试验证

- [ ] **Step 1: Run all tests**

Run: `mvn test`
Expected: All tests pass (JdbcUrlResolverTest, HolidayH2RepositoryTest, HolidayRepositoryFactoryTest, plus all existing tests)
