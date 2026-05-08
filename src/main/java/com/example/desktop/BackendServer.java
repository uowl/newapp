package com.example.desktop;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.util.List;

/**
 * Core HTTP server. All module-specific routes are registered via RouteRegistrar
 * implementations — BackendServer itself remains stable regardless of which
 * modules are active.
 */
public class BackendServer {

    private Javalin app;
    private int port;

    private final List<RouteRegistrar> registrars;

    public BackendServer(List<RouteRegistrar> registrars) {
        this.registrars = List.copyOf(registrars);
    }

    public void start() {
        // Start on port 0: the OS assigns a free port atomically, eliminating
        // the TOCTOU race of the old find-then-bind two-step approach.
        app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.routes.get("/", ctx -> ctx.redirect("/index.html"));
            config.routes.get("/api/health", ctx -> ctx.json(new HealthResponse("ok")));

            // Let each registered module add its own routes
            for (RouteRegistrar registrar : registrars) {
                registrar.register(config);
            }
        });
        app.start(0);       // bind on ephemeral port
        port = app.port();  // read back the actual assigned port
    }

    public void stop() {
        try {
            if (app != null) {
                app.stop();
            }
        } finally {
            DataSourceManager.shutdownAll();
        }
    }

    public int port() {
        return port;
    }

    private record HealthResponse(String status) {}
}
