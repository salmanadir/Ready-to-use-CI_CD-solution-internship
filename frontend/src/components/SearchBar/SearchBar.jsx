import React from "react";
import "./SearchBar.css";

const SearchBar = ({ placeholder = "find a repository...", onChange }) => {
  return (
    <input
      type="text"
      className="search-bar"
      placeholder={placeholder}
      onChange={(e) => onChange && onChange(e.target.value)}
    />
  );
};

export default SearchBar;
