import React from "react";

export default function StickyActions({ primary, secondary, right, center }) {
  // secondary peut être un objet {label, onClick, disabled} ou un tableau de ces objets
  const secondaries = Array.isArray(secondary)
    ? secondary
    : secondary
    ? [secondary]
    : [];

  return (
    <div className="sticky-actions">
      <div className="left">
        {secondaries.map((s, i) =>
          s?.label ? (
            <button
              key={i}
              className="btn ghost"
              disabled={s.disabled}
              onClick={s.onClick}
              title={s.title}
              aria-label={s.ariaLabel || s.label}
            >
              {s.label}
            </button>
          ) : null
        )}
      </div>

      {/* Slot central optionnel */}
      <div
        className="center"
        style={{
          flex: 1,
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          gap: 8,
          flexWrap: "wrap",
        }}
      >
        {center}
      </div>

      <div className="right">
        {right}
        {primary?.label && (
          <button
            className="btn primary"
            disabled={primary.disabled}
            onClick={primary.onClick}
            title={primary.title}
            aria-label={primary.ariaLabel || primary.label}
          >
            {primary.label}
          </button>
        )}
      </div>
    </div>
  );
}
