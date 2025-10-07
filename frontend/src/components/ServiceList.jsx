import React from "react";

function serviceNameOf(workingDirectory = "", fallback = "service") {
  if (!workingDirectory) return fallback;
  const clean = workingDirectory.replace(/^\.\//, "");
  const parts = clean.split("/").filter(Boolean);
  return parts.length ? parts[parts.length - 1] : fallback;
}

export default function ServiceList({ services = [], plansByWD = {}, selectedWD, onSelect }) {
  return (
    <div className="service-list">
      {services.map((svc) => {
        const wd = svc.workingDirectory;
        const plan = plansByWD[wd] || {};
        const status = plan.shouldGenerateDockerfile ? "To generate" : "Existing";
        const statusClass = plan.shouldGenerateDockerfile ? "warn" : "ok";
        const active = selectedWD === wd;

        return (
          <button
            key={wd || svc.id}
            className={`service-item ${active ? "active" : ""}`}
            onClick={() => onSelect(wd)}
          >
            <div className="svc-title">{serviceNameOf(wd, svc.id)}</div>
            <div className="svc-sub">
              <span className="badge">{String(svc.buildTool || "").toUpperCase()}</span>
              <span className={`badge ${statusClass}`}>{status}</span>
            </div>
            <div className="svc-path" title={plan.dockerfilePath || "(will be created)"}>
              {plan.dockerfilePath || "(will be created)"}
            </div>
          </button>
        );
      })}
    </div>
  );
}
