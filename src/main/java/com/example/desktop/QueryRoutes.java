package com.example.desktop;

import io.javalin.config.JavalinConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryRoutes implements RouteRegistrar {

    private static final int MAX_ROWS = 500;

    @Override
    public void register(JavalinConfig config) {
        config.routes.post("/api/sqlserver/query", ctx -> {
            try {
                QueryRequest request = ctx.bodyAsClass(QueryRequest.class);
                ctx.json(executeQuery(request));
            } catch (Exception ex) {
                ctx.status(400).json(Map.of("error", ex.getMessage()));
            }
        });
    }

    private QueryResponse executeQuery(QueryRequest request) throws SQLException {
        try (Connection conn = DataSourceManager.getConnection(request.hostname, request.port, request.username, request.password, request.database);
             Statement stmt = conn.createStatement()) {
            
            stmt.setMaxRows(MAX_ROWS);
            boolean isResultSet = stmt.execute(request.sql);

            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    return processResultSet(rs);
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                return new QueryResponse(List.of(), List.of(), updateCount + " rows affected.");
            }
        }
    }

    private QueryResponse processResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }

        return new QueryResponse(columns, rows, rows.size() + " rows returned.");
    }

    private static class QueryRequest {
        public String hostname;
        public String port;
        public String username;
        public String password;
        public String database;
        public String sql;
    }

    private record QueryResponse(List<String> columns, List<Map<String, Object>> rows, String message) {}
}
