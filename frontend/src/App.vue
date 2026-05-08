<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useTheme } from "vuetify";

const theme = useTheme();
const isDarkTheme = ref(false);
const isRefreshing = ref(false);
const vendorQuery = ref("");
const selectedVendor = ref(null);
const vendorMenuOpen = ref(false);
const workspaceText = ref("");
const workspaceTab = ref("mapping");
const componentsWidth = ref(360);
const gridRef = ref(null);
const conversionWorkspaceTab = ref("plan");
const conversionNotes = ref("");
const conversionSource = ref("SQL Server");
const conversionTarget = ref("PostgreSQL");
const conversionSourceQuery = ref("SQL Server");
const conversionTargetQuery = ref("PostgreSQL");
const conversionSourceMenuOpen = ref(false);
const conversionTargetMenuOpen = ref(false);
const conversionBatchSize = ref("5000");
const hostname = ref("");
const port = ref("");
const database = ref("");
const username = ref("");
const password = ref("");
const projectId = ref("");
const clientProjectName = ref("");
const projectEmail = ref("");
const databases = ref([]);
const databaseQuery = ref("");
const databaseMenuOpen = ref(false);
const isLoadingDatabases = ref(false);
const databaseError = ref("");
const uiScale = ref(1);
const COMPONENTS_MIN = 220;
const COMPONENTS_MAX = 560;
const UI_SCALE_MIN = 1;
const UI_SCALE_MAX = 1.35;
const UI_SCALE_STEP = 0.05;

const resizeState = {
  active: false,
  startX: 0,
  startComponentsWidth: 0,
  pendingClientX: 0,
  rafId: 0,
  metrics: null,
  liveComponentsWidth: 0
};

const vendors = ref([
  "Epic",
  "Cerner",
  "MEDITECH",
  "Allscripts",
  "athenahealth",
  "eClinicalWorks",
  "NextGen Healthcare",
  "Greenway Health",
  "Practice Fusion",
  "DrChrono"
]);

const navItems = [
  { id: "dashboard", label: "Extraction", icon: "mdi-view-dashboard-outline" },
  { id: "db-conversion", label: "DB Conversion", icon: "mdi-database-refresh-outline" }
];

const activeNav = ref(navItems[0].id);
const activeNavLabel = computed(() => navItems.find(n => n.id === activeNav.value)?.label || "Dashboard");

const sortedVendors = computed(() =>
  [...vendors.value].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: "base" }))
);

const filteredVendors = computed(() => {
  const q = vendorQuery.value.trim().toLowerCase();
  if (!q) return sortedVendors.value;
  return sortedVendors.value.filter(v => v.toLowerCase().includes(q));
});

const filteredDatabases = computed(() => {
  const q = databaseQuery.value.trim().toLowerCase();
  if (!q) return databases.value;
  return databases.value.filter(db => db.toLowerCase().includes(q));
});

const uiScalePercent = computed(() => Math.round(uiScale.value * 100));
const conversionDbTypes = [
  "SQL Server",
  "PostgreSQL",
  "MySQL",
  "MariaDB",
  "Oracle",
  "MongoDB",
  "Cassandra",
  "Snowflake",
  "Redshift",
  "BigQuery",
  "DB2",
  "SQLite"
];
const filteredConversionSources = computed(() => {
  const q = conversionSourceQuery.value.trim().toLowerCase();
  if (!q) return conversionDbTypes;
  return conversionDbTypes.filter(db => db.toLowerCase().includes(q));
});
const filteredConversionTargets = computed(() => {
  const q = conversionTargetQuery.value.trim().toLowerCase();
  if (!q) return conversionDbTypes;
  return conversionDbTypes.filter(db => db.toLowerCase().includes(q));
});

function applyTheme() {
  const name = isDarkTheme.value ? "dark" : "light";
  theme.global.name.value = name;
  localStorage.setItem("emr_theme", name);
}

function toggleTheme() {
  isDarkTheme.value = !isDarkTheme.value;
  applyTheme();
}

function increaseScale() {
  uiScale.value = Math.min(UI_SCALE_MAX, Math.round((uiScale.value + UI_SCALE_STEP) * 100) / 100);
}

function decreaseScale() {
  uiScale.value = Math.max(UI_SCALE_MIN, Math.round((uiScale.value - UI_SCALE_STEP) * 100) / 100);
}

async function loadVendors() {
  if (isRefreshing.value) return;
  isRefreshing.value = true;
  try {
    const response = await fetch("/api/vendors");
    if (!response.ok) return;
    const data = await response.json();
    if (!Array.isArray(data)) return;
    vendors.value = data.map(item => String(item));
    if (selectedVendor.value && !vendors.value.includes(selectedVendor.value)) {
      selectedVendor.value = null;
    }
  } catch (error) {
    console.error("Failed to load vendors", error);
  } finally {
    isRefreshing.value = false;
  }
}

function handleKeyDown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    const input = document.getElementById("vendor-search");
    if (input) input.focus();
  }
}

function handleVendorInput() {
  vendorMenuOpen.value = true;
  const exactMatch = sortedVendors.value.find(v => v.toLowerCase() === vendorQuery.value.trim().toLowerCase());
  selectedVendor.value = exactMatch || null;
}

function chooseVendor(vendor) {
  selectedVendor.value = vendor;
  vendorQuery.value = vendor;
  vendorMenuOpen.value = false;
}

function clearVendorSearch() {
  vendorQuery.value = "";
  selectedVendor.value = null;
  vendorMenuOpen.value = true;
}

function toggleVendorMenu() {
  vendorMenuOpen.value = !vendorMenuOpen.value;
}

function closeVendorMenuIfOutside(event) {
  const vendorWrap = document.querySelector(".vendor-input-wrap");
  if (vendorWrap && !vendorWrap.contains(event.target)) {
    vendorMenuOpen.value = false;
  }
  const databaseWrap = document.querySelector(".database-input-wrap");
  if (databaseWrap && !databaseWrap.contains(event.target)) {
    databaseMenuOpen.value = false;
  }
  const sourceWrap = document.querySelector(".conversion-source-wrap");
  if (sourceWrap && !sourceWrap.contains(event.target)) {
    conversionSourceMenuOpen.value = false;
  }
  const targetWrap = document.querySelector(".conversion-target-wrap");
  if (targetWrap && !targetWrap.contains(event.target)) {
    conversionTargetMenuOpen.value = false;
  }
}

function handleSourceInput() {
  conversionSourceMenuOpen.value = true;
  const exactMatch = conversionDbTypes.find(db => db.toLowerCase() === conversionSourceQuery.value.trim().toLowerCase());
  conversionSource.value = exactMatch || "";
}

function handleTargetInput() {
  conversionTargetMenuOpen.value = true;
  const exactMatch = conversionDbTypes.find(db => db.toLowerCase() === conversionTargetQuery.value.trim().toLowerCase());
  conversionTarget.value = exactMatch || "";
}

function chooseConversionSource(dbType) {
  conversionSource.value = dbType;
  conversionSourceQuery.value = dbType;
  conversionSourceMenuOpen.value = false;
}

function chooseConversionTarget(dbType) {
  conversionTarget.value = dbType;
  conversionTargetQuery.value = dbType;
  conversionTargetMenuOpen.value = false;
}

function toggleSourceMenu() {
  conversionSourceMenuOpen.value = !conversionSourceMenuOpen.value;
}

function toggleTargetMenu() {
  conversionTargetMenuOpen.value = !conversionTargetMenuOpen.value;
}

function clearSourceQuery() {
  conversionSource.value = "";
  conversionSourceQuery.value = "";
  conversionSourceMenuOpen.value = true;
}

function clearTargetQuery() {
  conversionTarget.value = "";
  conversionTargetQuery.value = "";
  conversionTargetMenuOpen.value = true;
}

function handleDatabaseInput() {
  databaseMenuOpen.value = true;
  const exactMatch = databases.value.find(db => db.toLowerCase() === databaseQuery.value.trim().toLowerCase());
  database.value = exactMatch || "";
}

function chooseDatabase(dbName) {
  database.value = dbName;
  databaseQuery.value = dbName;
  databaseMenuOpen.value = false;
  databaseError.value = "";
}

function clearDatabaseSearch() {
  database.value = "";
  databaseQuery.value = "";
  databaseMenuOpen.value = true;
}

function toggleDatabaseMenu() {
  databaseMenuOpen.value = !databaseMenuOpen.value;
}

async function loadSqlServerDatabases() {
  if (isLoadingDatabases.value) return;
  databaseError.value = "";
  isLoadingDatabases.value = true;
  try {
    const response = await fetch("/api/sqlserver/databases", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        hostname: hostname.value,
        port: port.value,
        username: username.value,
        password: password.value
      })
    });

    if (!response.ok) {
      let message = "Unable to connect to SQL Server";
      try {
        const errorPayload = await response.json();
        if (errorPayload?.error) {
          message = String(errorPayload.error);
        }
      } catch (parseError) {
        console.warn("Failed to parse SQL Server error payload", parseError);
      }
      databaseError.value = message;
      return;
    }

    const dbList = await response.json();
    if (!Array.isArray(dbList)) {
      databaseError.value = "Unexpected response while loading databases";
      return;
    }

    databases.value = dbList.map(item => String(item));
    databaseMenuOpen.value = true;
    const exactMatch = databases.value.find(db => db.toLowerCase() === databaseQuery.value.trim().toLowerCase());
    if (exactMatch) {
      database.value = exactMatch;
      databaseQuery.value = exactMatch;
    } else if (database.value && !databases.value.includes(database.value)) {
      database.value = "";
    }
  } catch (error) {
    databaseError.value = "Unable to reach server";
    console.error("Failed to load SQL Server databases", error);
  } finally {
    isLoadingDatabases.value = false;
  }
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function getLayoutMetrics() {
  const grid = document.querySelector(".workspace-grid");
  if (!grid) {
    return null;
  }
  const rect = grid.getBoundingClientRect();
  return {
    totalWidth: rect.width,
    iconRailWidth: 62,
    splitterWidth: 10,
    workspaceMin: 280,
    componentsMin: COMPONENTS_MIN,
    componentsMax: COMPONENTS_MAX
  };
}

function applyGridWidths(components) {
  const grid = gridRef.value;
  if (!grid) return;
  grid.style.setProperty("--components-width", components + "px");
}

function applyResizeFrame() {
  resizeState.rafId = 0;
  if (!resizeState.active || !resizeState.metrics) return;

  const metrics = resizeState.metrics;
  const deltaX = resizeState.pendingClientX - resizeState.startX;
  const maxComponents = metrics.totalWidth
    - metrics.iconRailWidth
    - metrics.workspaceMin
    - metrics.splitterWidth;
  const allowedMax = Math.min(metrics.componentsMax, maxComponents);
  resizeState.liveComponentsWidth = clamp(
    resizeState.startComponentsWidth + deltaX,
    metrics.componentsMin,
    Math.max(metrics.componentsMin, allowedMax)
  );
}

function onResizeMove(event) {
  if (!resizeState.active) return;
  resizeState.pendingClientX = event.clientX;
  if (!resizeState.rafId) {
    resizeState.rafId = requestAnimationFrame(applyResizeFrame);
  }
}

function stopResize() {
  if (!resizeState.active) return;
  componentsWidth.value = Math.round(resizeState.liveComponentsWidth || componentsWidth.value);
  applyGridWidths(componentsWidth.value);
  resizeState.active = false;
  resizeState.metrics = null;
  resizeState.pendingClientX = 0;
  if (resizeState.rafId) {
    cancelAnimationFrame(resizeState.rafId);
    resizeState.rafId = 0;
  }
  document.body.classList.remove("resizing-columns");
  gridRef.value?.classList.remove("resizing-active");
  window.removeEventListener("mousemove", onResizeMove);
  window.removeEventListener("mouseup", stopResize);
}

function startResize(event) {
  const metrics = getLayoutMetrics();
  if (!metrics) return;

  resizeState.active = true;
  resizeState.startX = event.clientX;
  resizeState.pendingClientX = event.clientX;
  resizeState.startComponentsWidth = componentsWidth.value;
  resizeState.liveComponentsWidth = componentsWidth.value;
  resizeState.metrics = metrics;
  document.body.classList.add("resizing-columns");
  gridRef.value?.classList.add("resizing-active");
  window.addEventListener("mousemove", onResizeMove);
  window.addEventListener("mouseup", stopResize);
}

onMounted(() => {
  const savedTheme = localStorage.getItem("emr_theme");
  const savedScale = Number(localStorage.getItem("emr_ui_scale"));
  isDarkTheme.value = savedTheme ? savedTheme === "dark" : false;
  if (!Number.isNaN(savedScale)) {
    uiScale.value = clamp(savedScale, UI_SCALE_MIN, UI_SCALE_MAX);
  }
  componentsWidth.value = clamp(componentsWidth.value, COMPONENTS_MIN, COMPONENTS_MAX);
  applyTheme();
  applyGridWidths(componentsWidth.value);
  loadVendors();
  window.addEventListener("keydown", handleKeyDown);
  window.addEventListener("click", closeVendorMenuIfOutside);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleKeyDown);
  window.removeEventListener("click", closeVendorMenuIfOutside);
  stopResize();
});

watch(uiScale, newValue => {
  localStorage.setItem("emr_ui_scale", String(newValue));
});
</script>

<template>
  <v-app class="layout-root">
    <v-main class="h-screen">
      <div ref="gridRef" class="workspace-grid" :style="{ '--ui-scale': uiScale }">
        <aside class="icon-rail">
          <div class="rail-top">
            <div class="brand-pill">
              <v-icon size="18" icon="mdi-database-sync-outline" />
            </div>

            <v-btn
              v-for="item in navItems"
              :key="item.id"
              :icon="item.icon"
              :title="item.label"
              :ripple="false"
              class="rail-btn"
              :class="{ active: activeNav === item.id }"
              variant="text"
              size="x-small"
              @click="activeNav = item.id"
            />
          </div>

          <div class="rail-bottom">
            <div class="scale-control" title="UI scale">
              <button
                type="button"
                class="scale-btn"
                aria-label="Increase UI scale"
                :disabled="uiScale >= UI_SCALE_MAX"
                @click="increaseScale"
              >
                <v-icon icon="mdi-plus" size="14" />
              </button>
              <span class="scale-value">{{ uiScalePercent }}%</span>
              <button
                type="button"
                class="scale-btn"
                aria-label="Decrease UI scale"
                :disabled="uiScale <= UI_SCALE_MIN"
                @click="decreaseScale"
              >
                <v-icon icon="mdi-minus" size="14" />
              </button>
            </div>
            <v-btn
              :icon="isDarkTheme ? 'mdi-white-balance-sunny' : 'mdi-weather-night'"
              :title="isDarkTheme ? 'Switch to light mode' : 'Switch to dark mode'"
              :ripple="false"
              class="rail-btn"
              variant="text"
              size="x-small"
              @click="toggleTheme"
            />
          </div>
        </aside>

        <div v-show="activeNav === 'dashboard'" class="nav-view">
          <section class="panel components-panel">
            <div class="panel-title">
              <div>
                <h2 class="panel-heading">Extraction</h2>
              </div>
            </div>
            <div class="panel-context-header">Extraction Workspace</div>

            <v-sheet class="form-group" border rounded="lg">
              <div class="panel-title">
                <div class="form-group-title">EMR Vendors</div>
                <v-chip size="small" color="primary" variant="tonal">{{ vendors.length }}</v-chip>
              </div>
              <div class="vendor-input-wrap">
                <v-icon icon="mdi-magnify" size="15" class="vendor-search-icon" />
                <input
                  id="vendor-search"
                  v-model="vendorQuery"
                  type="text"
                  class="vendor-input"
                  placeholder="Search Vendor"
                  @focus="vendorMenuOpen = true"
                  @input="handleVendorInput"
                />
                <button
                  v-if="vendorQuery"
                  type="button"
                  class="vendor-icon-btn"
                  aria-label="Clear search"
                  @click="clearVendorSearch"
                >
                  <v-icon icon="mdi-close-circle" size="14" />
                </button>
                <button
                  type="button"
                  class="vendor-icon-btn"
                  aria-label="Toggle vendor menu"
                  @click="toggleVendorMenu"
                >
                  <v-icon :icon="vendorMenuOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="16" />
                </button>
                <div v-if="vendorMenuOpen" class="vendor-menu">
                  <button
                    v-for="vendor in filteredVendors"
                    :key="vendor"
                    type="button"
                    class="vendor-option"
                    @click="chooseVendor(vendor)"
                  >
                    {{ vendor }}
                  </button>
                  <div v-if="filteredVendors.length === 0" class="vendor-empty">No vendors found</div>
                </div>
              </div>
              <v-sheet class="selection-box" rounded="lg">
                <span class="text-caption text-medium-emphasis">Current Selection</span>
                <strong>{{ selectedVendor || "None Selected" }}</strong>
              </v-sheet>
            </v-sheet>

            <v-sheet class="form-group" border rounded="lg">
              <div class="form-group-title">Project Details</div>
              <div class="project-grid-2x3">
                <label class="field-label" for="project-id-input">Project ID</label>
                <input
                  id="project-id-input"
                  v-model="projectId"
                  type="text"
                  class="field-input"
                  placeholder="Project ID"
                />

                <label class="field-label" for="project-name-input">Client/Project Name</label>
                <input
                  id="project-name-input"
                  v-model="clientProjectName"
                  type="text"
                  class="field-input"
                  placeholder="Client/Project Name"
                />

                <label class="field-label" for="project-email-input">Email</label>
                <input
                  id="project-email-input"
                  v-model="projectEmail"
                  type="email"
                  class="field-input"
                  placeholder="Email"
                />
              </div>
            </v-sheet>

            <v-sheet class="form-group" border rounded="lg">
              <div class="form-group-title">Server</div>
              <div class="server-grid-2x5">
                <label class="field-label" for="hostname-input">Hostname</label>
                <input
                  id="hostname-input"
                  v-model="hostname"
                  type="text"
                  class="field-input"
                  placeholder="Hostname"
                />

                <label class="field-label" for="port-input">Port</label>
                <input
                  id="port-input"
                  v-model="port"
                  type="text"
                  class="field-input"
                  placeholder="Port"
                />

                <label class="field-label" for="username-input">Username</label>
                <input
                  id="username-input"
                  v-model="username"
                  type="text"
                  class="field-input"
                  placeholder="Username"
                />

                <label class="field-label" for="password-input">Password</label>
                <input
                  id="password-input"
                  v-model="password"
                  type="password"
                  class="field-input"
                  placeholder="Password"
                />

                <label class="field-label" for="database-input">Database</label>
                <div class="database-stack-control">
                  <label class="field-label field-label-blank" aria-hidden="true">&nbsp;</label>
                  <button
                    type="button"
                    class="database-load-btn"
                    :disabled="isLoadingDatabases"
                    @click="loadSqlServerDatabases"
                  >
                    {{ isLoadingDatabases ? "Loading..." : "Load" }}
                  </button>
                  <div class="database-input-wrap">
                    <v-icon icon="mdi-database-search-outline" size="14" class="database-search-icon" />
                    <input
                      id="database-input"
                      v-model="databaseQuery"
                      type="text"
                      class="database-input"
                      placeholder="Search database"
                      @focus="databaseMenuOpen = true"
                      @input="handleDatabaseInput"
                    />
                    <button
                      v-if="databaseQuery"
                      type="button"
                      class="database-icon-btn"
                      aria-label="Clear database search"
                      @click="clearDatabaseSearch"
                    >
                      <v-icon icon="mdi-close-circle" size="14" />
                    </button>
                    <button
                      type="button"
                      class="database-icon-btn"
                      aria-label="Toggle database menu"
                      @click="toggleDatabaseMenu"
                    >
                      <v-icon :icon="databaseMenuOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="16" />
                    </button>
                    <div v-if="databaseMenuOpen" class="database-menu">
                      <button
                        v-for="dbName in filteredDatabases"
                        :key="dbName"
                        type="button"
                        class="database-option"
                        @click="chooseDatabase(dbName)"
                      >
                        {{ dbName }}
                      </button>
                      <div v-if="filteredDatabases.length === 0" class="database-empty">No databases found</div>
                    </div>
                  </div>
                </div>
              </div>
              <p v-if="databaseError" class="database-error">{{ databaseError }}</p>
            </v-sheet>
          </section>

          <div class="column-resizer" @mousedown="startResize($event)" />

          <main class="panel workspace-panel">
            <div class="workspace-head">
              <div>
                <p class="label-kicker">Execution Workspace</p>
                <h2 class="panel-heading">{{ activeNavLabel }}</h2>
              </div>
            </div>

            <v-tabs v-model="workspaceTab" color="primary" density="compact" class="workspace-tabs" height="36">
              <v-tab value="mapping" class="workspace-tab" :ripple="false">Mapping</v-tab>
              <v-tab value="preview" class="workspace-tab" :ripple="false">Preview</v-tab>
              <v-tab value="logs" class="workspace-tab" :ripple="false">Logs</v-tab>
            </v-tabs>

            <v-window v-model="workspaceTab" class="workspace-window">
              <v-window-item value="mapping" :transition="false" :reverse-transition="false">
                <v-textarea
                  v-model="workspaceText"
                  placeholder="Write mapping notes, transform rules, or runbook details..."
                  variant="outlined"
                  class="workspace-editor"
                  rows="18"
                  no-resize
                  hide-details
                />
              </v-window-item>
              <v-window-item value="preview" :transition="false" :reverse-transition="false">
                <v-sheet class="workspace-empty" rounded="lg" border>
                  <v-icon icon="mdi-table-eye" size="28" />
                  <p>Preview output records will appear here.</p>
                </v-sheet>
              </v-window-item>
              <v-window-item value="logs" :transition="false" :reverse-transition="false">
                <v-sheet class="workspace-empty" rounded="lg" border>
                  <v-icon icon="mdi-file-document-outline" size="28" />
                  <p>Execution logs will appear here.</p>
                </v-sheet>
              </v-window-item>
            </v-window>
          </main>
        </div>

        <div v-show="activeNav === 'db-conversion'" class="nav-view">
          <section class="panel components-panel">
            <div class="panel-title">
              <div>
                <h2 class="panel-heading">DB Conversion</h2>
              </div>
            </div>
            <div class="panel-context-header">DB Conversion Workspace</div>

            <v-sheet class="form-group" border rounded="lg">
              <div class="form-group-title">Pipeline</div>
              <div class="project-grid-2x3">
                <label class="field-label" for="source-type-input">Source</label>
                <div class="conversion-select-wrap conversion-source-wrap">
                  <v-icon icon="mdi-database-search-outline" size="14" class="vendor-search-icon" />
                  <input
                    id="source-type-input"
                    v-model="conversionSourceQuery"
                    type="text"
                    class="vendor-input"
                    placeholder="Search source DB"
                    @focus="conversionSourceMenuOpen = true"
                    @input="handleSourceInput"
                  />
                  <button
                    v-if="conversionSourceQuery"
                    type="button"
                    class="vendor-icon-btn"
                    aria-label="Clear source"
                    @click="clearSourceQuery"
                  >
                    <v-icon icon="mdi-close-circle" size="14" />
                  </button>
                  <button
                    type="button"
                    class="vendor-icon-btn"
                    aria-label="Toggle source menu"
                    @click="toggleSourceMenu"
                  >
                    <v-icon :icon="conversionSourceMenuOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="16" />
                  </button>
                  <div v-if="conversionSourceMenuOpen" class="vendor-menu">
                    <button
                      v-for="dbType in filteredConversionSources"
                      :key="'source-' + dbType"
                      type="button"
                      class="vendor-option"
                      @click="chooseConversionSource(dbType)"
                    >
                      {{ dbType }}
                    </button>
                    <div v-if="filteredConversionSources.length === 0" class="vendor-empty">No DB types found</div>
                  </div>
                </div>

                <label class="field-label" for="target-type-input">Target</label>
                <div class="conversion-select-wrap conversion-target-wrap">
                  <v-icon icon="mdi-database-search-outline" size="14" class="vendor-search-icon" />
                  <input
                    id="target-type-input"
                    v-model="conversionTargetQuery"
                    type="text"
                    class="vendor-input"
                    placeholder="Search target DB"
                    @focus="conversionTargetMenuOpen = true"
                    @input="handleTargetInput"
                  />
                  <button
                    v-if="conversionTargetQuery"
                    type="button"
                    class="vendor-icon-btn"
                    aria-label="Clear target"
                    @click="clearTargetQuery"
                  >
                    <v-icon icon="mdi-close-circle" size="14" />
                  </button>
                  <button
                    type="button"
                    class="vendor-icon-btn"
                    aria-label="Toggle target menu"
                    @click="toggleTargetMenu"
                  >
                    <v-icon :icon="conversionTargetMenuOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" size="16" />
                  </button>
                  <div v-if="conversionTargetMenuOpen" class="vendor-menu">
                    <button
                      v-for="dbType in filteredConversionTargets"
                      :key="'target-' + dbType"
                      type="button"
                      class="vendor-option"
                      @click="chooseConversionTarget(dbType)"
                    >
                      {{ dbType }}
                    </button>
                    <div v-if="filteredConversionTargets.length === 0" class="vendor-empty">No DB types found</div>
                  </div>
                </div>

                <label class="field-label" for="batch-size-input">Batch Size</label>
                <input
                  id="batch-size-input"
                  v-model="conversionBatchSize"
                  type="text"
                  class="field-input"
                  placeholder="Batch Size"
                />
              </div>
            </v-sheet>
          </section>

          <div class="column-resizer" @mousedown="startResize($event)" />

          <main class="panel workspace-panel">
            <div class="workspace-head">
              <div>
                <p class="label-kicker">DB Conversion Workspace</p>
                <h2 class="panel-heading">DB Conversion</h2>
              </div>
            </div>

            <v-tabs
              v-model="conversionWorkspaceTab"
              color="primary"
              density="compact"
              class="workspace-tabs"
              height="36"
            >
              <v-tab value="plan" class="workspace-tab" :ripple="false">Plan</v-tab>
              <v-tab value="mapping" class="workspace-tab" :ripple="false">Mapping</v-tab>
              <v-tab value="logs" class="workspace-tab" :ripple="false">Logs</v-tab>
            </v-tabs>

            <v-window v-model="conversionWorkspaceTab" class="workspace-window">
              <v-window-item value="plan" :transition="false" :reverse-transition="false">
                <v-sheet class="workspace-empty" rounded="lg" border>
                  <v-icon icon="mdi-source-branch" size="28" />
                  <p>Conversion execution plan will appear here.</p>
                </v-sheet>
              </v-window-item>
              <v-window-item value="mapping" :transition="false" :reverse-transition="false">
                <v-textarea
                  v-model="conversionNotes"
                  placeholder="Define source-target mapping rules for DB conversion..."
                  variant="outlined"
                  class="workspace-editor"
                  rows="18"
                  no-resize
                  hide-details
                />
              </v-window-item>
              <v-window-item value="logs" :transition="false" :reverse-transition="false">
                <v-sheet class="workspace-empty" rounded="lg" border>
                  <v-icon icon="mdi-file-document-outline" size="28" />
                  <p>DB conversion logs will appear here.</p>
                </v-sheet>
              </v-window-item>
            </v-window>
          </main>
        </div>
      </div>
    </v-main>
  </v-app>
</template>

<style scoped>
.workspace-grid {
  --ui-scale: 1;
  height: 100vh;
  display: flex;
  background: rgb(var(--v-theme-background));
  min-width: 0;
  overflow-x: auto;
  color: rgb(var(--v-theme-on-surface));
}

.nav-view {
  display: contents;
}

.icon-rail {
  width: calc(62px * var(--ui-scale));
  min-width: calc(62px * var(--ui-scale));
  border-right: 1px solid rgba(var(--v-border-color), 0.2);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: calc(12px * var(--ui-scale)) calc(8px * var(--ui-scale));
  background: color-mix(in srgb, rgb(var(--v-theme-surface)) 90%, rgb(var(--v-theme-primary)) 10%);
}

.rail-top {
  display: flex;
  flex-direction: column;
  gap: calc(6px * var(--ui-scale));
  align-items: center;
}

.brand-pill {
  width: calc(34px * var(--ui-scale));
  height: calc(34px * var(--ui-scale));
  border-radius: calc(10px * var(--ui-scale));
  display: grid;
  place-items: center;
  color: rgb(var(--v-theme-on-surface));
  background: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 14%, transparent);
  margin-bottom: calc(4px * var(--ui-scale));
}

.rail-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: calc(8px * var(--ui-scale));
}

.scale-control {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: calc(4px * var(--ui-scale));
}

.scale-btn {
  width: calc(22px * var(--ui-scale));
  height: calc(22px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.32);
  border-radius: calc(6px * var(--ui-scale));
  background: rgb(var(--v-theme-surface));
  color: rgb(var(--v-theme-on-surface));
  display: grid;
  place-items: center;
  padding: 0;
  cursor: pointer;
}

.scale-btn:hover:not(:disabled) {
  border-color: rgba(var(--v-theme-primary), 0.55);
}

.scale-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.scale-value {
  font-size: calc(10px * var(--ui-scale));
  line-height: 1;
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 78%, transparent);
}

.rail-btn {
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 72%, transparent);
  opacity: 1;
  transition: none !important;
}

.rail-btn.active {
  color: rgb(var(--v-theme-primary));
  opacity: 1;
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 16%, transparent);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: calc(10px * var(--ui-scale));
  padding: calc(12px * var(--ui-scale));
  border-right: 1px solid rgba(var(--v-border-color), 0.2);
  background: color-mix(in srgb, rgb(var(--v-theme-surface)) 96%, rgb(var(--v-theme-background)) 4%);
  min-height: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.label-kicker {
  margin: 0 0 2px;
  font-size: calc(9px * var(--ui-scale));
  text-transform: uppercase;
  opacity: 0.72;
  letter-spacing: 0;
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 72%, transparent);
}

.panel-title h2,
.workspace-head h2 {
  margin: 0;
  font-size: calc(16px * var(--ui-scale));
  font-weight: 700;
  line-height: 1.15;
}

.panel-heading {
  color: rgb(var(--v-theme-on-surface));
}

.panel-context-header {
  height: calc(28px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.25);
  border-radius: calc(7px * var(--ui-scale));
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 12%, rgb(var(--v-theme-surface)) 88%);
  color: rgb(var(--v-theme-on-surface));
  display: flex;
  align-items: center;
  padding: 0 calc(9px * var(--ui-scale));
  font-size: calc(11px * var(--ui-scale));
  font-weight: 600;
}

.components-panel {
  flex: 0 0 auto;
  width: var(--components-width, 300px);
  min-width: 250px;
  will-change: width;
}

.workspace-panel {
  flex: 1 1 0;
  min-width: 280px;
  border-right: 0;
}

.column-resizer {
  flex: 0 0 10px;
  width: 10px;
  cursor: col-resize;
  position: relative;
  background: transparent;
}

.column-resizer::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 4px;
  width: 1px;
  background: rgba(var(--v-border-color), 0.22);
}

.column-resizer:hover::before {
  background: rgb(var(--v-theme-primary));
}

.workspace-grid.resizing-active * {
  transition: none !important;
}

.workspace-grid.resizing-active .workspace-window,
.workspace-grid.resizing-active .workspace-window *,
.workspace-grid.resizing-active .panel,
.workspace-grid.resizing-active .panel * {
  pointer-events: none !important;
}

.selection-box {
  padding: calc(8px * var(--ui-scale)) calc(10px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.2);
  display: flex;
  flex-direction: column;
  gap: calc(3px * var(--ui-scale));
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 10%, rgb(var(--v-theme-surface)) 90%);
  color: rgb(var(--v-theme-on-surface));
}

.form-group {
  padding: calc(10px * var(--ui-scale));
  border-color: rgba(var(--v-border-color), 0.22) !important;
  background: color-mix(in srgb, rgb(var(--v-theme-surface)) 94%, rgb(var(--v-theme-primary)) 6%);
  display: flex;
  flex-direction: column;
  gap: calc(10px * var(--ui-scale));
}

.form-group-title {
  font-size: calc(13px * var(--ui-scale));
  font-weight: 600;
  color: rgb(var(--v-theme-on-surface));
}

.server-grid-2x5 {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  grid-template-rows: repeat(5, minmax(calc(30px * var(--ui-scale)), auto));
  column-gap: calc(6px * var(--ui-scale));
  row-gap: calc(6px * var(--ui-scale));
  align-items: center;
}

.project-grid-2x3 {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  grid-template-rows: repeat(3, minmax(calc(30px * var(--ui-scale)), auto));
  column-gap: calc(6px * var(--ui-scale));
  row-gap: calc(6px * var(--ui-scale));
  align-items: center;
}

.field-label {
  margin: 0;
  white-space: nowrap;
  line-height: 1;
  font-size: calc(11px * var(--ui-scale));
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 75%, transparent);
}

.field-label-blank {
  visibility: hidden;
}

.field-input {
  width: 100%;
  height: calc(30px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: calc(7px * var(--ui-scale));
  background: rgb(var(--v-theme-surface));
  color: rgb(var(--v-theme-on-surface));
  padding: 0 calc(8px * var(--ui-scale));
  font-size: calc(12px * var(--ui-scale));
  outline: none;
}

.field-input:focus {
  border-color: rgb(var(--v-theme-primary));
}

.database-stack-control {
  display: flex;
  flex-direction: column;
  gap: calc(6px * var(--ui-scale));
  min-width: 0;
}

.database-input-wrap {
  height: calc(30px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: calc(7px * var(--ui-scale));
  background: rgb(var(--v-theme-surface));
  display: flex;
  align-items: center;
  gap: calc(4px * var(--ui-scale));
  padding: 0 calc(6px * var(--ui-scale));
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
}

.database-search-icon {
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 70%, transparent);
  opacity: 1;
}

.database-input {
  border: 0;
  outline: none;
  width: 100%;
  height: 100%;
  background: transparent;
  color: rgb(var(--v-theme-on-surface));
  font-size: calc(12px * var(--ui-scale));
}

.database-input::placeholder {
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 55%, transparent);
}

.database-icon-btn {
  border: 0;
  background: transparent;
  color: rgba(var(--v-theme-on-surface), 0.7);
  width: calc(16px * var(--ui-scale));
  height: calc(16px * var(--ui-scale));
  border-radius: calc(4px * var(--ui-scale));
  display: grid;
  place-items: center;
  padding: 0;
  cursor: pointer;
  flex: 0 0 auto;
}

.database-icon-btn:hover {
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 12%, transparent);
  color: rgb(var(--v-theme-primary));
}

.database-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  width: 100%;
  max-height: 180px;
  overflow: auto;
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: 8px;
  background: color-mix(in srgb, rgb(var(--v-theme-surface)) 98%, rgb(var(--v-theme-background)) 2%);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  z-index: 20;
}

.database-option {
  width: 100%;
  border: 0;
  background: transparent;
  color: rgb(var(--v-theme-on-surface));
  text-align: left;
  padding: calc(7px * var(--ui-scale)) calc(9px * var(--ui-scale));
  font-size: calc(12px * var(--ui-scale));
  cursor: pointer;
}

.database-option:hover {
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 14%, transparent);
}

.database-empty {
  padding: calc(8px * var(--ui-scale)) calc(10px * var(--ui-scale));
  font-size: calc(12px * var(--ui-scale));
  opacity: 0.78;
}

.database-load-btn {
  align-self: stretch;
  width: 100%;
  height: calc(30px * var(--ui-scale));
  min-width: 0;
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: calc(7px * var(--ui-scale));
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 12%, rgb(var(--v-theme-surface)) 88%);
  color: rgb(var(--v-theme-on-surface));
  font-size: calc(12px * var(--ui-scale));
  padding: 0 calc(8px * var(--ui-scale));
  cursor: pointer;
}

.database-load-btn:hover:not(:disabled) {
  border-color: rgba(var(--v-theme-primary), 0.55);
}

.database-load-btn:disabled {
  opacity: 0.65;
  cursor: default;
}

.database-error {
  margin: 2px 0 0;
  color: rgb(var(--v-theme-error));
  font-size: calc(11px * var(--ui-scale));
}

.workspace-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: calc(12px * var(--ui-scale));
}

.workspace-tabs {
  border-bottom: 1px solid rgba(var(--v-border-color), 0.2);
}

.workspace-tab {
  min-width: calc(84px * var(--ui-scale));
  letter-spacing: 0;
  font-size: calc(12px * var(--ui-scale));
}

.workspace-window {
  flex: 1;
  min-height: 0;
}

.workspace-editor {
  height: 100%;
}

.workspace-empty {
  height: calc(100vh - 210px);
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: calc(8px * var(--ui-scale));
  opacity: 0.8;
  text-align: center;
}

:deep(.v-btn.v-btn--size-x-small) {
  --v-btn-height: 26px;
}

:deep(.v-field--variant-solo-filled .v-field__input) {
  min-height: 30px;
  padding-top: 4px;
  padding-bottom: 4px;
}

:deep(.v-list-item) {
  min-height: 30px;
}

.vendor-input-wrap {
  height: calc(32px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: calc(8px * var(--ui-scale));
  background: rgb(var(--v-theme-surface));
  display: flex;
  align-items: center;
  padding: 0 calc(8px * var(--ui-scale));
  gap: calc(6px * var(--ui-scale));
  position: relative;
}

.conversion-select-wrap {
  height: calc(30px * var(--ui-scale));
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: calc(7px * var(--ui-scale));
  background: rgb(var(--v-theme-surface));
  display: flex;
  align-items: center;
  padding: 0 calc(8px * var(--ui-scale));
  gap: calc(6px * var(--ui-scale));
  position: relative;
}

.vendor-search-icon {
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 70%, transparent);
  opacity: 1;
}

.vendor-input {
  border: 0;
  outline: none;
  width: 100%;
  height: 100%;
  background: transparent;
  color: rgb(var(--v-theme-on-surface));
  font-size: calc(12px * var(--ui-scale));
}

.vendor-input::placeholder {
  color: color-mix(in srgb, rgb(var(--v-theme-on-surface)) 55%, transparent);
}

.vendor-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  width: 100%;
  max-height: 180px;
  overflow: auto;
  border: 1px solid rgba(var(--v-border-color), 0.35);
  border-radius: 8px;
  background: color-mix(in srgb, rgb(var(--v-theme-surface)) 98%, rgb(var(--v-theme-background)) 2%);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  z-index: 20;
}

.vendor-icon-btn {
  border: 0;
  background: transparent;
  color: rgba(var(--v-theme-on-surface), 0.7);
  width: calc(18px * var(--ui-scale));
  height: calc(18px * var(--ui-scale));
  border-radius: calc(4px * var(--ui-scale));
  display: grid;
  place-items: center;
  padding: 0;
  cursor: pointer;
  flex: 0 0 auto;
}

.vendor-icon-btn:hover {
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 12%, transparent);
  color: rgb(var(--v-theme-primary));
}

.vendor-option {
  width: 100%;
  border: 0;
  background: transparent;
  color: rgb(var(--v-theme-on-surface));
  text-align: left;
  padding: calc(7px * var(--ui-scale)) calc(9px * var(--ui-scale));
  font-size: calc(12px * var(--ui-scale));
  cursor: pointer;
}

.vendor-option:hover {
  background: color-mix(in srgb, rgb(var(--v-theme-primary)) 14%, transparent);
}

.vendor-empty {
  padding: calc(8px * var(--ui-scale)) calc(10px * var(--ui-scale));
  font-size: calc(12px * var(--ui-scale));
  opacity: 0.78;
}

@media (max-width: 1200px) {
  .components-panel {
    min-width: 230px;
  }

  .workspace-panel {
    min-width: 240px;
  }
}

@media (max-width: 980px) {
  .components-panel {
    flex-basis: 220px;
    min-width: 220px;
  }

  .workspace-panel {
    flex-basis: 360px;
    min-width: 360px;
  }
}
</style>
