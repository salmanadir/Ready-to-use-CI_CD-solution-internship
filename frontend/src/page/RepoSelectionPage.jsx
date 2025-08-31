import React, { useState, useEffect, useMemo } from "react";  
import { useNavigate } from "react-router-dom";  
import RepoCard from "../components/RepoCard/RepoCard";  
import SelectButton from "../components/Buttons/SelectButton/SelectButton";  
import SearchBar from "../components/SearchBar/SearchBar";  
import "./RepoSelectionPage.css";  
  
const RepoSelectionPage = () => {  
  const navigate = useNavigate();  
    

  const [repos, setRepos] = useState([]);  
  const [loading, setLoading] = useState(false);  
  const [, setError] = useState(null);  
    
 
  const [query, setQuery] = useState("");  
  const [visibleCount, setVisibleCount] = useState(6);  
  const [displayedRepos, setDisplayedRepos] = useState([]);  
  const [visibility, setVisibility] = useState("all");  
    
  
  const [modalOpen, setModalOpen] = useState(false);  
  const [selectedRepo, setSelectedRepo] = useState(null);  
  const [selectLoading, setSelectLoading] = useState(false);  
  
  // Simulation temporaire d'authentification JWT  
  const simulateAuth = () => {  
   
    const mockJWT = 'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJBc21hSG1pZGE5OSIsImdpdGh1YklkIjoxNTYyNDM4MDcsInVzZXJJZCI6MSwiaWF0IjoxNzU2NjAyNjIwLCJleHAiOjE3NTY2Mzg2MjB9.poMb_6HeAvBDN7dFAmHk4FpHbmufdAoTCpJw5sLyOqI4QZjApWDlnNTHVzuuwG-O';  
    localStorage.setItem('authToken', mockJWT);  
  };  
  
  // Headers pour les appels API  
  const getAuthHeaders = () => ({  
    'Authorization': `Bearer ${localStorage.getItem('authToken')}`,  
    'Content-Type': 'application/json'  
  });  
  
  // Charger les repositories depuis le backend  
  const loadRepos = async () => {  
    setLoading(true);  
    setError(null);  
      
    try {  
      const response = await fetch('http://localhost:8080/api/repositories/available', {  
        headers: getAuthHeaders()  
      });  
        
      if (!response.ok) {  
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);  
      }  
        
      const data = await response.json();  
        
      if (data.success) {  
        setRepos(data.repositories);  
      } else {  
        throw new Error(data.message || 'Erreur lors du chargement des repositories');  
      }  
    } catch (error) {  
      console.error('Erreur lors du chargement des repositories:', error);  
      setError(error.message);  
    } finally {  
      setLoading(false);  
    }  
  };  
  
  // Sélectionner un repository via l'API backend  
  const handleSelectRepo = async (repo) => {  
    setSelectLoading(true);  
      
    try {  
      const response = await fetch('http://localhost:8080/api/repositories/select', {  
        method: 'POST',  
        headers: getAuthHeaders(),  
        body: JSON.stringify({ repoData: repo })  
      });  
        
      if (!response.ok) {  
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);  
      }  
        
      const data = await response.json();  
        
      if (data.success) {  
        console.log("Repository sélectionné:", data.repository);  
          
        // Naviguer vers la page d'analyse avec l'ID du repository  
        navigate(`/analysis/${data.repository.repoId}`, {  
          state: { repo: data.repository }  
        });  
      } else {  
        throw new Error(data.message || 'Erreur lors de la sélection');  
      }  
    } catch (error) {  
      console.error('Erreur lors de la sélection:', error);  
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
      base = base.filter(r =>  
        r.full_name.toLowerCase().includes(q) ||  
        (r.language?.toLowerCase() || "").includes(q) ||  
        (r.description?.toLowerCase() || "").includes(q)  
      );  
    }  
  
    if (visibility === "public") base = base.filter(r => !r.private);  
    if (visibility === "private") base = base.filter(r => r.private);  
  
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
    // Simuler l'authentification pour le développement  
    if (!localStorage.getItem('authToken')) {  
      simulateAuth();  
    }  
      
    // Charger les repositories  
    loadRepos();  
  }, []);  
  
  const handleShowMore = () => setVisibleCount(prev => prev + 6);  
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
  
  return (  
    <div className="repo-selection-page">  
      <h1>Your <span>repositories</span></h1>  
  
      {/* Barre de recherche et filtres */}  
      <div className="searchbar-row">  
        <SearchBar  
          placeholder="Rechercher par nom, langage…"  
          onChange={setQuery}  
        />  
          
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
  
      {/* Indicateur de chargement seulement */}  
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
              <button  
                className="btn btn-secondary"  
                onClick={cancelAnalyze}  
                disabled={selectLoading}  
              >  
                No  
              </button>  
              <button  
                className="btn btn-primary"  
                onClick={confirmAnalyze}  
                disabled={selectLoading}  
              >  
                {selectLoading ? 'Sélection...' : 'Yes'}  
              </button>  
            </div>  
          </div>  
        </div>  
      )}  
    </div>  
  );  
};  
  
export default RepoSelectionPage;