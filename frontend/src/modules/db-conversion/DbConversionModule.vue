<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useResizablePanel }    from "../shared/useResizablePanel.js";
import { useLocalStorage }      from "../shared/useLocalStorage.js";
import { useConnectionManager }  from "../shared/useConnectionManager.js";
import {
  Database, ArrowRight, Settings2, RotateCw,
  FileText, Table2, GitMerge, Play, History, X, Save, Trash2
} from "lucide-vue-next";

// ── Sidebar resize ────────────────────────────────────────────────────────────
const { componentsWidth, moduleRef, componentsPanelRef, workspacePanelRef, startResize } = useResizablePanel({
  storageKey: "emr_dbconversion_width",
  defaultWidth: 400,
  min: 280,
  max: 1400,
  workspaceMin: 320
});
const connectionCardsRef = ref(null);
const stackConnectionCards = ref(false);

// ── DB type options ───────────────────────────────────────────────────────────
const DB_TYPES = [
  { id: "sqlserver", label: "SQL Server" },
  { id: "postgres",  label: "PostgreSQL" },
  { id: "mysql",     label: "MySQL" },
  { id: "mariadb",   label: "MariaDB" },
  { id: "oracle",    label: "Oracle DB" },
  { id: "sqlite",    label: "SQLite" }
];

// ── Source config (persisted; password never stored) ──────────────────────────
const sourceType     = useLocalStorage("emr_dbc_src_type",     "sqlserver");
const sourceHost     = useLocalStorage("emr_dbc_src_host",     "");
const sourcePort     = useLocalStorage("emr_dbc_src_port",     "");
const sourceUser     = useLocalStorage("emr_dbc_src_username", "");
const sourceDatabase = useLocalStorage("emr_dbc_src_database", "");
const sourcePass     = ref(""); // never persisted

// ── Target config (persisted; password never stored) ──────────────────────────
const targetType     = useLocalStorage("emr_dbc_tgt_type",     "postgres");
const targetHost     = useLocalStorage("emr_dbc_tgt_host",     "");
const targetPort     = useLocalStorage("emr_dbc_tgt_port",     "");
const targetUser     = useLocalStorage("emr_dbc_tgt_username", "");
const targetDatabase = useLocalStorage("emr_dbc_tgt_database", "");
const targetPass     = ref(""); // never persisted

// ── Connection Manager (Secure Backend Storage) ───────────────────────────────
const { savedConnections, saveConnection, deleteConnection } = useConnectionManager();
const showSrcHistory = ref(false);
const showTgtHistory = ref(false);
const srcHistoryRef  = ref(null);
const tgtHistoryRef  = ref(null);
const srcConnName    = ref("");
const tgtConnName    = ref("");

function applySrcConnection(c) {
  sourceHost.value   = c.hostname;
  sourcePort.value   = c.port;
  sourceUser.value   = c.username;
  sourcePass.value   = c.password || "";
  sourceDatabase.value = c.defaultDatabase || "";
  showSrcHistory.value = false;
}
function applyTgtConnection(c) {
  targetHost.value   = c.hostname;
  targetPort.value   = c.port;
  targetUser.value   = c.username;
  targetPass.value   = c.password || "";
  targetDatabase.value = c.defaultDatabase || "";
  showTgtHistory.value = false;
}

async function handleSaveSrc() {
  await saveConnection({
    name: srcConnName.value || sourceHost.value,
    hostname: sourceHost.value,
    port: sourcePort.value,
    username: sourceUser.value,
    password: sourcePass.value,
    defaultDatabase: sourceDatabase.value
  });
  srcConnName.value = "";
}

async function handleSaveTgt() {
  await saveConnection({
    name: tgtConnName.value || targetHost.value,
    hostname: targetHost.value,
    port: targetPort.value,
    username: targetUser.value,
    password: targetPass.value,
    defaultDatabase: targetDatabase.value
  });
  tgtConnName.value = "";
}

// ── Options (persisted) ───────────────────────────────────────────────────────
const batchSize     = useLocalStorage("emr_dbc_batch_size",      500);
const dropTarget    = useLocalStorage("emr_dbc_drop_target",     false);
const migrateData   = useLocalStorage("emr_dbc_migrate_data",    true);
const migrateSchema = useLocalStorage("emr_dbc_migrate_schema",  true);

// ── Workspace (tab persisted) ─────────────────────────────────────────────────
const workspaceTab  = useLocalStorage("emr_dbc_ws_tab", "plan");
const conversionLog = useLocalStorage("emr_dbc_ws_log", "");
const isRunning     = ref(false);

const sourceLabel = computed(() => DB_TYPES.find(t => t.id === sourceType.value)?.label ?? "Source");
const targetLabel = computed(() => DB_TYPES.find(t => t.id === targetType.value)?.label ?? "Target");

async function runConversion() {
  if (isRunning.value) return;
  isRunning.value = true;
  conversionLog.value = `[${new Date().toLocaleTimeString()}] Starting: ${sourceLabel.value} → ${targetLabel.value}\n`;
  workspaceTab.value = "log";
  await new Promise(r => setTimeout(r, 1200));
  conversionLog.value += `[${new Date().toLocaleTimeString()}] Done.\n`;
  isRunning.value = false;
}

function adjustMainLayoutWidth() {
  const components = componentsPanelRef.value;
  const workspace = workspacePanelRef.value;
  const module = moduleRef.value;
  if (!components || !workspace || !module) return;

  const splitter = 8;
  const totalWidth = components.clientWidth + workspace.clientWidth + splitter;
  if (totalWidth <= 0) return;

  const target = Math.round(totalWidth * 0.45);
  const minWidth = 280;
  const maxWidth = Math.max(minWidth, totalWidth - 320 - splitter);
  const clamped = Math.max(minWidth, Math.min(target, maxWidth));

  componentsWidth.value = clamped;
  module.style.setProperty("--components-width", `${clamped}px`);
  localStorage.setItem("emr_dbconversion_width", String(clamped));
}

function adjustConnectionCardsLayout() {
  const grid = connectionCardsRef.value;
  if (!grid) return;
  stackConnectionCards.value = grid.clientWidth < 980;
}

function onWindowMaximized() {
  requestAnimationFrame(adjustMainLayoutWidth);
  setTimeout(adjustMainLayoutWidth, 120);
  requestAnimationFrame(adjustConnectionCardsLayout);
  setTimeout(adjustConnectionCardsLayout, 120);
}

onMounted(() => {
  window.addEventListener("resize", adjustMainLayoutWidth);
  window.addEventListener("resize", adjustConnectionCardsLayout);
  window.addEventListener("emr-window-maximized", onWindowMaximized);
  requestAnimationFrame(adjustMainLayoutWidth);
  requestAnimationFrame(adjustConnectionCardsLayout);
  setTimeout(adjustMainLayoutWidth, 120);
  setTimeout(adjustConnectionCardsLayout, 120);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", adjustMainLayoutWidth);
  window.removeEventListener("resize", adjustConnectionCardsLayout);
  window.removeEventListener("emr-window-maximized", onWindowMaximized);
});
</script>

<template>
  <div ref="moduleRef" class="contents">

    <!-- ── Config panel (left) ─────────────────────────────────────────────── -->
    <div ref="componentsPanelRef" class="components-panel flex flex-col bg-base-100 border-r border-base-300 overflow-hidden">
      <div class="px-4 py-3 border-b border-base-300 shrink-0">
        <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">
          DB Conversion
        </span>
      </div>

      <div class="flex-1 overflow-y-auto p-4 space-y-4">

        <div
          ref="connectionCardsRef"
          class="grid gap-4 items-stretch"
          :class="stackConnectionCards ? 'grid-cols-1' : 'grid-cols-[minmax(0,1fr)_40px_minmax(0,1fr)]'"
        >
          <!-- Source DB -->
          <div class="card bg-base-200 border border-base-300 h-full">
            <div class="card-body p-4 gap-4 h-full">
              <h3 class="font-semibold text-sm flex items-center gap-2">
                <Database class="w-4 h-4 text-primary" /> Source Database
              </h3>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Database Engine</span></label>
              <select v-model="sourceType" class="select select-bordered select-sm w-full">
                <option v-for="t in DB_TYPES" :key="t.id" :value="t.id">{{ t.label }}</option>
              </select>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Host</span></label>
              <!-- Hostname with history dropdown -->
              <div class="relative" ref="srcHistoryRef">
                <input v-model="sourceHost" type="text"
                       class="input input-bordered input-sm w-full"
                       placeholder="Host"
                       @focus="showSrcHistory = savedConnections.length > 0" />

                <div v-if="savedConnections.length" class="mt-1.5 flex justify-end">
                  <button class="btn btn-ghost btn-xs gap-1"
                          title="Saved connections"
                          @click.stop="showSrcHistory = !showSrcHistory">
                    <History class="w-3.5 h-3.5" />
                    Connections
                  </button>
                </div>

                <ul v-if="showSrcHistory" class="absolute z-10 w-full mt-1 menu bg-base-100 rounded-box border border-base-300 shadow-xl max-h-48 overflow-y-auto">
                  <li v-for="c in savedConnections" :key="c.id" class="text-xs">
                    <a @click="applySrcConnection(c)" class="flex justify-between items-center py-2">
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
              <label class="label p-0"><span class="label-text text-xs font-semibold">Port</span></label>
              <input v-model="sourcePort" type="text" class="input input-bordered input-sm w-full" placeholder="Port" />
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Credentials</span></label>
              <input v-model="sourceUser" type="text" class="input input-bordered input-sm w-full" placeholder="Username" />
              <input v-model="sourcePass" type="password" class="input input-bordered input-sm w-full" placeholder="Password (not saved)" />
              <div class="flex justify-end">
                <button class="btn btn-ghost btn-sm btn-square" @click="handleSaveSrc" title="Save">
                  <Save class="w-4 h-4" />
                </button>
              </div>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Initial Database</span></label>
              <input v-model="sourceDatabase" type="text" class="input input-bordered input-sm w-full" placeholder="Database name" />
            </div>
            </div>
          </div>

          <!-- Arrow separator -->
          <div v-if="stackConnectionCards" class="flex items-center justify-center text-base-content/30 gap-2">
            <div class="flex-1 h-px bg-base-300" />
            <ArrowRight class="w-4 h-4 shrink-0 rotate-90" />
            <div class="flex-1 h-px bg-base-300" />
          </div>
          <div v-else class="flex flex-col items-center justify-center text-base-content/30 gap-2 self-stretch">
            <div class="w-px flex-1 bg-base-300" />
            <ArrowRight class="w-4 h-4 shrink-0" />
            <div class="w-px flex-1 bg-base-300" />
          </div>

          <!-- Target DB -->
          <div class="card bg-base-200 border border-base-300 h-full">
            <div class="card-body p-4 gap-4 h-full">
              <h3 class="font-semibold text-sm flex items-center gap-2">
                <Database class="w-4 h-4 text-secondary" /> Target Database
              </h3>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Database Engine</span></label>
              <select v-model="targetType" class="select select-bordered select-sm w-full">
                <option v-for="t in DB_TYPES" :key="t.id" :value="t.id">{{ t.label }}</option>
              </select>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Host</span></label>
              <!-- Hostname with history dropdown -->
              <div class="relative" ref="tgtHistoryRef">
                <input v-model="targetHost" type="text"
                       class="input input-bordered input-sm w-full"
                       placeholder="Host"
                       @focus="showTgtHistory = savedConnections.length > 0" />

                <div v-if="savedConnections.length" class="mt-1.5 flex justify-end">
                  <button class="btn btn-ghost btn-xs gap-1"
                          title="Saved connections"
                          @click.stop="showTgtHistory = !showTgtHistory">
                    <History class="w-3.5 h-3.5" />
                    Connections
                  </button>
                </div>

                <ul v-if="showTgtHistory" class="absolute z-10 w-full mt-1 menu bg-base-100 rounded-box border border-base-300 shadow-xl max-h-48 overflow-y-auto">
                  <li v-for="c in savedConnections" :key="c.id" class="text-xs">
                    <a @click="applyTgtConnection(c)" class="flex justify-between items-center py-2">
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
              <label class="label p-0"><span class="label-text text-xs font-semibold">Port</span></label>
              <input v-model="targetPort" type="text" class="input input-bordered input-sm w-full" placeholder="Port" />
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Credentials</span></label>
              <input v-model="targetUser" type="text" class="input input-bordered input-sm w-full" placeholder="Username" />
              <input v-model="targetPass" type="password" class="input input-bordered input-sm w-full" placeholder="Password (not saved)" />
              <div class="flex justify-end">
                <button class="btn btn-ghost btn-sm btn-square" @click="handleSaveTgt" title="Save">
                  <Save class="w-4 h-4" />
                </button>
              </div>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Initial Database</span></label>
              <input v-model="targetDatabase" type="text" class="input input-bordered input-sm w-full" placeholder="Database name" />
            </div>
            </div>
          </div>

          <!-- Options -->
          <div
            class="card bg-base-200 border border-base-300"
            :class="stackConnectionCards ? '' : 'col-start-1 col-end-2'"
          >
            <div class="card-body p-4 gap-4">
              <h3 class="font-semibold text-sm flex items-center gap-2">
                <Settings2 class="w-4 h-4 text-primary" /> Options
              </h3>
              <div class="form-control gap-1.5">
                <label class="label p-0"><span class="label-text text-xs font-semibold">Batch Size</span></label>
                <input v-model.number="batchSize" type="number" min="1" max="10000"
                       class="input input-bordered input-sm w-full" />
              </div>
              <label class="flex items-center gap-3 cursor-pointer py-1">
                <input v-model="migrateSchema" type="checkbox" class="checkbox checkbox-primary checkbox-sm" />
                <span class="text-sm">Migrate schema</span>
              </label>
              <label class="flex items-center gap-3 cursor-pointer py-1">
                <input v-model="migrateData" type="checkbox" class="checkbox checkbox-primary checkbox-sm" />
                <span class="text-sm">Migrate data</span>
              </label>
              <label class="flex items-center gap-3 cursor-pointer py-1">
                <input v-model="dropTarget" type="checkbox" class="checkbox checkbox-warning checkbox-sm" />
                <span class="text-sm text-warning">Drop target tables first</span>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Column resizer ──────────────────────────────────────────────────── -->
    <div class="col-resizer" @mousedown="startResize" />

    <!-- ── Workspace panel (right) ────────────────────────────────────────── -->
    <div ref="workspacePanelRef" class="workspace-panel flex flex-col overflow-hidden bg-base-200">
      <div class="px-6 py-4 bg-base-100 border-b border-base-300 shrink-0 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-bold leading-tight">{{ sourceLabel }} → {{ targetLabel }}</h2>
          <p class="text-xs text-base-content/50 mt-0.5">Batch size: {{ batchSize }} rows</p>
        </div>
        <button class="btn btn-primary btn-sm gap-2" :disabled="isRunning" @click="runConversion">
          <RotateCw v-if="isRunning" class="w-4 h-4 animate-spin" />
          <Play v-else class="w-4 h-4" />
          {{ isRunning ? "Converting…" : "Run Conversion" }}
        </button>
      </div>

      <div role="tablist" class="tabs tabs-bordered px-6 bg-base-100 border-b border-base-300 shrink-0">
        <button role="tab" class="tab gap-2" :class="workspaceTab === 'plan' ? 'tab-active font-semibold' : ''" @click="workspaceTab = 'plan'">
          <GitMerge class="w-4 h-4" /> Plan
        </button>
        <button role="tab" class="tab gap-2" :class="workspaceTab === 'mapping' ? 'tab-active font-semibold' : ''" @click="workspaceTab = 'mapping'">
          <Table2 class="w-4 h-4" /> Mapping
        </button>
        <button role="tab" class="tab gap-2" :class="workspaceTab === 'log' ? 'tab-active font-semibold' : ''" @click="workspaceTab = 'log'">
          <FileText class="w-4 h-4" /> Log
        </button>
      </div>

      <div class="flex-1 overflow-hidden">
        <div v-show="workspaceTab === 'plan'" class="h-full flex items-center justify-center text-base-content/30">
          <div class="text-center space-y-3">
            <GitMerge class="w-12 h-12 mx-auto" />
            <p class="font-medium">Fill in source and target details, then run a conversion</p>
          </div>
        </div>
        <div v-show="workspaceTab === 'mapping'" class="h-full flex items-center justify-center text-base-content/30">
          <div class="text-center space-y-3">
            <Table2 class="w-12 h-12 mx-auto" />
            <p class="font-medium">Table mapping will appear here after schema analysis</p>
          </div>
        </div>
        <div v-show="workspaceTab === 'log'" class="h-full flex flex-col p-4 gap-2">
          <textarea v-model="conversionLog"
                    class="textarea w-full flex-1 font-mono text-xs resize-none bg-base-100
                           border border-base-300 focus:outline-none focus:border-primary"
                    placeholder="Conversion log output will appear here…"
                    readonly />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.contents { display: contents; }
.components-panel { width: var(--components-width, 400px); min-width: 240px; flex-shrink: 0; }
.workspace-panel  { flex: 1; min-width: 240px; }
.col-resizer { width: 8px; flex-shrink: 0; cursor: col-resize; position: relative; }
.col-resizer::after {
  content: ""; position: absolute; top: 0; bottom: 0; left: 3px;
  width: 2px; background: oklch(var(--bc) / 0.12); transition: background 0.15s;
}
.col-resizer:hover::after { background: oklch(var(--p) / 0.6); }
</style>
