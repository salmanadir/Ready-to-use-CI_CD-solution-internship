import React from "react";

export default function StickyActions({ primary, secondary, right }) {
  return (
    <div className="sticky-actions">
      <div className="left">
        {secondary?.label && (
          <button className="btn ghost" disabled={secondary.disabled} onClick={secondary.onClick}>
            {secondary.label}
          </button>
        )}
      </div>
      <div className="right">
        {right}
        {primary?.label && (
          <button className="btn primary" disabled={primary.disabled} onClick={primary.onClick}>
            {primary.label}
          </button>
        )}
      </div>
    </div>
  );
}
