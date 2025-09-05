import React from "react";
import "./SelectButton.css";

const SelectButton = ({
  children = "choose",
  onClick,
  disabled = false,
  loading = false,
  className = "",
}) => {
  return (
    <button
      className={`custom-button ${className} ${disabled ? "disabled" : ""}`}
      onClick={onClick}
      disabled={disabled || loading}
    >
      {loading ? "Chargement..." : children}
    </button>
  );
};

export default SelectButton;
