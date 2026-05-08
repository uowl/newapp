import { ref, onMounted, onBeforeUnmount } from "vue";

/**
 * Composable that provides drag-resizable sidebar panel behaviour.
 *
 * Usage:
 *   const { componentsWidth, moduleRef, componentsPanelRef, workspacePanelRef, startResize } =
 *     useResizablePanel({ storageKey: "emr_mymodule_width" });
 *
 *   In the template, attach the refs:
 *     <div class="module-frame" ref="moduleRef">           ← CSS-var target
 *       <section ref="componentsPanelRef">...</section>   ← used for metrics
 *       <div class="column-resizer" @mousedown="startResize" />
 *       <main ref="workspacePanelRef">...</main>          ← used for metrics
 *     </div>
 */
export function useResizablePanel({
  storageKey = null,
  defaultWidth = 360,
  min = 220,
  max = 560,
  workspaceMin = 280,
  splitterWidth = 10,
} = {}) {
  const componentsWidth = ref(defaultWidth);

  // Refs the caller must wire in the template
  const moduleRef = ref(null);           // wrapper element (display:contents is fine)
  const componentsPanelRef = ref(null);  // the sidebar panel
  const workspacePanelRef = ref(null);   // the workspace panel

  const state = {
    active: false,
    startX: 0,
    startWidth: 0,
    pendingClientX: 0,
    rafId: 0,
    totalAvailable: 0,
    liveWidth: 0,
  };

  function clamp(v, lo, hi) {
    return Math.min(Math.max(v, lo), hi);
  }

  function applyWidth(w) {
    // CSS custom properties cascade even through display:contents elements,
    // so setting --components-width here is inherited by .components-panel.
    if (moduleRef.value) {
      moduleRef.value.style.setProperty("--components-width", w + "px");
    }
  }

  function getAvailableWidth() {
    const cp = componentsPanelRef.value;
    const wp = workspacePanelRef.value;
    if (!cp || !wp) return 0;
    // Sum of both panels + splitter = total space available for this module row
    return cp.offsetWidth + splitterWidth + wp.offsetWidth;
  }

  function applyResizeFrame() {
    state.rafId = 0;
    if (!state.active) return;
    const deltaX = state.pendingClientX - state.startX;
    const maxComp = state.totalAvailable - workspaceMin - splitterWidth;
    state.liveWidth = clamp(state.startWidth + deltaX, min, Math.max(min, Math.min(max, maxComp)));
    applyWidth(state.liveWidth);
  }

  function onResizeMove(e) {
    if (!state.active) return;
    state.pendingClientX = e.clientX;
    if (!state.rafId) {
      state.rafId = requestAnimationFrame(applyResizeFrame);
    }
  }

  function stopResize() {
    if (!state.active) return;
    componentsWidth.value = Math.round(state.liveWidth || componentsWidth.value);
    applyWidth(componentsWidth.value);
    if (storageKey) localStorage.setItem(storageKey, String(componentsWidth.value));
    state.active = false;
    if (state.rafId) { cancelAnimationFrame(state.rafId); state.rafId = 0; }
    document.body.classList.remove("resizing-columns");
    window.removeEventListener("mousemove", onResizeMove);
    window.removeEventListener("mouseup", stopResize);
  }

  function startResize(e) {
    state.active = true;
    state.startX = e.clientX;
    state.pendingClientX = e.clientX;
    state.startWidth = componentsWidth.value;
    state.liveWidth = componentsWidth.value;
    state.totalAvailable = getAvailableWidth();
    document.body.classList.add("resizing-columns");
    window.addEventListener("mousemove", onResizeMove);
    window.addEventListener("mouseup", stopResize);
  }

  onMounted(() => {
    if (storageKey) {
      const saved = Number(localStorage.getItem(storageKey));
      if (saved > 0) componentsWidth.value = clamp(saved, min, max);
    }
    applyWidth(componentsWidth.value);
  });

  onBeforeUnmount(stopResize);

  return { componentsWidth, moduleRef, componentsPanelRef, workspacePanelRef, startResize };
}
