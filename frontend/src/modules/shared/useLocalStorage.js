import { ref, watch } from "vue";

/**
 * Creates a ref whose value is automatically persisted to localStorage.
 * On creation, reads the existing stored value (falls back to defaultValue).
 *
 * Usage:
 *   const hostname = useLocalStorage("emr_extraction_host", "");
 *   // hostname behaves exactly like a normal ref, but survives page reload.
 *
 * @param {string} key            localStorage key
 * @param {*}      defaultValue   value used when nothing is stored yet
 * @returns {import("vue").Ref}
 */
export function useLocalStorage(key, defaultValue) {
  let initial = defaultValue;
  try {
    const raw = localStorage.getItem(key);
    if (raw !== null) initial = JSON.parse(raw);
  } catch { /* corrupted storage — use default */ }

  const state = ref(initial);
  watch(state, (v) => {
    try { localStorage.setItem(key, JSON.stringify(v)); }
    catch { /* storage full or unavailable */ }
  }, { deep: true });

  return state;
}
