import { createApp } from "vue";
import "./style.css";
import App from "./App.vue";

// ── Module Registry ───────────────────────────────────────────────────────────
// Icons are Lucide Vue components (tree-shaken by Vite — only used icons bundled).
// To add a module:
//   1. Create src/modules/my-module/MyModule.vue
//   2. Import the Lucide icon you want for the nav rail
//   3. registerModule({ id, label, icon: LucideComponent, component: MyModule })
import { registerModule } from "./modules/registry.js";
import { LayoutDashboard, DatabaseZap, Terminal } from "lucide-vue-next";
import ExtractionModule from "./modules/extraction/ExtractionModule.vue";
import DbConversionModule from "./modules/db-conversion/DbConversionModule.vue";
import QueryRunnerModule from "./modules/query-runner/QueryRunnerModule.vue";

registerModule({
  id: "extraction",
  label: "Extraction",
  icon: LayoutDashboard,
  component: ExtractionModule
});

registerModule({
  id: "query-runner",
  label: "Query Runner",
  icon: Terminal,
  component: QueryRunnerModule
});

registerModule({
  id: "db-conversion",
  label: "DB Conversion",
  icon: DatabaseZap,
  component: DbConversionModule
});

// ── Mount ─────────────────────────────────────────────────────────────────────
createApp(App).mount("#app");
