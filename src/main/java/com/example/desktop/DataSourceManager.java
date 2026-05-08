package com.example.desktop;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages database connection pools using HikariCP.
 * In a multi-tenant or multi-server environment like this EMR workspace,
 * it caches pools by connection string to avoid re-initializing for every request.
 */
public class DataSourceManager {

    private static final Map<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();
    private static final int MAX_POOL_SIZE = 10;
    private static final long IDLE_TIMEOUT = 300000; // 5 minutes

    public static Connection getConnection(String hostname, String port, String username, String password, String database) throws SQLException {
        String key = String.format("%s:%s:%s:%s", hostname, port, username, database);
        
        HikariDataSource ds = POOLS.computeIfAbsent(key, k -> createDataSource(hostname, port, username, password, database));
        
        return ds.getConnection();
    }

    private static HikariDataSource createDataSource(String hostname, String port, String username, String password, String database) {
        HikariConfig config = new HikariConfig();
        
        // MSSQL JDBC URL
        String jdbcUrl = String.format("jdbc:sqlserver://%s:%s;encrypt=true;trustServerCertificate=true;loginTimeout=10",
                hostname, (port == null || port.isBlank()) ? "1433" : port);
        
        if (database != null && !database.isBlank()) {
            jdbcUrl += ";databaseName=" + database;
        }

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Hikari Specifics
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setPoolName("HikariPool-" + hostname);
        
        // Optimization for SQL Server
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        return new HikariDataSource(config);
    }

    public static void shutdownAll() {
        POOLS.values().forEach(HikariDataSource::close);
        POOLS.clear();
    }
}
