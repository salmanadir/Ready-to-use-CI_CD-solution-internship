import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApp } from "../store/AppContext";

const multiSample = `{
  "mode": "multi",
  "defaultBranch": "main",
  "services": [
    {
      "id": "backend-0",
      "stackType": "SPRING_BOOT_MAVEN",
      "workingDirectory": "./backend/backend",
      "buildTool": "maven",
      "language": "Java",
      "projectDetails": { "springBootVersion": "3.5.5", "packaging": "jar" },
      "orchestrator": "github-actions",
      "javaVersion": "21"
    },
    {
      "id": "frontend-1",
      "stackType": "NODE_JS",
      "workingDirectory": "./frontend",
      "buildTool": "npm",
      "language": "JavaScript",
      "projectDetails": { "framework": "React", "nodeVersion": "Latest" },
      "orchestrator": "github-actions",
      "javaVersion": null
    }
  ]
}`;

const singleSample = `{
  "mode": "single",
  "defaultBranch": "main",
  "services": [
    {
      "id": "backend-0",
      "stackType": "SPRING_BOOT_MAVEN",
      "workingDirectory": "./sample-app",
      "buildTool": "maven",
      "language": "Java",
      "projectDetails": { "springBootVersion": "3.5.3", "packaging": "jar" },
      "orchestrator": "github-actions",
      "javaVersion": "21"
    }
  ],
  "analysis": {
    "stackType": "SPRING_BOOT_MAVEN",
    "javaVersion": "21",
    "orchestrator": "github-actions",
    "workingDirectory": "./sample-app",
    "buildTool": "Maven",
    "language": "Java",
    "projectDetails": { "springBootVersion": "3.5.3", "packaging": "jar" }
  }
}`;

export default function Analyze() {
  const { setRepoId, setAnalysis, setDockerOptions } = useApp();
  const nav = useNavigate();

  const [repoIdInput, setRepoIdInput] = useState("17"); // mets 9 pour single si tu veux
  const [jsonText, setJsonText] = useState("");
  const [error, setError] = useState("");

  function loadMulti() {
    setRepoIdInput("17");
    setJsonText(multiSample);
    setError("");
  }
  function loadSingle() {
    setRepoIdInput("9");
    setJsonText(singleSample);
    setError("");
  }
  function clearAll() {
    setRepoIdInput("");
    setJsonText("");
    setError("");
  }

  function continueToDockerfile() {
    try {
      setError("");
      const parsed = JSON.parse(jsonText);
      const idNum = Number(repoIdInput);
      if (!idNum) {
        setError("repoId invalide.");
        return;
      }

      // Remplir le store que lit DockerfilePreview
      setRepoId(idNum);
      setAnalysis(parsed);
      setDockerOptions({ registry: "ghcr.io", imageNameOverride: null });

      nav("/docker/preview");
    } catch (e) {
      setError("JSON invalide : " + (e.message || "erreur de parsing"));
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="step-header">
          <div>
            <h1 className="h1">Analyse (Mock) — Préparer les données</h1>
            <p className="muted">Colle ton JSON d’analyse, choisis un repoId, puis “Continuer”.</p>
          </div>
        </div>

        <div className="card" style={{ marginBottom: 12 }}>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <div style={{ minWidth: 220 }}>
              <label style={{ fontSize: 12, color: "#94a3b8" }}>repoId</label>
              <input
                value={repoIdInput}
                onChange={(e) => setRepoIdInput(e.target.value)}
                placeholder="ex: 17"
                style={{
                  width: "100%", padding: 10, borderRadius: 10,
                  border: "1px solid #1f2937", background: "#0f172a", color: "#e5e7eb"
                }}
              />
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
              <button className="btn" onClick={loadMulti}>Charger exemple MULTI</button>
              <button className="btn ghost" onClick={loadSingle}>Exemple SINGLE</button>
              <button className="btn ghost" onClick={clearAll}>Vider</button>
            </div>
          </div>
        </div>

        <div className="card" style={{ marginBottom: 12 }}>
          <label style={{ fontSize: 12, color: "#94a3b8" }}>Payload JSON d’analyse</label>
          <textarea
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            placeholder='Colle ici le JSON renvoyé par /analyze'
            rows={14}
            style={{
              width: "100%", marginTop: 6, padding: 12, borderRadius: 12,
              border: "1px solid #1f2937", background: "#0a0f1a", color: "#cbd5e1",
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
            }}
          />
          {error && (
            <div style={{ marginTop: 8, color: "#f87171", fontSize: 14 }}>
              {error}
            </div>
          )}
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <button className="btn primary" onClick={continueToDockerfile}>
            Continuer → Dockerfile Preview
          </button>
        </div>
      </div>
    </div>
  );
}
