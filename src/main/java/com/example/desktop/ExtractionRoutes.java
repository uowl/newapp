package com.example.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RouteRegistrar for the Extraction module.
 * Registers vendor lookup and SQL Server database discovery.
 */
public class ExtractionRoutes implements RouteRegistrar {

    private static final String VENDORS_RESOURCE_PATH = "/vendors.json";
    private static final String VENDOR_CONFIGS_RESOURCE_PATH = "/vendor-configs.json";
    private static final Set<String> EXCLUDED_DATABASES = Set.of(
            "master", "tempdb", "medispan", "multum", "msdb", "ecwmaster", "model"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ExtractionRoutes() {}

    @Override
    public void register(JavalinConfig config) {
        config.routes.get("/api/vendors", ctx -> ctx.json(vendorNames()));
        config.routes.get("/api/vendors/{name}/config", ctx -> {
            String name = ctx.pathParam("name");
            ctx.json(vendorConfig(name));
        });
        config.routes.post("/api/sqlserver/databases", ctx -> {
            try {
                SqlServerConnectionRequest request = ctx.bodyAsClass(SqlServerConnectionRequest.class);
                ctx.json(loadSqlServerDatabases(request));
            } catch (IllegalArgumentException ex) {
                ctx.status(400).json(new ErrorResponse(ex.getMessage()));
            } catch (IllegalStateException ex) {
                ctx.status(502).json(new ErrorResponse(ex.getMessage()));
            }
        });
    }

    private Object vendorConfig(String name) {
        try (InputStream input = ExtractionRoutes.class.getResourceAsStream(VENDOR_CONFIGS_RESOURCE_PATH)) {
            if (input == null) return new java.util.HashMap<>();
            var configs = OBJECT_MAPPER.readValue(input, new TypeReference<java.util.Map<String, Object>>() {});
            return configs.getOrDefault(name, new java.util.HashMap<>());
        } catch (IOException ex) {
            System.err.println("Unable to read vendor config resource: " + ex.getMessage());
            return new java.util.HashMap<>();
        }
    }

    private List<String> vendorNames() {
        try (InputStream input = ExtractionRoutes.class.getResourceAsStream(VENDORS_RESOURCE_PATH)) {
            if (input == null) {
                System.err.println("Vendor resource not found: " + VENDORS_RESOURCE_PATH);
                return List.of();
            }
            List<String> items = OBJECT_MAPPER.readValue(input, new TypeReference<List<String>>() {});
            return items.stream()
                    .map(name -> name == null ? "" : name.trim())
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .toList();
        } catch (IOException ex) {
            System.err.println("Unable to read vendor resource: " + ex.getMessage());
            return List.of();
        }
    }

    private List<String> loadSqlServerDatabases(SqlServerConnectionRequest request) {
        String hostname = trimToEmpty(request.hostname());
        if (hostname.isBlank()) throw new IllegalArgumentException("Hostname is required");
        String username = trimToEmpty(request.username());
        if (username.isBlank()) throw new IllegalArgumentException("Username is required");

        int sqlPort = parseSqlServerPort(request.port());
        try (Connection conn = DataSourceManager.getConnection(hostname, String.valueOf(sqlPort), username, trimToEmpty(request.password()), null);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT name FROM sys.databases WHERE state_desc = 'ONLINE' ORDER BY name ASC");
             ResultSet rs = stmt.executeQuery()) {

            List<String> databases = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("name");
                if (name == null) continue;
                String normalized = name.trim().toLowerCase(Locale.ROOT);
                if (!EXCLUDED_DATABASES.contains(normalized)) databases.add(name);
            }
            return databases;
        } catch (SQLException ex) {
            throw new IllegalStateException("SQL Server connection failed: " + ex.getMessage(), ex);
        }
    }

    private int parseSqlServerPort(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isBlank()) return 1433;
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 1 || parsed > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port must be a valid number", ex);
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record SqlServerConnectionRequest(String hostname, String port, String username, String password) {}
    private record ErrorResponse(String error) {}
}
