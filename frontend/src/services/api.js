// src/services/api.js
let client = null;

// Appelée une seule fois pour brancher l'apiClient authentifié
export const setApiClient = (c) => { client = c; };

// ---- Docker
export async function previewDocker(payload) {
  if (!client) throw new Error("API client not initialized");
  return client.post("/api/workflows/docker/preview", payload);
}
export async function applyDockerfile(payload) {
  if (!client) throw new Error("API client not initialized");
  return client.post("/api/workflows/dockerfile/apply", payload);
}

// ---- CI
export async function previewCi(payload) {
  if (!client) throw new Error("API client not initialized");
  return client.post("/api/workflows/ci/preview", payload);
}
export async function generateCi(payload) {
  if (!client) throw new Error("API client not initialized");
  return client.post("/api/workflows/generate", payload);
}
