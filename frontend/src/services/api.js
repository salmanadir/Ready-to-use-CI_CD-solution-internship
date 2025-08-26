const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

// Lis un éventuel JWT mis par ton collègue après login
function authHeaders() {
  const headers = { "Content-Type": "application/json" };
  const token = localStorage.getItem("AUTH_TOKEN");
  if (token) headers["Authorization"] = `Bearer ${token}`; // 👈 ajoute l'entête
  return headers;
}

async function postJSON(path, body) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(json?.message || `HTTP ${res.status}`);
    err.status = res.status;
    err.payload = json;
    throw err;
  }
  return json;
}

export function previewDocker(payload) {
  return postJSON("/api/workflows/docker/preview", payload);
}

export function applyDockerfile(payload) {
  return postJSON("/api/workflows/dockerfile/apply", payload);
}

export function previewCi(payload) {
  // par défaut on reste prudent: UPDATE_IF_EXISTS
  return postJSON("/api/workflows/ci/preview", {
    fileHandlingStrategy: "UPDATE_IF_EXISTS",
    ...payload,
  });
}

export function generateCi(payload) {
  return postJSON("/api/workflows/generate", {
    fileHandlingStrategy: "UPDATE_IF_EXISTS",
    ...payload,
  });
}
