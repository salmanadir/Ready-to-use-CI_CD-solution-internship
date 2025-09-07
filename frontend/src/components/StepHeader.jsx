import React from "react";

export default function StepHeader({ title, chips = [], subtitle }) {
  return (
    <div className="step-header">
      <div>
        <h1 className="h1">{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      <div className="chips">
        {/* We keep the container, but we won’t pass chips anymore */}
        {chips.map((c, i) => (
          <span key={i} className={`chip ${c.variant || "default"}`}>{c.label}</span>
        ))}
      </div>
    </div>
  );
}
