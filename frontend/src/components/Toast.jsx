import React, { useEffect, useState } from "react";
import { useApp } from "../store/AppContext";

export default function Toast() {
  const { toast, setToast } = useApp();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (toast?.message) {
      setVisible(true);
      const t = setTimeout(() => {
        setVisible(false);
        setTimeout(() => setToast(null), 300);
      }, Number(toast?.duration) || 3000); // ⟵ 3s par défaut
      return () => clearTimeout(t);
    }
  }, [toast, setToast]);

  if (!toast?.message || !visible) return null;

  const color =
    toast.type === "success" ? "#16a34a" :
    toast.type === "error"   ? "#dc2626" :
                               "#2563eb";

  // ----- VARIANTES D'AFFICHAGE -----
  const isCenter = toast?.position === "center"; // ⟵ nouvelle option

  if (isCenter) {
    // Overlay centré (banner)
    return (
      <div
        role="status"
        aria-live="assertive"
        style={{
          position: "fixed",
          inset: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "rgba(0,0,0,.45)",
          zIndex: 10000,
          padding: 24,
        }}
      >
        <div
          style={{
            maxWidth: 720,
            width: "min(92vw, 720px)",
            textAlign: "center",
            fontSize: 20,           // ⟵ un peu plus grand
            fontWeight: 700,
            lineHeight: 1.3,
            color: "#fff",
            padding: "18px 20px",
            borderRadius: 16,
            boxShadow: "0 20px 50px rgba(0,0,0,.35)",
            border: "1px solid rgba(255,255,255,.08)",
            background: color,
          }}
        >
          {toast.message}
        </div>
      </div>
    );
  }

  // Bottom-right (comportement existant)
  return (
    <div
      role="status"
      aria-live="polite"
      style={{
        position: "fixed",
        right: 16,
        bottom: 16,
        background: color,
        color: "white",
        padding: "12px 14px",
        borderRadius: 12,
        boxShadow: "0 10px 20px rgba(0,0,0,0.2)",
        maxWidth: 420,
        zIndex: 9999,
        fontSize: 14,
      }}
    >
      {toast.message}
    </div>
  );
}
