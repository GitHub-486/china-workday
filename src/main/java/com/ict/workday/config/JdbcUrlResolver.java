package com.ict.workday.config;

import javax.sql.DataSource;
import java.sql.SQLException;

public class JdbcUrlResolver {

    public enum DatabaseType {
        POSTGRESQL, H2, MYSQL, UNSUPPORTED
    }

    public static DatabaseType resolve(DataSource dataSource) {
        return resolveFromUrl(getJdbcUrl(dataSource));
    }

    public static DatabaseType resolveFromUrl(String url) {
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
