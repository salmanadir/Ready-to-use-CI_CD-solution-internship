import React from "react";

export default function MetaCards({ items = [] }) {
  return (
    <div className="grid-4">
      {items.map((it, i) => (
        <div key={i} className="card meta">
          <div className="meta-label">{it.label}</div>
          <div className="meta-value" title={it.value}>{it.value || "—"}</div>
        </div>
      ))}
    </div>
  );
}
