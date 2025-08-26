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
      }, 3000);
      return () => clearTimeout(t);
    }
  }, [toast, setToast]);

  if (!toast?.message || !visible) return null;

  const color =
    toast.type === "success" ? "#16a34a" : toast.type === "error" ? "#dc2626" : "#2563eb";

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
