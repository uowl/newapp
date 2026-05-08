import { ref } from "vue";

const STORAGE_KEY = "emr_connection_history";
const MAX_ENTRIES = 8;

function load() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]"); }
  catch { return []; }
}

function persist(list) {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(list)); }
  catch { /* storage full */ }
}

/**
 * Shared connection history for SQL Server entries (hostname + port + username).
 * The same history is surfaced in both the Extraction and DB Conversion modules
 * so a server configured in one place is immediately suggested in the other.
 *
 * Usage:
 *   const { connections, saveConnection, removeConnection } = useConnectionHistory();
 *
 *   // After a successful DB load:
 *   saveConnection(hostname.value, port.value, username.value);
 *
 *   // Apply a saved entry:
 *   function applyConnection(c) { hostname.value = c.hostname; port.value = c.port; username.value = c.username; }
 */
export function useConnectionHistory() {
  const connections = ref(load());

  function saveConnection(hostname, port, username) {
    const h = String(hostname).trim();
    if (!h) return;
    const entry = { hostname: h, port: String(port).trim(), username: String(username).trim() };

    // De-duplicate: move existing identical entry to the top
    const deduped = connections.value.filter(c =>
      !(c.hostname === entry.hostname && c.port === entry.port && c.username === entry.username)
    );
    connections.value = [entry, ...deduped].slice(0, MAX_ENTRIES);
    persist(connections.value);
  }

  function removeConnection(index) {
    connections.value.splice(index, 1);
    persist(connections.value);
  }

  return { connections, saveConnection, removeConnection };
}
