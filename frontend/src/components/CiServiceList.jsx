import React from "react";

function nice(name) {
  if (!name || name === ".") return "root";
  const clean = String(name).replace(/^\.\//, "");
  const parts = clean.split("/").filter(Boolean);
  return parts[parts.length - 1];
}

export default function CiServiceList({ previews = [], selectedWD, onSelect }) {
  return (
    <div className="service-list">
      {previews.map((p) => {
        const active = selectedWD === p.service;
        const statusLabel =
          p.status === "NOT_FOUND" ? "To create" :
          p.status === "DIFFERENT" ? "Will update" :
          "Up to date";
        const statusClass =
          p.status === "NOT_FOUND" ? "warn" :
          p.status === "DIFFERENT" ? "info" : "ok";

        return (
          <button
            key={p.service || "."}
            className={`service-item ${active ? "active" : ""}`}
            onClick={() => onSelect(p.service || ".")}
          >
            <div className="svc-title">{nice(p.service)}</div>
            <div className="svc-sub">
              <span className="badge">{p.filePath?.split("/").pop() || "ci.yml"}</span>
              <span className={`badge ${statusClass}`}>{statusLabel}</span>
            </div>
            <div className="svc-path" title={p.filePath || "(will be created)"}>
              {p.filePath || "(will be created)"}
            </div>
          </button>
        );
      })}
    </div>
  );
}
