# Project Metadata Inspection
_Last updated: 2026-05-07_

## Overview
**EMR Workspace** (`javalin-vue-app`) is a hybrid JavaFX desktop application. A Javalin HTTP server serves a Vue/Vuetify SPA which is loaded in a JavaFX `WebView`. The app uses a **plugin module architecture** — both the frontend and backend are designed so that new feature modules can be added without modifying any core files.

## Technology Stack
- **Backend:** Java 21, Maven, Javalin 7.2.0, SLF4J, MS SQL Server JDBC (`mssql-jdbc 12.8.1.jre11` — jre11 JAR is compatible with Java 21)
- **Frontend:** Vue 3, Vuetify 3, Vite 5, Sass
- **Desktop Container:** JavaFX 21 (WebView wrapper)
- **Packaging:** Windows — PowerShell + `jpackage` + WiX Toolset
- **Build:** `javafx-maven-plugin 0.0.8` (required for Java 21 module system)

---

## Plugin Module Architecture

### Adding a new module (3 steps)

**Frontend** (`frontend/src/main.js`):
```js
import MyModule from "./modules/my-module/MyModule.vue";
registerModule({ id: "my-module", label: "My Module", icon: "mdi-...", component: MyModule });
```

**Backend** (`src/main/java/com/example/desktop/MainApp.java`):
```java
List<RouteRegistrar> registrars = List.of(
    new ExtractionRoutes()
    // new MyNewModuleRoutes(), // <-- add future modules here
);
```

**Backend routes** — implement `RouteRegistrar`:
```java
public class MyModuleRoutes implements RouteRegistrar {
    public void register(JavalinConfig config) { ... }
}
```

Zero changes to `App.vue`, `BackendServer.java`, or any other core file.

---

## Directory Structure

### `/src/main/java/com/example/desktop/`

| File | Role |
|---|---|
| `Launcher.java` | Entry point. Tees stdout/stderr to timestamped log file. Delegates to `MainApp.main()`. |
| `MainApp.java` | JavaFX `Application`. Wires `RouteRegistrar` list and starts `BackendServer`. Creates full-screen WebView window. |
| `BackendServer.java` | Javalin HTTP host. Accepts `List<RouteRegistrar>`. Binds on port 0 (OS assigns port atomically), reads back via `app.port()`. Serves static files from `/public` classpath. Routes: `GET /`, `GET /api/health`, + all module routes. |
| `RouteRegistrar.java` | Plugin interface. One method: `void register(JavalinConfig config)`. |
| `ExtractionRoutes.java` | `RouteRegistrar` for the Extraction module. Routes: `GET /api/vendors`, `POST /api/sqlserver/databases`. |

### `/src/main/resources/`
- **`vendors.json`** — EMR vendor list served by `/api/vendors`.
- **`public/`** — Built Vue frontend (written by Vite, do NOT edit manually).

### `/frontend/src/`

| File/Dir | Role |
|---|---|
| `main.js` | Creates Vuetify, registers modules via `registerModule()`, mounts app. **← Add new modules here.** |
| `App.vue` | Thin shell (~130 lines). Reads module registry, renders nav rail dynamically, renders active module via `<component :is="...">`. Owns: theme, UI scale, keyboard shortcut. |
| `style.css` | Global base styles. |
| `modules/registry.js` | Reactive module registry (`registerModule`, `useModules`). |
| `modules/shared/useResizablePanel.js` | Composable for drag-resizable sidebar. Used by all modules with a two-panel layout. Persists width to localStorage per-module. |
| `modules/extraction/ExtractionModule.vue` | Extraction module SFC — vendors, project details, SQL Server connection, database picker, mapping workspace. Self-contained state and styles. |
| `modules/db-conversion/DbConversionModule.vue` | DB Conversion module SFC — source/target DB type pickers, batch size, conversion plan/mapping/log workspace. Self-contained. |

### `/scripts/`
- **`package-windows.ps1`** — Windows packaging. Parameters: `-JavaHome` (default: Zulu 21), `-MavenHome` (auto-detected from PATH), `-WinConsole` (opt-in debug terminal). Builds Vue frontend via `npm run build` first (required — `src/main/resources/public/` is gitignored), then Maven `clean package`, copies runtime deps (including JavaFX 21 `win`-classifier JARs), generates `.ico`, stages JARs, produces a native `.exe` installer via `jpackage` + WiX (falls back to `app-image`). Passes `--add-modules javafx.controls,javafx.web` to jpackage for Java 21 module compatibility.

### Root Files
| File | Role |
|---|---|
| `run.sh` | Build + run script. Checks `npm`/`mvn` prerequisites. Builds Vue frontend (Vite outputs to `src/main/resources/public`). Runs `mvn clean compile javafx:run`. |
| `pom.xml` | Maven POM. `<release>21</release>`. OS-detection profiles (`linux`/`win`/`mac`) for JavaFX native classifiers. Dependencies: Javalin, Jackson, SLF4J, `mssql-jdbc:jre21`, `javafx-controls:21.0.1`, `javafx-web:21.0.1`. Plugins: `maven-compiler-plugin 3.13.0`, `javafx-maven-plugin 0.0.8`. |
| `README.md` | Project readme. |

---

## Core Architectural Rules & Maintenance Requirements
1. **Java 21 + JavaFX 21**: Always use `<release>21</release>` and OpenJFX 21. Use `javafx-maven-plugin` (not `exec-maven-plugin`).
2. **OS classifiers**: Always include `${javafx.platform}` classifier on JavaFX deps — without it, native libs are missing.
3. **Port binding**: Javalin binds on port `0`; read back with `app.port()`. Never use find-then-bind.
4. **Plugin frontend**: New modules register in `main.js` — never add module-specific code to `App.vue`.
5. **Plugin backend**: New module routes implement `RouteRegistrar` and are added to the list in `MainApp.java` — never add route logic to `BackendServer.java`.
6. **Frontend output**: Vite writes directly to `src/main/resources/public`. Never copy manually.
7. **Maven goal**: `mvn javafx:run` (not `exec:java`).
8. **Metadata maintenance**: Update this file whenever files, dependencies, or architecture changes.
