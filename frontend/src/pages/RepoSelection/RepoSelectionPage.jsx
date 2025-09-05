import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import RepoCard from "../../components/RepoCard/RepoCard";
import SelectButton from "../../components/Buttons/SelectButton/SelectButton";
import SearchBar from "../../components/SearchBar/SearchBar";
import "./RepoSelectionPage.css";

const RepoSelectionPage = () => {
  const navigate = useNavigate();
  const { apiClient } = useAuth(); // contexte d'authentification

  const [repos, setRepos] = useState([]);
  const [loading, setLoading] = useState(false);

  const [query, setQuery] = useState("");
  const [visibleCount, setVisibleCount] = useState(6);
  const [displayedRepos, setDisplayedRepos] = useState([]);
  const [visibility, setVisibility] = useState("all");

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedRepo, setSelectedRepo] = useState(null);
  const [selectLoading, setSelectLoading] = useState(false);

  // Charger les repositories depuis le backend avec apiClient
  const loadRepos = async () => {
    setLoading(true);
    try {
      const data = await apiClient.get("/api/repositories/available");
      if (data?.success) {
        setRepos(data.repositories);
      } else {
        throw new Error(data?.message || "Erreur lors du chargement des repositories");
      }
    } catch (error) {
      console.error("Erreur lors du chargement des repositories:", error);
    } finally {
      setLoading(false);
    }
  };

  // Sélectionner un repository via l'API backend avec apiClient
  const handleSelectRepo = async (repo) => {
    setSelectLoading(true);
    try {
      const data = await apiClient.post("/api/repositories/select", { repoData: repo });
      if (data?.success) {
        console.log("Repository sélectionné:", data.repository);
        navigate(`/analysis/${data.repository.repoId}`, {
          state: { repo: data.repository },
        });
      } else {
        throw new Error(data?.message || "Erreur lors de la sélection");
      }
    } catch (error) {
      console.error("Erreur lors de la sélection:", error);
      alert(`Erreur: ${error.message}`);
    } finally {
      setSelectLoading(false);
    }
  };

  // Filtrage des repositories
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let base = repos;

    if (q) {
      base = base.filter(
        (r) =>
          r.full_name.toLowerCase().includes(q) ||
          (r.language?.toLowerCase() || "").includes(q) ||
          (r.description?.toLowerCase() || "").includes(q)
      );
    }

    if (visibility === "public") base = base.filter((r) => !r.private);
    if (visibility === "private") base = base.filter((r) => r.private);

    return base;
  }, [repos, query, visibility]);

  // Pagination
  useEffect(() => {
    if (query.trim()) {
      setDisplayedRepos(filtered);
    } else {
      setDisplayedRepos(filtered.slice(0, visibleCount));
    }
  }, [filtered, query, visibleCount]);

  // Initialisation au chargement de la page
  useEffect(() => {
    if (apiClient) {
      loadRepos();
    }
  }, [apiClient]);

  const handleShowMore = () => setVisibleCount((prev) => prev + 6);
  const hasMoreRepos = !query.trim() && visibleCount < filtered.length;

  // Actions du modal
  const openConfirm = (repo) => {
    setSelectedRepo(repo);
    setModalOpen(true);
  };

  const confirmAnalyze = () => {
    setModalOpen(false);
    if (selectedRepo) {
      handleSelectRepo(selectedRepo);
    }
  };

  const cancelAnalyze = () => {
    setModalOpen(false);
    setSelectedRepo(null);
  };

  // Afficher un message de chargement si l'utilisateur n'est pas encore authentifié
  if (!apiClient) {
    return (
      <div className="repo-selection-page">
        <div className="loading-indicator">
          <p>Chargement de l'authentification...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="repo-selection-page">
      <h1>
        Your <span>repositories</span>
      </h1>

      {/* Barre de recherche et filtres */}
      <div className="searchbar-row">
        <SearchBar placeholder="Search by name, language…" onChange={setQuery} />

        <select
          className="visibility-filter"
          value={visibility}
          onChange={(e) => setVisibility(e.target.value)}
          aria-label="Filtrer par visibilité"
          title="Filtrer"
        >
          <option value="all">All</option>
          <option value="public">Public</option>
          <option value="private">Private</option>
        </select>
      </div>

      {/* Indicateur de chargement */}
      {loading && (
        <div className="loading-indicator">
          <p>Chargement des repositories...</p>
        </div>
      )}

      {/* Grille des repositories */}
      <div className="repo-grid">
        {displayedRepos.map((repo) => (
          <RepoCard
            key={repo.id}
            repo={repo}
            onSelect={openConfirm}
            loading={selectLoading && selectedRepo?.id === repo.id}
          />
        ))}
      </div>

      {/* Bouton "Voir plus" */}
      {hasMoreRepos && (
        <div className="show-more-container">
          <SelectButton onClick={handleShowMore}>voir plus</SelectButton>
        </div>
      )}

      {/* Modal de confirmation */}
      {modalOpen && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-title"
          onClick={cancelAnalyze}
        >
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 id="confirm-title">Confirm analysis</h3>
            </div>
            <div className="modal-body">
              Do you really want to analyze this repo?
              <br />
              <small className="modal-subtle">{selectedRepo?.full_name}</small>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={cancelAnalyze} disabled={selectLoading}>
                No
              </button>
              <button className="btn btn-primary" onClick={confirmAnalyze} disabled={selectLoading}>
                {selectLoading ? "Sélection..." : "Yes"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RepoSelectionPage;
