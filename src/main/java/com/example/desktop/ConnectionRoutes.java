package com.example.desktop;

import io.javalin.config.JavalinConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConnectionRoutes implements RouteRegistrar {

    private final ConnectionService service = new ConnectionService();

    @Override
    public void register(JavalinConfig config) {
        
        // List all connections (decrypted for the UI, or at least mask passwords)
        config.routes.get("/api/connections", ctx -> {
            List<ConnectionDTO> dtos = service.getConnections().stream()
                    .map(c -> new ConnectionDTO(
                            c.id(), c.name(), c.hostname(), c.port(), 
                            c.username(), service.decrypt(c.encryptedPassword()),
                            c.defaultDatabase()
                    ))
                    .collect(Collectors.toList());
            ctx.json(dtos);
        });

        // Save or update a connection
        config.routes.post("/api/connections", ctx -> {
            ConnectionDTO dto = ctx.bodyAsClass(ConnectionDTO.class);
            List<ConnectionService.SavedConnection> connections = service.getConnections();
            
            String id = dto.id != null ? dto.id : UUID.randomUUID().toString();
            ConnectionService.SavedConnection newConn = new ConnectionService.SavedConnection(
                    id, dto.name, dto.hostname, dto.port, 
                    dto.username, service.encrypt(dto.password),
                    dto.defaultDatabase
            );

            // Remove existing if updating
            connections.removeIf(c -> c.id().equals(id));
            connections.add(newConn);
            service.saveConnections(connections);
            ctx.json(newConn);
        });

        // Delete a connection
        config.routes.delete("/api/connections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            List<ConnectionService.SavedConnection> connections = service.getConnections();
            connections.removeIf(c -> c.id().equals(id));
            service.saveConnections(connections);
            ctx.status(204);
        });
    }

    public static class ConnectionDTO {
        public String id;
        public String name;
        public String hostname;
        public String port;
        public String username;
        public String password;
        public String defaultDatabase;

        public ConnectionDTO() {}
        public ConnectionDTO(String id, String name, String hostname, String port, String username, String password, String defaultDatabase) {
            this.id = id;
            this.name = name;
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.password = password;
            this.defaultDatabase = defaultDatabase;
        }
    }
}
