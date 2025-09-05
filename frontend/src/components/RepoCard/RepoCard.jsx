import React from "react";
import SelectButton from "../Buttons/SelectButton/SelectButton";
import "./RepoCard.css";

const RepoCard = ({ repo, onSelect, loading }) => {
  return (
    <div className="repo-card">
      <div className="repo-info">
        <div className="repo-header">
          <h3 className="repo-title">
            <a href={repo.html_url} target="_blank" rel="noopener noreferrer">
              {repo.full_name || "full-name"}
            </a>
          </h3>

          <span className={`repo-type ${repo.private ? "private" : "public"}`}>
            {repo.private ? "private" : "public"}
          </span>
        </div>

        <div className="repo-details">
          <span className="repo-techno">
            <span className="dot"></span> {repo.language || "techno"}
          </span>
          <span className="repo-update">
            {repo.updated_at ? `Updated ${new Date(repo.updated_at).toLocaleDateString()}` : "Update on .."}
          </span>
        </div>
      </div>

      <SelectButton
        onClick={() => onSelect(repo)}
        loading={loading}
        className="select-button"
      >
        choose
      </SelectButton>
    </div>
  );
};

export default RepoCard;
