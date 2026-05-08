<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useModules } from "./modules/registry.js";
import { DatabaseIcon, Palette, ZoomIn, ZoomOut } from "lucide-vue-next";

// ── Module registry ───────────────────────────────────────────────────────────
const modules = useModules();
const activeNavId = ref("");

watch(modules, (mods) => {
  if (mods.length && !activeNavId.value) activeNavId.value = mods[0].id;
}, { immediate: true });

const activeModule = computed(() =>
  modules.value.find(m => m.id === activeNavId.value) ?? modules.value[0] ?? null
);

// ── Theme ─────────────────────────────────────────────────────────────────────
// All 35 DaisyUI built-in themes. DaisyUI scopes color tokens to data-theme,
// so each swatch preview renders in the actual theme's own colors.
const THEMES = [
  "light",       "dark",      "cupcake",     "bumblebee",  "emerald",
  "corporate",   "synthwave", "retro",       "cyberpunk",  "valentine",
  "halloween",   "garden",    "forest",      "aqua",       "lofi",
  "pastel",      "fantasy",   "wireframe",   "black",      "luxury",
  "dracula",     "cmyk",      "autumn",      "business",   "acid",
  "lemonade",    "night",     "coffee",      "winter",     "dim",
  "nord",        "sunset",    "caramellatte","abyss",      "silk"
];

const activeTheme      = ref("black");
const themePickerOpen  = ref(false);
const themePickerRef   = ref(null);
const themeButtonRef   = ref(null);

function applyTheme(theme) {
  activeTheme.value = theme;
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem("emr_theme", theme);
}

// ── UI Zoom (CSS zoom on <html>) ──────────────────────────────────────────────
// zoom on the html element scales everything uniformly — including px borders,
// SVG stroke widths, and DaisyUI component sizing. rem scaling would leave
// px-based values (borders, icons) at their original size.
const UI_ZOOM_MIN  = 0.75;
const UI_ZOOM_MAX  = 1.30;
const UI_ZOOM_STEP = 0.05;

const uiZoom        = ref(1.0);
const uiZoomPercent = computed(() => Math.round(uiZoom.value * 100));

function setZoom(v) {
  const clamped = Math.min(UI_ZOOM_MAX, Math.max(UI_ZOOM_MIN, Math.round(v * 100) / 100));
  uiZoom.value = clamped;
}
function increaseZoom() { setZoom(uiZoom.value + UI_ZOOM_STEP); }
function decreaseZoom() { setZoom(uiZoom.value - UI_ZOOM_STEP); }

watch(uiZoom, (v) => {
  document.documentElement.style.zoom = String(v);
  localStorage.setItem("emr_ui_zoom", String(v));
});

// ── Keyboard shortcut ─────────────────────────────────────────────────────────
function handleKeyDown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    document.getElementById("vendor-search")?.focus();
  }
  if (e.key === "Escape" && themePickerOpen.value) {
    themePickerOpen.value = false;
  }
}

// ── Click-outside theme picker ────────────────────────────────────────────────
function handleClickOutside(e) {
  if (!themePickerOpen.value) return;
  if (
    themePickerRef.value && !themePickerRef.value.contains(e.target) &&
    themeButtonRef.value && !themeButtonRef.value.contains(e.target)
  ) {
    themePickerOpen.value = false;
  }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(() => {
  const savedTheme = localStorage.getItem("emr_theme") ?? "black";
  const savedZoom  = Number(localStorage.getItem("emr_ui_zoom")) || 1.0;

  applyTheme(THEMES.includes(savedTheme) ? savedTheme : "black");
  setZoom(savedZoom);
  // Apply zoom immediately on mount without triggering the watcher delay
  document.documentElement.style.zoom = String(uiZoom.value);

  window.addEventListener("keydown", handleKeyDown);
  window.addEventListener("click", handleClickOutside, true);

  // Trigger the gradual reveal transition once Vue has mounted the UI
  requestAnimationFrame(() => {
    document.documentElement.classList.remove('theme-initializing');
    document.documentElement.classList.add('theme-loaded');
  });
});
onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleKeyDown);
  window.removeEventListener("click", handleClickOutside, true);
});
</script>

<template>
  <div class="flex h-screen overflow-hidden font-sans bg-base-200">

    <!-- ── Icon rail ───────────────────────────────────────────────────────── -->
    <aside class="w-16 shrink-0 bg-base-100 border-r border-base-300
                  flex flex-col items-center py-3 gap-1 z-10">

      <!-- Brand mark -->
      <div class="w-10 h-10 rounded-xl bg-primary flex items-center justify-center mb-3 shrink-0">
        <DatabaseIcon class="w-5 h-5 text-primary-content" />
      </div>

      <!-- Module nav buttons -->
      <button
        v-for="mod in modules"
        :key="mod.id"
        :title="mod.label"
        class="btn btn-ghost btn-sm btn-square w-10 h-10"
        :class="activeNavId === mod.id
          ? 'bg-primary text-primary-content hover:bg-primary'
          : 'text-base-content/60 hover:text-base-content'"
        @click="activeNavId = mod.id"
      >
        <component :is="mod.icon" class="w-5 h-5" />
      </button>

      <div class="flex-1" />

      <!-- ── Zoom control ──────────────────────────────────────────────────── -->
      <div class="flex flex-col items-center gap-0.5" title="UI zoom">
        <button
          class="btn btn-ghost btn-xs btn-square w-8 h-8"
          :disabled="uiZoom >= UI_ZOOM_MAX"
          @click="increaseZoom"
          aria-label="Zoom in"
        >
          <ZoomIn class="w-3.5 h-3.5" />
        </button>
        <span class="text-[10px] font-mono text-base-content/50 leading-none select-none">
          {{ uiZoomPercent }}%
        </span>
        <button
          class="btn btn-ghost btn-xs btn-square w-8 h-8"
          :disabled="uiZoom <= UI_ZOOM_MIN"
          @click="decreaseZoom"
          aria-label="Zoom out"
        >
          <ZoomOut class="w-3.5 h-3.5" />
        </button>
      </div>

      <!-- ── Theme picker trigger ──────────────────────────────────────────── -->
      <button
        ref="themeButtonRef"
        class="btn btn-ghost btn-sm btn-square w-10 h-10 mt-1"
        :class="themePickerOpen ? 'bg-base-200 text-base-content' : 'text-base-content/60 hover:text-base-content'"
        title="Change theme"
        @click.stop="themePickerOpen = !themePickerOpen"
      >
        <Palette class="w-5 h-5" />
      </button>
    </aside>

    <!-- ── Theme picker flyout ─────────────────────────────────────────────── -->
    <!--
      Fixed-position panel anchored left of the rail so it sits outside
      the flex layout and doesn't affect module sizing.
      CSS zoom on html does NOT affect fixed-position elements' viewport anchor,
      so we offset by the zoomed rail width: 64px * zoom.
    -->
    <Transition name="picker">
      <div
        v-if="themePickerOpen"
        ref="themePickerRef"
        class="fixed bottom-0 left-16 z-50 w-52 max-h-[480px] overflow-y-auto
               bg-base-100 border border-base-300 rounded-tr-2xl shadow-2xl
               flex flex-col"
      >
        <!-- Header -->
        <div class="px-4 py-3 border-b border-base-300 shrink-0">
          <p class="text-xs font-semibold uppercase tracking-widest text-base-content/50">
            Theme — {{ activeTheme }}
          </p>
        </div>

        <!-- Theme list -->
        <ul class="py-1">
          <li v-for="theme in THEMES" :key="theme">
            <button
              class="w-full text-left px-3 py-2 flex items-center gap-3 hover:bg-base-200
                     transition-colors text-sm capitalize"
              :class="activeTheme === theme ? 'font-semibold text-primary' : ''"
              @click="applyTheme(theme)"
            >
              <!-- Live color swatches rendered in the actual theme's own colors -->
              <span :data-theme="theme" class="flex gap-0.5 shrink-0">
                <span class="w-3 h-3 rounded-full bg-primary ring-1 ring-base-300" />
                <span class="w-3 h-3 rounded-full bg-secondary ring-1 ring-base-300" />
                <span class="w-3 h-3 rounded-full bg-accent ring-1 ring-base-300" />
              </span>
              {{ theme }}
              <span v-if="activeTheme === theme" class="ml-auto text-primary text-xs">✓</span>
            </button>
          </li>
        </ul>
      </div>
    </Transition>

    <!-- ── Active module ───────────────────────────────────────────────────── -->
    <component :is="activeModule?.component" v-if="activeModule" />
  </div>
</template>

<style scoped>
/* Theme picker slide-in animation */
.picker-enter-active,
.picker-leave-active {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.picker-enter-from,
.picker-leave-to {
  transform: translateX(-8px);
  opacity: 0;
}
</style>
