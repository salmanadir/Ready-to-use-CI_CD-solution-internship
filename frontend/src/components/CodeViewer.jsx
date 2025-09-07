import React from "react";

export default function CodeViewer({ content = "", onCopy }) {
  return (
    <div className="code-viewer">
      <pre><code>{content || " (empty) Nothing to display."}</code></pre>
      <div className="code-actions">
        <button
          className="btn ghost"
          onClick={() => { navigator.clipboard.writeText(content || ""); onCopy && onCopy(); }}
        >
          Copy
        </button>
      </div>
    </div>
  );
}
