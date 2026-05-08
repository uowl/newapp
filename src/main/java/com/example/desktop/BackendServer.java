package com.example.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public class BackendServer {
    private static final String VENDORS_RESOURCE_PATH = "/vendors.json";
    private static final Set<String> EXCLUDED_DATABASES = Set.of(
            "master",
            "tempdb",
            "medispan",
            "multum",
            "msdb",
            "ecwmaster",
            "model"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Javalin app;
    private int port;
    private final Path logDirectory = resolveLogDirectory();

    public void start() {
        port = findAvailablePort();
        app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.routes.get("/", ctx -> ctx.redirect("/index.html"));
            config.routes.get("/api/health", ctx -> ctx.json(new HealthResponse("ok")));
            config.routes.get("/api/vendors", ctx -> ctx.json(vendorNames()));
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
            config.routes.post("/api/layout-metrics", ctx -> {
                String payload = ctx.body();
                appendLayoutMetric(payload);
                ctx.status(204);
            });
        });
        app.start(port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    public int port() {
        return port;
    }

    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to acquire a free TCP port", ex);
        }
    }

    private List<String> vendorNames() {
        try (InputStream input = BackendServer.class.getResourceAsStream(VENDORS_RESOURCE_PATH)) {
            if (input == null) {
                System.err.println("Vendor resource not found: " + VENDORS_RESOURCE_PATH);
                return List.of();
            }
            List<String> items = OBJECT_MAPPER.readValue(input, new TypeReference<List<String>>() {
            });
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

    private void appendLayoutMetric(String payload) {
        try {
            Files.createDirectories(logDirectory);
            Path logFile = logDirectory.resolve("layout-metrics-" + LocalDate.now() + ".log");
            String entry = LocalDateTime.now() + " " + payload + System.lineSeparator();
            Files.writeString(
                    logFile,
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            System.err.println("Failed to write layout metrics log: " + ex.getMessage());
        }
    }

    private Path resolveLogDirectory() {
        String configured = System.getProperty("app.logs.dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("logs");
    }

    private List<String> loadSqlServerDatabases(SqlServerConnectionRequest request) {
        String hostname = trimToEmpty(request.hostname());
        if (hostname.isBlank()) {
            throw new IllegalArgumentException("Hostname is required");
        }

        String username = trimToEmpty(request.username());
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        int sqlPort = parseSqlServerPort(request.port());
        String jdbcUrl = "jdbc:sqlserver://" + hostname + ":" + sqlPort
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=8";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, trimToEmpty(request.password()));
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name FROM sys.databases WHERE state_desc = 'ONLINE' ORDER BY name ASC");
             ResultSet resultSet = statement.executeQuery()) {

            List<String> databases = new ArrayList<>();
            while (resultSet.next()) {
                String databaseName = resultSet.getString("name");
                if (databaseName == null) {
                    continue;
                }
                String normalized = databaseName.trim().toLowerCase(Locale.ROOT);
                if (!EXCLUDED_DATABASES.contains(normalized)) {
                    databases.add(databaseName);
                }
            }
            return databases;
        } catch (SQLException ex) {
            throw new IllegalStateException("SQL Server connection failed: " + ex.getMessage(), ex);
        }
    }

    private int parseSqlServerPort(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isBlank()) {
            return 1433;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 1 || parsed > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port must be a valid number", ex);
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record HealthResponse(String status) {
    }

    private record SqlServerConnectionRequest(String hostname, String port, String username, String password) {
    }

    private record ErrorResponse(String error) {
    }
}
