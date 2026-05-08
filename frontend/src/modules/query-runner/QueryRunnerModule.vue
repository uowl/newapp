<script setup>
import { ref, computed } from "vue";
import { useLocalStorage } from "../shared/useLocalStorage.js";
import { useConnectionManager } from "../shared/useConnectionManager.js";
import { useResizablePanel } from "../shared/useResizablePanel.js";
import { 
  Play, Database, KeyRound, Server, History, X, ChevronDown, 
  Table2, Download, Terminal, AlertCircle, Loader2, Trash2
} from "lucide-vue-next";

// ── Connection State ──────────────────────────────────────────────────────────
const hostname = useLocalStorage("emr_ext_sql_host", "");
const port     = useLocalStorage("emr_ext_sql_port", "");
const username = useLocalStorage("emr_ext_sql_username", "");
const password = ref("");
const database = useLocalStorage("emr_ext_sql_database", "");

// ── Sidebar resize ────────────────────────────────────────────────────────────
const { moduleRef, componentsPanelRef, workspacePanelRef, startResize } = useResizablePanel({
  storageKey: "emr_queryrunner_width",
  defaultWidth: 320,
  minWidth: 240,
  maxWidth: 560
});

// ── Connection Manager (Secure Backend Storage) ───────────────────────────────
const { savedConnections, deleteConnection } = useConnectionManager();
const showConnHistory = ref(false);

function applyConnection(c) {
  hostname.value = c.hostname;
  port.value     = c.port;
  username.value = c.username;
  password.value = c.password || "";
  database.value = c.defaultDatabase || "";
  showConnHistory.value = false;
}

// ── Query State ───────────────────────────────────────────────────────────────
const sqlQuery = useLocalStorage("emr_query_sql", "SELECT TOP 10 * FROM sys.tables");
const results  = ref(null);
const isRunning = ref(false);
const error    = ref("");
const message  = ref("");

async function runQuery() {
  if (isRunning.value) return;
  isRunning.value = true;
  error.value = "";
  message.value = "";
  results.value = null;

  try {
    const res = await fetch("/api/sqlserver/query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        hostname: hostname.value,
        port: port.value,
        username: username.value,
        password: password.value,
        database: database.value,
        sql: sqlQuery.value
      })
    });

    const data = await res.json();
    if (!res.ok) {
      error.value = data.error || "Query failed";
    } else {
      results.value = data;
      message.value = data.message;
    }
  } catch (e) {
    error.value = "Unable to reach server";
  } finally {
    isRunning.value = false;
  }
}

function downloadCSV() {
  if (!results.value || !results.value.rows.length) return;
  
  const headers = results.value.columns.join(",");
  const rows = results.value.rows.map(row => 
    results.value.columns.map(col => {
      const val = row[col];
      if (val === null || val === undefined) return "";
      const str = String(val).replace(/"/g, '""');
      return str.includes(",") || str.includes("\n") || str.includes('"') ? `"${str}"` : str;
    }).join(",")
  );
  
  const csv = [headers, ...rows].join("\n");
  const blob = new Blob([csv], { type: "text/csv" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `query_results_${new Date().getTime()}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div ref="moduleRef" class="flex flex-1 overflow-hidden h-full">
    <!-- ── Sidebar (Connection) ────────────────────────────────────────────── -->
    <aside ref="componentsPanelRef" class="components-panel bg-base-100 border-r border-base-300 flex flex-col shrink-0 overflow-y-auto">
      <div class="px-4 py-3 border-b border-base-300">
        <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">SQL Pad</span>
      </div>
      
      <div class="p-4 space-y-4">
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4 gap-4">
            <h3 class="font-semibold text-sm flex items-center gap-2">
              <KeyRound class="w-4 h-4 text-primary" /> Connection
            </h3>
            
            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Host & Port</span></label>
              <div class="relative">
                <label class="input input-bordered input-sm flex items-center gap-2 pr-1">
                  <Server class="w-3.5 h-3.5 text-base-content/40 shrink-0" />
                  <input v-model="hostname" type="text" class="grow min-w-0" placeholder="Hostname"
                         @focus="showConnHistory = savedConnections.length > 0" />
                  <button
                    v-if="savedConnections.length"
                    class="btn btn-ghost btn-xs btn-square"
                    @click.stop="showConnHistory = !showConnHistory"
                  >
                    <History class="w-3.5 h-3.5" />
                  </button>
                </label>

                <!-- Connection history dropdown -->
                <ul v-if="showConnHistory" class="absolute z-10 w-full mt-1 menu bg-base-100 rounded-box border border-base-300 shadow-xl max-h-48 overflow-y-auto">
                  <li v-for="c in savedConnections" :key="c.id" class="text-xs">
                    <a @click="applyConnection(c)" class="flex justify-between items-center py-2">
                      <span>{{ c.name || c.hostname }}</span>
                      <button @click.stop="deleteConnection(c.id)" class="btn btn-ghost btn-xs text-error">
                        <Trash2 class="w-3 h-3" />
                      </button>
                    </a>
                  </li>
                </ul>
              </div>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Connection Details</span></label>
              <div class="flex flex-col gap-3">
                <div class="form-control gap-1">
                  <label class="label p-0"><span class="label-text text-[10px] uppercase opacity-50 font-bold">Port</span></label>
                  <input v-model="port" type="text" class="input input-bordered input-sm w-full" placeholder="1433" />
                </div>
                <div class="form-control gap-1">
                  <label class="label p-0"><span class="label-text text-[10px] uppercase opacity-50 font-bold">Username</span></label>
                  <input v-model="username" type="text" class="input input-bordered input-sm w-full" placeholder="sa" />
                </div>
                <div class="form-control gap-1">
                  <label class="label p-0"><span class="label-text text-[10px] uppercase opacity-50 font-bold">Password</span></label>
                  <input v-model="password" type="password" class="input input-bordered input-sm w-full" placeholder="••••••••" />
                </div>
                <div class="form-control gap-1">
                  <label class="label p-0"><span class="label-text text-[10px] uppercase opacity-50 font-bold">Database</span></label>
                  <input v-model="database" type="text" class="input input-bordered input-sm w-full" placeholder="master" />
                </div>
              </div>
            </div>
            
            <button class="btn btn-primary btn-sm w-full gap-2" :disabled="isRunning" @click="runQuery">
              <Play v-if="!isRunning" class="w-3.5 h-3.5" />
              <Loader2 v-else class="w-3.5 h-3.5 animate-spin" />
              Execute Query
            </button>
          </div>
        </div>

        <div v-if="error" class="alert alert-error text-xs p-3 rounded-lg">
          <AlertCircle class="w-4 h-4" />
          <span>{{ error }}</span>
        </div>

        <div v-if="message" class="alert alert-success bg-success/10 border-success/20 text-success-content text-xs p-3 rounded-lg">
          <Terminal class="w-4 h-4 text-success" />
          <span>{{ message }}</span>
        </div>
      </div>
    </aside>

    <!-- ── Column resizer ──────────────────────────────────────────────────── -->
    <div class="col-resizer" @mousedown="startResize" />

    <!-- ── Main Area (Editor & Results) ────────────────────────────────────── -->
    <main ref="workspacePanelRef" class="flex-1 flex flex-col overflow-hidden bg-base-200">
      <!-- SQL Editor -->
      <div class="h-1/3 min-h-[160px] flex flex-col bg-base-100 border-b border-base-300">
        <div class="px-4 py-2 bg-base-200/50 border-b border-base-300 flex items-center justify-between shrink-0">
          <div class="flex items-center gap-2 text-xs font-bold text-base-content/60">
            <Terminal class="w-3.5 h-3.5" /> SQL EDITOR
          </div>
          <div class="text-[10px] text-base-content/40 font-mono">MAX 500 ROWS</div>
        </div>
        <div class="flex-1 min-h-0 flex flex-col">
          <textarea
            v-model="sqlQuery"
            class="flex-1 p-4 font-mono text-sm bg-transparent outline-none resize-none overflow-auto"
            spellcheck="false"
            placeholder="Enter SQL query here..."
          />
        </div>
      </div>

      <!-- Results Table -->
      <div class="flex-1 flex flex-col overflow-hidden">
        <div class="px-4 py-2 bg-base-100 border-b border-base-300 flex items-center justify-between shrink-0">
          <div class="flex items-center gap-2 text-xs font-bold text-base-content/60">
            <Table2 class="w-3.5 h-3.5" /> RESULTS
          </div>
          <button 
            v-if="results?.rows?.length" 
            class="btn btn-ghost btn-xs gap-1.5 h-7"
            @click="downloadCSV"
          >
            <Download class="w-3 h-3" /> Export CSV
          </button>
        </div>

        <div class="flex-1 overflow-auto bg-base-100">
          <table v-if="results?.rows?.length" class="table table-xs table-pin-rows table-pin-cols">
            <thead>
              <tr>
                <th v-for="col in results.columns" :key="col">{{ col }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in results.rows" :key="idx" class="hover">
                <td v-for="col in results.columns" :key="col" class="font-mono whitespace-nowrap max-w-xs truncate">
                  {{ row[col] === null ? 'NULL' : row[col] }}
                </td>
              </tr>
            </tbody>
          </table>
          
          <div v-else-if="!isRunning" class="h-full flex items-center justify-center text-base-content/20">
            <div class="text-center">
              <Terminal class="w-12 h-12 mx-auto mb-2 opacity-10" />
              <p class="text-sm font-medium">Run a query to see results</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.components-panel {
  width: var(--components-width, 320px);
  min-width: 240px;
}
.col-resizer {
  width: 8px;
  flex-shrink: 0;
  cursor: col-resize;
  position: relative;
}
.col-resizer::after {
  content: "";
  position: absolute;
  top: 0; bottom: 0; left: 3px;
  width: 2px;
  background: oklch(var(--bc) / 0.12);
  transition: background 0.15s;
}
.col-resizer:hover::after { background: oklch(var(--p) / 0.6); }
</style>
