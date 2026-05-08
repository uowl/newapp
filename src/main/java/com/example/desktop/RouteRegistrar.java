package com.example.desktop;

import io.javalin.config.JavalinConfig;

/**
 * Implemented by each pluggable module to register its own API routes.
 * Add a new implementation and register it in Launcher to add a module
 * without touching BackendServer or any other existing code.
 */
public interface RouteRegistrar {
    void register(JavalinConfig config);
}
