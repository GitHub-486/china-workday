package com.ict.workday.config;

import com.ict.workday.repository.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HolidayRepositoryFactoryTest {

    @Test
    void resolveUrl_detects_postgresql() {
        assertEquals(JdbcUrlResolver.DatabaseType.POSTGRESQL,
                JdbcUrlResolver.resolveFromUrl("jdbc:postgresql://localhost:5432/test"));
    }

    @Test
    void resolveUrl_detects_h2() {
        assertEquals(JdbcUrlResolver.DatabaseType.H2,
                JdbcUrlResolver.resolveFromUrl("jdbc:h2:mem:test"));
    }

    @Test
    void resolveUrl_detects_mysql() {
        assertEquals(JdbcUrlResolver.DatabaseType.MYSQL,
                JdbcUrlResolver.resolveFromUrl("jdbc:mysql://localhost:3306/test"));
    }

    @Test
    void resolveUrl_detects_mariadb_as_mysql() {
        assertEquals(JdbcUrlResolver.DatabaseType.MYSQL,
                JdbcUrlResolver.resolveFromUrl("jdbc:mariadb://localhost:3306/test"));
    }

    @Test
    void resolveUrl_unsupported() {
        assertEquals(JdbcUrlResolver.DatabaseType.UNSUPPORTED,
                JdbcUrlResolver.resolveFromUrl("jdbc:sqlserver://localhost:1433"));
    }

    @Test
    void resolveUrl_null() {
        assertEquals(JdbcUrlResolver.DatabaseType.UNSUPPORTED,
                JdbcUrlResolver.resolveFromUrl(null));
    }

    @Test
    void holidayRepositoryTypeByDatabaseType() {
        assertEquals(HolidayPostgresRepository.class,
                repoByType(JdbcUrlResolver.DatabaseType.POSTGRESQL).getClass());
        assertEquals(HolidayH2Repository.class,
                repoByType(JdbcUrlResolver.DatabaseType.H2).getClass());
        assertEquals(HolidayMysqlRepository.class,
                repoByType(JdbcUrlResolver.DatabaseType.MYSQL).getClass());
        assertThrows(IllegalStateException.class,
                () -> repoByType(JdbcUrlResolver.DatabaseType.UNSUPPORTED));
    }

    private HolidayRepository repoByType(JdbcUrlResolver.DatabaseType type) {
        switch (type) {
            case POSTGRESQL: return new HolidayPostgresRepository(null);
            case H2:         return new HolidayH2Repository(null);
            case MYSQL:      return new HolidayMysqlRepository(null);
            default:
                throw new IllegalStateException("不支持的数据库类型。");
        }
    }
}
