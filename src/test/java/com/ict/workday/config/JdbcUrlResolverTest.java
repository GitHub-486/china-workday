package com.ict.workday.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcUrlResolverTest {

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
    void resolveUrl_unsupported() {
        assertEquals(JdbcUrlResolver.DatabaseType.UNSUPPORTED,
                JdbcUrlResolver.resolveFromUrl("jdbc:sqlserver://localhost:1433"));
    }
}
