import { shallowRef } from "vue";

/**
 * Central module registry.
 *
 * Each module calls registerModule() in main.js to add itself.
 * The shell App.vue reads useModules() to build the nav rail and
 * render the active module — no module-specific code lives in the shell.
 *
 * Module shape:
 * {
 *   id:        string   — unique identifier (used for routing/localStorage)
 *   label:     string   — display name shown in nav rail tooltip
 *   icon:      string   — MDI icon name e.g. "mdi-view-dashboard-outline"
 *   component: object   — Vue component (use defineAsyncComponent for lazy loading)
 * }
 */

const _modules = shallowRef([]);

export function registerModule(mod) {
  _modules.value = [..._modules.value, mod];
}

export function useModules() {
  return _modules;
}
