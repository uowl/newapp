import { ref, onMounted } from "vue";

export function useConnectionManager() {
  const savedConnections = ref([]);
  const isLoading = ref(false);

  async function fetchConnections() {
    isLoading.value = true;
    try {
      const res = await fetch("/api/connections");
      if (res.ok) {
        savedConnections.value = await res.json();
      }
    } catch (e) {
      console.error("Failed to fetch connections", e);
    } finally {
      isLoading.value = false;
    }
  }

  async function saveConnection(conn) {
    try {
      const res = await fetch("/api/connections", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(conn)
      });
      if (res.ok) {
        await fetchConnections();
        return true;
      }
    } catch (e) {
      console.error("Failed to save connection", e);
    }
    return false;
  }

  async function deleteConnection(id) {
    try {
      const res = await fetch(`/api/connections/${id}`, {
        method: "DELETE"
      });
      if (res.ok) {
        await fetchConnections();
        return true;
      }
    } catch (e) {
      console.error("Failed to delete connection", e);
    }
    return false;
  }

  onMounted(fetchConnections);

  return {
    savedConnections,
    isLoading,
    fetchConnections,
    saveConnection,
    deleteConnection
  };
}
