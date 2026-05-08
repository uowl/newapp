<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useResizablePanel }     from "../shared/useResizablePanel.js";
import { useLocalStorage }       from "../shared/useLocalStorage.js";
import { useConnectionManager }  from "../shared/useConnectionManager.js";
import {
  Search, X, ChevronDown, RotateCw, Server, KeyRound,
  Database, Table2, FileText, Play, History, Lightbulb, Info, Save, Trash2
} from "lucide-vue-next";

// ── Sidebar resize ────────────────────────────────────────────────────────────
const { moduleRef, componentsPanelRef, workspacePanelRef, startResize } = useResizablePanel({
  storageKey: "emr_extraction_width",
  defaultWidth: 320,
  minWidth: 240,
  maxWidth: 560
});

// ── Vendor state (persisted) ──────────────────────────────────────────────────
const vendors        = ref([]);
const vendorQuery    = useLocalStorage("emr_ext_vendor_query",    "");
const selectedVendor = useLocalStorage("emr_ext_vendor_selected", null);
const vendorMenuOpen = ref(false);
const vendorWrapRef  = ref(null);
const isRefreshing   = ref(false);
const vendorConfig   = ref(null);

async function loadVendorConfig(name) {
  if (!name) { vendorConfig.value = null; return; }
  try {
    const res = await fetch(`/api/vendors/${encodeURIComponent(name)}/config`);
    if (res.ok) {
      const data = await res.json();
      vendorConfig.value = (data && Object.keys(data).length) ? data : null;
      // Auto-fill port if currently empty or using the default 1433
      if (vendorConfig.value?.defaultPort && (!port.value || port.value === "1433")) {
        port.value = vendorConfig.value.defaultPort;
      }
    }
  } catch (e) { console.error("Failed to load vendor config", e); }
}

watch(selectedVendor, (nv) => loadVendorConfig(nv), { immediate: true });

const sortedVendors = computed(() =>
  [...vendors.value].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }))
);
const filteredVendors = computed(() => {
  const q = vendorQuery.value.trim().toLowerCase();
  return q ? sortedVendors.value.filter(v => v.toLowerCase().includes(q)) : sortedVendors.value;
});

async function loadVendors() {
  if (isRefreshing.value) return;
  isRefreshing.value = true;
  try {
    const res = await fetch("/api/vendors");
    if (!res.ok) return;
    const data = await res.json();
    if (Array.isArray(data)) {
      vendors.value = data.map(i => String(i));
      if (selectedVendor.value && !vendors.value.includes(selectedVendor.value))
        selectedVendor.value = null;
    }
  } catch (e) { console.error("Failed to load vendors", e); }
  finally { isRefreshing.value = false; }
}

function handleVendorInput() {
  vendorMenuOpen.value = true;
  const exact = sortedVendors.value.find(v =>
    v.toLowerCase() === vendorQuery.value.trim().toLowerCase()
  );
  selectedVendor.value = exact || null;
}
function chooseVendor(v) {
  selectedVendor.value = v;
  vendorQuery.value    = v;
  vendorMenuOpen.value = false;
}
function clearVendor() {
  vendorQuery.value    = "";
  selectedVendor.value = null;
  vendorMenuOpen.value = true;
}
function toggleVendorMenu() { vendorMenuOpen.value = !vendorMenuOpen.value; }

// ── Project details (persisted) ───────────────────────────────────────────────
const projectId         = useLocalStorage("emr_ext_project_id",    "");
const clientProjectName = useLocalStorage("emr_ext_project_name",  "");
const projectEmail      = useLocalStorage("emr_ext_project_email", "");



// ── SQL Server connection (hostname/port/username persisted; password never) ──
const hostname = useLocalStorage("emr_ext_sql_host",     "");
const port     = useLocalStorage("emr_ext_sql_port",     "");
const username = useLocalStorage("emr_ext_sql_username", "");
const password = ref(""); // never persisted

// ── Connection Manager (Secure Backend Storage) ───────────────────────────────
const { savedConnections, saveConnection, deleteConnection } = useConnectionManager();
const showConnHistory  = ref(false);
const connHistoryRef   = ref(null);
const connectionName   = ref("");

function applyConnection(c) {
  hostname.value       = c.hostname;
  port.value           = c.port;
  username.value       = c.username;
  password.value       = c.password || "";
  database.value       = c.defaultDatabase || "";
  databaseQuery.value  = c.defaultDatabase || "";
  showConnHistory.value = false;
}

async function handleSaveConnection() {
  if (!hostname.value || !username.value) return;
  const name = connectionName.value || hostname.value;
  const success = await saveConnection({
    name,
    hostname: hostname.value,
    port: port.value,
    username: username.value,
    password: password.value,
    defaultDatabase: database.value
  });
  if (success) {
    connectionName.value = "";
    showConnHistory.value = true;
  }
}

// ── Database picker (persisted) ───────────────────────────────────────────────
const databases        = ref([]);
const database         = useLocalStorage("emr_ext_sql_database", "");
const databaseQuery    = useLocalStorage("emr_ext_db_query",     "");
const databaseMenuOpen = ref(false);
const databaseWrapRef  = ref(null);
const isLoadingDbs     = ref(false);
const databaseError    = ref("");

const filteredDatabases = computed(() => {
  const q = databaseQuery.value.trim().toLowerCase();
  return q ? databases.value.filter(db => db.toLowerCase().includes(q)) : databases.value;
});

function handleDatabaseInput() {
  databaseMenuOpen.value = true;
  const exact = databases.value.find(db =>
    db.toLowerCase() === databaseQuery.value.trim().toLowerCase()
  );
  database.value = exact || "";
}
function chooseDatabase(name) {
  database.value         = name;
  databaseQuery.value    = name;
  databaseMenuOpen.value = false;
  databaseError.value    = "";
}
function clearDatabase() {
  database.value         = "";
  databaseQuery.value    = "";
  databaseMenuOpen.value = true;
}
function toggleDatabaseMenu() { databaseMenuOpen.value = !databaseMenuOpen.value; }

async function loadDatabases() {
  if (isLoadingDbs.value) return;
  databaseError.value = "";
  isLoadingDbs.value  = true;
  try {
    const res = await fetch("/api/sqlserver/databases", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        hostname: hostname.value,
        port:     port.value,
        username: username.value,
        password: password.value
      })
    });
    if (!res.ok) {
      let msg = "Unable to connect";
      try { const err = await res.json(); if (err?.error) msg = String(err.error); } catch {}
      databaseError.value = msg;
      return;
    }
    const list = await res.json();
    if (!Array.isArray(list)) { databaseError.value = "Unexpected response"; return; }
    databases.value        = list.map(i => String(i));
    databaseMenuOpen.value = true;
  } catch (e) { databaseError.value = "Unable to reach server"; console.error(e); }
  finally { isLoadingDbs.value = false; }
}

// ── Workspace (tab persisted) ─────────────────────────────────────────────────
const workspaceTab  = useLocalStorage("emr_ext_ws_tab", "mapping");
const workspaceText = useLocalStorage("emr_ext_ws_log", "");

// ── Click-outside ─────────────────────────────────────────────────────────────
function onClickOutside(e) {
  if (vendorWrapRef.value   && !vendorWrapRef.value.contains(e.target))   vendorMenuOpen.value   = false;
  if (databaseWrapRef.value && !databaseWrapRef.value.contains(e.target)) databaseMenuOpen.value = false;
  if (connHistoryRef.value  && !connHistoryRef.value.contains(e.target))  showConnHistory.value  = false;
}

onMounted(() => { loadVendors(); window.addEventListener("click", onClickOutside); });
onBeforeUnmount(() => window.removeEventListener("click", onClickOutside));
</script>

<template>
  <div ref="moduleRef" class="contents">

    <!-- ── Components panel (left sidebar) ─────────────────────────────────── -->
    <div ref="componentsPanelRef" class="components-panel flex flex-col bg-base-100 border-r border-base-300 overflow-hidden">

      <div class="px-4 py-3 border-b border-base-300 flex items-center justify-between shrink-0">
        <span class="text-xs font-semibold uppercase tracking-widest text-base-content/50">
          Extraction
        </span>
        <button
          class="btn btn-ghost btn-xs btn-square"
          title="Refresh vendor list"
          :disabled="isRefreshing"
          @click="loadVendors"
        >
          <RotateCw class="w-3.5 h-3.5" :class="{ 'animate-spin': isRefreshing }" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4 space-y-4">

        <!-- ── Vendor picker ───────────────────────────────────────────────── -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4 gap-4">
            <h3 class="font-semibold text-sm flex items-center gap-2">
              <Server class="w-4 h-4 text-primary" /> EMR System
            </h3>

            <div class="form-control gap-1.5" ref="vendorWrapRef">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Vendor Selection</span></label>
              <label class="input input-bordered input-sm flex items-center gap-2 w-full pr-1">
                <Search class="w-3.5 h-3.5 shrink-0 text-base-content/40" />
                <input
                  id="vendor-search"
                  v-model="vendorQuery"
                  type="text"
                  class="grow text-sm min-w-0"
                  placeholder="Search vendor…"
                  autocomplete="off"
                  @input="handleVendorInput"
                  @focus="vendorMenuOpen = true"
                />
                <button v-if="vendorQuery" class="btn btn-ghost btn-xs btn-square" @click.stop="clearVendor">
                  <X class="w-3 h-3" />
                </button>
                <button class="btn btn-ghost btn-xs btn-square" @click.stop="toggleVendorMenu">
                  <ChevronDown class="w-3.5 h-3.5" />
                </button>
              </label>

              <ul v-if="vendorMenuOpen && filteredVendors.length"
                  class="absolute z-50 w-full mt-1 bg-base-100 border border-base-300
                         rounded-lg shadow-lg max-h-44 overflow-y-auto py-1">
                <li v-for="v in filteredVendors" :key="v">
                  <button
                    class="w-full text-left px-3 py-1.5 text-sm hover:bg-base-200 flex items-center gap-2"
                    :class="selectedVendor === v ? 'text-primary font-medium' : ''"
                    @click="chooseVendor(v)"
                  >
                    <Database class="w-3.5 h-3.5 shrink-0 opacity-50" />
                    {{ v }}
                  </button>
                </li>
              </ul>
              <p v-if="vendorMenuOpen && !filteredVendors.length"
                 class="absolute z-50 w-full mt-1 bg-base-100 border border-base-300
                        rounded-lg shadow-lg px-3 py-3 text-sm text-base-content/50 text-center">
                No results
              </p>
            </div>
          </div>
        </div>

        <!-- ── Project details ─────────────────────────────────────────────── -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4 gap-4">
            <h3 class="font-semibold text-sm flex items-center gap-2">
              <FileText class="w-4 h-4 text-primary" /> Project Details
            </h3>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Project ID</span></label>
              <input v-model="projectId" type="text" inputmode="numeric" pattern="[0-9]*"
                     minlength="6"
                     class="input input-bordered input-sm w-full"
                     placeholder="1234567" />
              <span v-if="projectId && projectId.length < 6"
                    class="text-xs text-error mt-0.5">Minimum 6 digits</span>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Client / Project Name</span></label>
              <input v-model="clientProjectName" type="text"
                     class="input input-bordered input-sm w-full"
                     placeholder="Sample Project Name" />
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Contact Email</span></label>
              <input v-model="projectEmail" type="email"
                     class="input input-bordered input-sm w-full"
                     placeholder="contact@example.com" />
            </div>
          </div>
        </div>

        <!-- ── SQL Server connection ───────────────────────────────────────── -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4 gap-4">
            <h3 class="font-semibold text-sm flex items-center gap-2">
              <KeyRound class="w-4 h-4 text-primary" /> SQL Server
            </h3>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Host & Port</span></label>
              <!-- Hostname + port with connection history dropdown -->
              <div class="relative" ref="connHistoryRef">
                <div class="grid grid-cols-[1fr_auto] gap-2">
                  <label class="input input-bordered input-sm flex items-center gap-1.5 pr-1">
                    <input v-model="hostname" type="text" class="grow text-sm min-w-0"
                           placeholder="Host / IP"
                           @focus="showConnHistory = savedConnections.length > 0" />
                    <button
                      v-if="savedConnections.length"
                      class="btn btn-ghost btn-xs btn-square shrink-0"
                      title="Saved connections"
                      @click.stop="showConnHistory = !showConnHistory"
                    >
                      <History class="w-3.5 h-3.5" />
                    </button>
                  </label>
                  <input v-model="port" type="text"
                         class="input input-bordered input-sm w-20"
                         placeholder="1433" />
                </div>

                <!-- Connection history dropdown -->
                <ul v-if="showConnHistory && savedConnections.length"
                    class="absolute z-50 w-full mt-1 bg-base-100 border border-base-300
                           rounded-lg shadow-lg py-1 overflow-hidden">
                  <li class="px-3 py-1.5 text-[10px] font-semibold uppercase tracking-widest
                             text-base-content/40 border-b border-base-200">
                    Saved Connections
                  </li>
                  <li v-for="conn in savedConnections" :key="conn.id"
                      class="flex items-center gap-1 hover:bg-base-200">
                    <button class="flex-1 text-left px-3 py-2 text-xs overflow-hidden"
                            @click="applyConnection(conn)">
                      <div class="font-bold truncate">{{ conn.name }}</div>
                      <div class="text-[10px] text-base-content/50 truncate">
                        {{ conn.hostname }} : {{ conn.port || "1433" }}
                      </div>
                    </button>
                    <button class="btn btn-ghost btn-xs btn-square mr-1 text-base-content/40
                                   hover:text-error"
                            title="Remove"
                            @click.stop="deleteConnection(conn.id)">
                      <Trash2 class="w-3 h-3" />
                    </button>
                  </li>
                </ul>
              </div>
            </div>

            <div class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Credentials</span></label>
              <div class="flex gap-2">
                <input v-model="username" type="text"
                       class="input input-bordered input-sm flex-1"
                       placeholder="Username" />
                <button class="btn btn-ghost btn-sm btn-square" 
                        title="Save Connection"
                        :disabled="!hostname || !username"
                        @click="handleSaveConnection">
                  <Save class="w-4 h-4" />
                </button>
              </div>
              <input v-model="password" type="password"
                     class="input input-bordered input-sm w-full"
                     placeholder="Password (not saved)" />
            </div>

            <button
              class="btn btn-primary btn-sm w-full"
              :disabled="isLoadingDbs"
              @click="loadDatabases"
            >
              <RotateCw v-if="isLoadingDbs" class="w-3.5 h-3.5 animate-spin" />
              <Database v-else class="w-3.5 h-3.5" />
              {{ isLoadingDbs ? "Connecting…" : "Load Databases" }}
            </button>

            <p v-if="databaseError" class="text-xs text-error">{{ databaseError }}</p>

            <div v-if="databases.length || database" class="form-control gap-1.5">
              <label class="label p-0"><span class="label-text text-xs font-semibold">Target Database</span></label>
              <div class="relative" ref="databaseWrapRef">
              <label class="input input-bordered input-sm flex items-center gap-2 w-full pr-1">
                <Table2 class="w-3.5 h-3.5 shrink-0 text-base-content/40" />
                <input v-model="databaseQuery" type="text" class="grow text-sm min-w-0"
                       placeholder="Select database…"
                       autocomplete="off"
                       @input="handleDatabaseInput"
                       @focus="databaseMenuOpen = true" />
                <button v-if="databaseQuery" class="btn btn-ghost btn-xs btn-square"
                        @click.stop="clearDatabase">
                  <X class="w-3 h-3" />
                </button>
                <button class="btn btn-ghost btn-xs btn-square" @click.stop="toggleDatabaseMenu">
                  <ChevronDown class="w-3.5 h-3.5" />
                </button>
              </label>

              <ul v-if="databaseMenuOpen && filteredDatabases.length"
                  class="absolute z-50 w-full mt-1 bg-base-100 border border-base-300
                         rounded-lg shadow-lg max-h-44 overflow-y-auto py-1">
                <li v-for="db in filteredDatabases" :key="db">
                  <button
                    class="w-full text-left px-3 py-1.5 text-sm hover:bg-base-200 flex items-center justify-between gap-2"
                    :class="database === db ? 'text-primary font-medium' : ''"
                    @click="chooseDatabase(db)"
                  >
                    <div class="flex items-center gap-2 overflow-hidden">
                      <Database class="w-3.5 h-3.5 shrink-0 opacity-50" />
                      <span class="truncate">{{ db }}</span>
                    </div>
                    <span v-if="vendorConfig?.suggestedDatabases?.includes(db)"
                          class="badge badge-primary badge-xs shrink-0 font-normal">Suggested</span>
                  </button>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Vendor Guidance ────────────────────────────────────────────────── -->
      <Transition name="fade">
        <div v-if="vendorConfig?.guidance" class="px-4 pb-4">
          <div class="alert alert-info bg-info/10 border-info/20 text-info-content rounded-xl p-3 shadow-sm">
            <Lightbulb class="w-5 h-5 shrink-0 text-info" />
            <div class="text-xs leading-relaxed">
              <div class="font-bold mb-0.5">{{ selectedVendor }} Guidance</div>
              {{ vendorConfig.guidance }}
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- ── Column resizer ──────────────────────────────────────────────────── -->
    <div class="col-resizer" @mousedown="startResize" />

    <!-- ── Workspace panel (right) ────────────────────────────────────────── -->
    <div ref="workspacePanelRef" class="workspace-panel flex flex-col overflow-hidden bg-base-200">

      <div class="px-6 py-4 bg-base-100 border-b border-base-300 shrink-0 flex items-center justify-between">
        <div>
          <h2 class="text-lg font-bold leading-tight">
            {{ selectedVendor ? `${selectedVendor} — Extraction Workspace` : "Extraction Workspace" }}
          </h2>
          <p class="text-xs text-base-content/50 mt-0.5">
            {{ database ? `Database: ${database}` : "No database selected" }}
          </p>
        </div>
        <button class="btn btn-primary btn-sm gap-2" :disabled="!selectedVendor || !database">
          <Play class="w-4 h-4" /> Run Extraction
        </button>
      </div>

      <div role="tablist" class="tabs tabs-bordered px-6 bg-base-100 border-b border-base-300 shrink-0">
        <button role="tab" class="tab gap-2"
                :class="workspaceTab === 'mapping' ? 'tab-active font-semibold' : ''"
                @click="workspaceTab = 'mapping'">
          <Table2 class="w-4 h-4" /> Mapping
        </button>
        <button role="tab" class="tab gap-2"
                :class="workspaceTab === 'log' ? 'tab-active font-semibold' : ''"
                @click="workspaceTab = 'log'">
          <FileText class="w-4 h-4" /> Log
        </button>
      </div>

      <div class="flex-1 overflow-hidden">
        <div v-show="workspaceTab === 'mapping'"
             class="h-full flex items-center justify-center text-base-content/30">
          <div class="text-center space-y-3">
            <Table2 class="w-12 h-12 mx-auto" />
            <p class="font-medium">Select a vendor and database to begin mapping</p>
          </div>
        </div>

        <div v-show="workspaceTab === 'log'" class="h-full flex flex-col p-4 gap-2">
          <textarea v-model="workspaceText"
                    class="textarea w-full flex-1 font-mono text-xs resize-none bg-base-100
                           border border-base-300 focus:outline-none focus:border-primary"
                    placeholder="Extraction log output will appear here…"
                    readonly />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.contents { display: contents; }

.components-panel {
  width: var(--components-width, 320px);
  min-width: 240px;
  flex-shrink: 0;
}
.workspace-panel {
  flex: 1;
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
