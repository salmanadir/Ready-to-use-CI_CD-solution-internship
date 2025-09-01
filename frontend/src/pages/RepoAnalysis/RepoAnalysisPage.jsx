import React, { useEffect, useMemo, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import "./RepoAnalysisPage.css";

// ⚠️ Assure-toi d'avoir installé FA et importé le CSS global :
// npm i @fortawesome/fontawesome-free
// puis dans index.js ou App.jsx :
// import "@fortawesome/fontawesome-free/css/all.min.css";

// -------------------- Helpers --------------------
const ND = "Not detected";
const safe = (v) => (v === undefined || v === null || v === "" ? ND : v);

// Un service est supporté s'il est SPRING_BOOT (Maven/Gradle) ou NODE_JS
function isSupportedService(service) {
  const st = service?.stackType || "";
  if (st.includes("SPRING_BOOT")) {
    const bt = (service?.buildTool || "").toUpperCase();
    if (bt !== "MAVEN" && bt !== "GRADLE") {
      return {
        ok: false,
        reason:
          "Spring Boot détecté mais build tool non supporté (Maven/Gradle requis).",
      };
    }
    return { ok: true };
  }
  if (st === "NODE_JS") return { ok: true };
  return { ok: false, reason: `Stack non supportée: ${safe(st)}.` };
}

// Normalise les champs (remplace les valeurs manquantes par "Not detected")
function normalizeService(s = {}) {
  return {
    ...s,
    id: safe(s.id),
    stackType: safe(s.stackType),
    buildTool: safe(s.buildTool),
    workingDirectory: safe(s.workingDirectory),
    javaVersion: safe(s.javaVersion),
    language: safe(s.language),
    projectDetails: {
      ...s.projectDetails,
      springBootVersion: safe(s.projectDetails?.springBootVersion),
      framework: safe(s.projectDetails?.framework),
      nodeVersion: safe(s.projectDetails?.nodeVersion),
    },
    databaseType: safe(s.databaseType),
    databaseName: safe(s.databaseName),
  };
}

function validateAndNormalizeAnalysis(raw) {
  const errors = [];

  if (raw?.mode === "multi" && Array.isArray(raw?.services)) {
    const normalized = raw.services.map(normalizeService);
    const supported = [];
    for (const srv of normalized) {
      const chk = isSupportedService(srv);
      if (chk.ok) supported.push(srv);
      else errors.push(chk.reason);
    }
    return {
      ok: supported.length > 0,
      data: { ...raw, services: supported, mode: "multi" },
      errors,
    };
  }

  if (raw?.mode === "single" && raw?.analysis) {
    const s = normalizeService(raw.analysis);
    const chk = isSupportedService(s);
    if (!chk.ok) errors.push(chk.reason);
    return {
      ok: chk.ok,
      data: { ...raw, analysis: s, mode: "single" },
      errors,
    };
  }

  errors.push("Format d'analyse inattendu.");
  return { ok: false, data: raw, errors };
}

// -------------------- Headers pour appels API --------------------
const getAuthHeaders = () => ({
  Authorization: `Bearer ${localStorage.getItem("authToken")}`,
  "Content-Type": "application/json",
});

// -------------------- API calls --------------------
// Fonction pour récupérer l'analyse complète du repository
async function fetchRepositoryAnalysis(repo) {
  try {
    if (!repo || !repo.repoId) {
      throw new Error("Repository ID not available");
    }

    const response = await fetch(
      `http://localhost:8080/api/stack-analysis/analyze/${repo.repoId}`,
      {
        method: "POST",
        headers: getAuthHeaders(),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const data = await response.json();

    if (data.success) {
      return data;
    } else {
      throw new Error(data.message || "Erreur lors de l'analyse du repository");
    }
  } catch (error) {
    console.error("Erreur lors de l'analyse:", error);
    throw error;
  }
}

// Récupère les vrais fichiers du repository
async function fetchRepoFiles(repo) {
  if (!repo || !repo.repoId) {
    throw new Error("Repository ID not available");
  }

  const response = await fetch(
    `http://localhost:8080/api/stack-analysis/repository/${repo.repoId}/all-files`,
    { headers: getAuthHeaders() }
  );

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const data = await response.json();
  if (!data.success) {
    throw new Error(data.message || "Erreur lors du chargement des fichiers");
  }
  return data.files || [];
}

// -------------------- Arbre de fichiers --------------------
/** Construit un arbre {name,type,path,children[]} à partir d'une liste de chemins */
function buildTree(paths) {
  const root = { name: "", type: "dir", path: "", children: new Map() };
  for (const p of paths) {
    if (!p) continue;
    const parts = p.split("/");
    let node = root;
    let acc = "";
    parts.forEach((part, idx) => {
      acc = acc ? `${acc}/${part}` : part;
      const isFile = idx === parts.length - 1 && /\.[^./\\]+$/.test(part);
      if (!node.children.has(part)) {
        node.children.set(part, {
          name: part,
          type: isFile ? "file" : "dir",
          path: acc,
          children: new Map(),
        });
      }
      node = node.children.get(part);
    });
  }
  const mapToArray = (n) => {
    const childrenArr = Array.from(n.children.values())
      .sort((a, b) =>
        a.type === b.type ? a.name.localeCompare(b.name) : a.type === "dir" ? -1 : 1
      )
      .map(mapToArray);
    return { ...n, children: childrenArr };
  };
  return mapToArray(root);
}

/** Rendu récursif de l'arbre avec <details>/<summary> */
function TreeNode({ node }) {
  if (node.type === "dir") {
    return (
      <details className="tree-dir" open>
        {node.name && (
          <summary>
            <span className="tree-icon">📁</span>
            {node.name}
          </summary>
        )}
        <div className="tree-children">
          {node.children.map((c) => (
            <TreeNode key={c.path} node={c} />
          ))}
        </div>
      </details>
    );
  }
  return (
    <div className="tree-file">
      <span className="tree-icon">📄</span>
      {node.name}
    </div>
  );
}

// -------------------- Composant principal --------------------
export default function RepoAnalysisPage() {
  const location = useLocation();
  const [params] = useSearchParams();

  const repo = location.state?.repo || null;
  const repoFullName = repo?.fullName || params.get("repo") || "unknown/repo";

  // files
  const [allFiles, setAllFiles] = useState([]);
  const [visibleFiles, setVisibleFiles] = useState([]);
  const [filesError, setFilesError] = useState(null);

  // analysis modal
  const [analysisOpen, setAnalysisOpen] = useState(true);
  const [isAnalyzing, setIsAnalyzing] = useState(true);
  const [analysis, setAnalysis] = useState(null);
  const [analysisError, setAnalysisError] = useState(null);

  // bouton "Continue"
  const [showContinueButton, setShowContinueButton] = useState(false);

  // État pour gérer l'édition des valeurs
  const [editingField, setEditingField] = useState(null);
  const [editingValue, setEditingValue] = useState("");
  const [editingPath, setEditingPath] = useState("");

  // charger fichiers puis les révéler un par un
  useEffect(() => {
    let interval;
    let alive = true;

    (async () => {
      try {
        setFilesError(null);
        const files = await fetchRepoFiles(repo);
        if (!alive) return;
        setAllFiles(files);

        let i = 0;
        interval = setInterval(() => {
          i++;
          setVisibleFiles(files.slice(0, i));
          if (i >= files.length) clearInterval(interval);
        }, 120);
      } catch (e) {
        if (!alive) return;
        setFilesError(e.message || "Impossible de charger les fichiers.");
      }
    })();

    return () => {
      alive = false;
      clearInterval(interval);
    };
  }, [repo]);

  // Lancer l'analyse technique du repository
  useEffect(() => {
    if (!repo) return;

    let alive = true;

    (async () => {
      try {
        setIsAnalyzing(true);
        setAnalysisError(null);
        const analysisData = await fetchRepositoryAnalysis(repo);

        // validation + normalisation
        const result = validateAndNormalizeAnalysis(analysisData);

        if (!alive) return;

        if (!result.ok) {
          const msg = result.errors?.length
            ? `Projet non supporté. Seuls Spring Boot (Maven/Gradle) et Node.js sont pris en charge.\n• ${result.errors.join(
                "\n• "
              )}`
            : "Projet non supporté. Seuls Spring Boot (Maven/Gradle) et Node.js sont pris en charge.";
          setAnalysisError(msg);
          setIsAnalyzing(false);
          return;
        }

        setAnalysis({ ...result.data, _errors: result.errors || [] });
        setIsAnalyzing(false);
      } catch (error) {
        if (!alive) return;
        setAnalysisError(error.message);
        setIsAnalyzing(false);
      }
    })();

    return () => {
      alive = false;
    };
  }, [repo]);

  // construit l'arbre dynamiquement
  const tree = useMemo(() => buildTree(visibleFiles), [visibleFiles]);

  // Fonction pour démarrer l'édition d'un champ
  const startEditing = (path, value) => {
    setEditingPath(path);
    setEditingValue(value);
    setEditingField(path);
  };

  // -------------------- SAUVEGARDE BACKEND --------------------
  const saveAnalysisToBackend = async () => {
    try {
      if (!analysis) {
        console.error("Aucune analyse disponible pour la sauvegarde");
        return;
      }

      const updatedData = {};

      if (analysis.mode === "multi" && Array.isArray(analysis.services)) {
        analysis.services.forEach((service, index) => {
          if (service) {
            updatedData[`services.${index}.buildTool`] = service.buildTool || "NONE";
            updatedData[`services.${index}.javaVersion`] = service.javaVersion || "NONE";
            updatedData[`services.${index}.workingDirectory`] = service.workingDirectory || ".";
            updatedData[`services.${index}.language`] = service.language || "NONE";

            if (service.projectDetails) {
              updatedData[`services.${index}.projectDetails.springBootVersion`] = 
                service.projectDetails.springBootVersion || "NONE";
              updatedData[`services.${index}.projectDetails.framework`] = 
                service.projectDetails.framework || "NONE";
              updatedData[`services.${index}.projectDetails.nodeVersion`] = 
                service.projectDetails.nodeVersion || "NONE";
            }
          }
        });

        updatedData.databaseType = analysis.databaseType || "NONE";
        updatedData.databaseName = analysis.databaseName || "my_database";
      } else if (analysis.mode === "single" && analysis.analysis) {
        updatedData["analysis.buildTool"] = analysis.analysis.buildTool || "NONE";
        updatedData["analysis.javaVersion"] = analysis.analysis.javaVersion || "NONE";
        updatedData["analysis.workingDirectory"] = analysis.analysis.workingDirectory || ".";
        updatedData["analysis.language"] = analysis.analysis.language || "NONE";
        updatedData["analysis.databaseType"] = analysis.analysis.databaseType || "NONE";
        updatedData["analysis.databaseName"] = analysis.analysis.databaseName || "my_database";

        if (analysis.analysis.projectDetails) {
          updatedData["analysis.projectDetails.springBootVersion"] = 
            analysis.analysis.projectDetails.springBootVersion || "NONE";
          updatedData["analysis.projectDetails.framework"] = 
            analysis.analysis.projectDetails.framework || "NONE";
          updatedData["analysis.projectDetails.nodeVersion"] = 
            analysis.analysis.projectDetails.nodeVersion || "NONE";
        }
      }

      console.log("Données à envoyer:", updatedData);

      const response = await fetch(
        `http://localhost:8080/api/stack-analysis/repository/${repo.repoId}/update-parameters`,
        {
          method: "PUT",
          headers: getAuthHeaders(),
          body: JSON.stringify(updatedData),
        }
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      if (data.success) {
        alert("Paramètres sauvegardés avec succès !");
      }
    } catch (error) {
      console.error("Erreur lors de la sauvegarde:", error);
      alert(`Erreur lors de la sauvegarde: ${error.message}`);
    }
  };

  // -------------------- ÉDITION LOCALE --------------------
 const saveEdit = async () => {  
  if (!analysis || !editingPath) return;  
  
  const pathParts = editingPath.split(".");  
  const updatedAnalysis = JSON.parse(JSON.stringify(analysis)); // Deep clone  
  
  // Naviguer dans l'objet pour trouver le champ à modifier  
  let current = updatedAnalysis;  
  for (let i = 0; i < pathParts.length - 1; i++) {  
    if (!current[pathParts[i]]) {  
      current[pathParts[i]] = {};  
    }  
    current = current[pathParts[i]];  
  }  
  
  // Mettre à jour la valeur  
  current[pathParts[pathParts.length - 1]] = editingValue;  
  
  // IMPORTANT: Préserver les propriétés critiques pour l'affichage  
  if (analysis.mode) updatedAnalysis.mode = analysis.mode;  
  if (analysis._errors) updatedAnalysis._errors = analysis._errors;  
  
  // Debug pour diagnostiquer les problèmes  
  console.log("Avant mise à jour:", analysis);  
  console.log("Après mise à jour:", updatedAnalysis);  
  console.log("Path modifié:", editingPath, "Nouvelle valeur:", editingValue);  
  
  setAnalysis(updatedAnalysis);  
  setEditingField(null);  
  setEditingValue("");  
  setEditingPath("");  
};

  // Fonction pour annuler l'édition
  const cancelEdit = () => {
    setEditingField(null);
    setEditingValue("");
    setEditingPath("");
  };

  // Fonction pour gérer les changements dans l'input d'édition
  const handleEditChange = (e) => {
    setEditingValue(e.target.value);
  };

  // Fonction pour gérer la touche Entrée dans l'input d'édition
  const handleEditKeyDown = (e) => {
    if (e.key === "Enter") {
      saveEdit();
    } else if (e.key === "Escape") {
      cancelEdit();
    }
  };

  // -------------------- ACTIONS DES BOUTONS --------------------
  const onValidateGenerateCI = async () => {
    // Afficher le message de confirmation
    const confirmMessage = "Please make sure of the parameters' values because these parameters will be used in the next process. Do you want to continue?";
    
    if (window.confirm(confirmMessage)) {
      // SEULEMENT ICI : Sauvegarder automatiquement après confirmation
      await saveAnalysisToBackend();
      
      // Procéder à la génération CI
      alert("CI generation triggered (stub).");
      setAnalysisOpen(false);
      setShowContinueButton(false);
    }
  };

  const onCancel = () => {
    // PAS de sauvegarde automatique - juste fermer
    setAnalysisOpen(false);
    setShowContinueButton(true);
  };

  const onContinueAnalysis = () => {
    setAnalysisOpen(true);
    setShowContinueButton(false);
  };

  // Composant pour afficher un champ avec possibilité d'édition
  const EditableField = ({ label, value, path }) => {
    const isEditing = editingField === path;

    return (
      <div className="editable-field">
        <strong>{label}:</strong>
        {isEditing ? (
          <div className="edit-input-container">
            <input
              type="text"
              value={editingValue}
              onChange={handleEditChange}
              onKeyDown={handleEditKeyDown}
              autoFocus
              className="edit-input"
            />
            <button onClick={saveEdit} className="edit-confirm-btn">✓</button>
            <button onClick={cancelEdit} className="edit-cancel-btn">✗</button>
          </div>
        ) : (
          <span className="editable-value">
            {value}
            <i
              className="fa-solid fa-pen-to-square edit-icon"
              title="Edit"
              onClick={() => startEditing(path, value)}
              style={{ cursor: "pointer", marginLeft: "5px" }}
            ></i>
          </span>
        )}
      </div>
    );
  };

  return (
    <div className="analysis-page">
      <div className="analysis-header">
        <h2>
          Analyzing <span>{repoFullName}</span>
        </h2>
      </div>

      {showContinueButton && !isAnalyzing && (
        <div className="continue-analysis">
          <p>
            Analysis completed! Continue to generate CI configuration for this repository.
          </p>
          <button className="continue-btn" onClick={onContinueAnalysis}>
            Continue Analysis &amp; Generate CI
          </button>
        </div>
      )}

      <div className="file-panel">
        <h3>Repository structure</h3>

        {filesError ? (
          <div className="error">{filesError}</div>
        ) : (
          <div className="tree-root">
            {tree.children?.map((n) => (
              <TreeNode key={n.path} node={n} />
            ))}
          </div>
        )}
      </div>

      {analysisOpen && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          onClick={() => !isAnalyzing && setAnalysisOpen(false)}
        >
          <div className="modal modal-large" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Repository Analysis</h3>
            </div>
            <div className="modal-body">
              {/* Bandeau d'avertissement si des services ont été filtrés */}
              {Array.isArray(analysis?._errors) && analysis._errors.length > 0 && (
                <div className="analysis-warning">
                  <strong>Attention :</strong>
                  <ul>
                    {analysis._errors.map((e, i) => (
                      <li key={i}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}

              {isAnalyzing ? (
                <div className="analysis-loading">
                  <span className="spinner" /> Analyzing repository stack...
                </div>
              ) : analysisError ? (
                <div className="analysis-error">
                  <h4>❌ Analysis Failed</h4>
                  <pre style={{ whiteSpace: "pre-wrap" }}>{analysisError}</pre>
                </div>
              ) : analysis ? (
                <div className="analysis-results">
                  <div className="service-section">
                    <div>
                      <h4>Orchestrator:</h4>
                      GitHub Actions{" "}
                    </div>
                  </div>

                  {/* Total Services */}
                  <div className="analysis-item">
                    <strong>Total Services:</strong> {analysis.services?.length || 0}{" "}
                  </div>

                  {/* MODE MULTI */}
                  {analysis.mode === "multi" && analysis.services && (
                    <div className="services-details">
                      {analysis.services.map((service, index) => (
                        <div key={index} className="service-section">
                          <h4>
                            {service.id?.includes("backend")
                              ? "Backend:"
                              : service.id?.includes("frontend")
                              ? "Frontend:"
                              : service.stackType === "NODE_JS"
                              ? "Frontend:"
                              : service.stackType?.includes("SPRING_BOOT")
                              ? "Backend:"
                              : `${service.id}:`}
                          </h4>

                          {/* Backend Details */}
                          {service.stackType?.includes("SPRING_BOOT") && (
                            <>
                              <EditableField
                                label="Framework"
                                value="Spring Boot"
                                path={`services.${index}.stackType`}
                              />
                              <EditableField
                                label="Build Tool"
                                value={safe(service.buildTool)}
                                path={`services.${index}.buildTool`}
                              />
                              <EditableField
                                label="Spring Version"
                                value={safe(service.projectDetails?.springBootVersion)}
                                path={`services.${index}.projectDetails.springBootVersion`}
                              />
                              <EditableField
                                label="Working Directory"
                                value={safe(service.workingDirectory)}
                                path={`services.${index}.workingDirectory`}
                              />
                              <EditableField
                                label="Java Version"
                                value={safe(service.javaVersion)}
                                path={`services.${index}.javaVersion`}
                              />
                            </>
                          )}

                          {/* Frontend Details */}
                          {service.stackType === "NODE_JS" && (
                            <>
                              <EditableField
                                label="Stack Type"
                                value="Node.js"
                                path={`services.${index}.stackType`}
                              />
                              <EditableField
                                label="Framework"
                                value={safe(service.projectDetails?.framework)}
                                path={`services.${index}.projectDetails.framework`}
                              />
                              <EditableField
                                label="Build Tool"
                                value={safe(service.buildTool)}
                                path={`services.${index}.buildTool`}
                              />
                              <EditableField
                                label="Language"
                                value={safe(service.language)}
                                path={`services.${index}.language`}
                              />
                              <EditableField
                                label="Node Version"
                                value={safe(service.projectDetails?.nodeVersion)}
                                path={`services.${index}.projectDetails.nodeVersion`}
                              />
                            </>
                          )}
                        </div>
                      ))}

                      {/* Database Section */}
                      {analysis.services.some((s) => s.stackType?.includes("SPRING_BOOT")) && (
                        <div className="service-section">
                          <h4>Base de données:</h4>
                          <EditableField
                            label="Database Type"
                            value="Detected (Spring Boot with JPA)"
                            path="databaseType"
                          />
                          <EditableField
                            label="Database Name"
                            value={ND}
                            path="databaseName"
                          />
                        </div>
                      )}
                    </div>
                  )}

                  {/* MODE SINGLE */}
                  {analysis.mode === "single" && analysis.analysis && (
                    <div className="service-details">
                      <div className="service-section">
                        <h4>
                          {analysis.analysis.stackType?.includes("SPRING_BOOT")
                            ? "Backend:"
                            : analysis.analysis.stackType === "NODE_JS"
                            ? "Frontend:"
                            : "Service:"}
                        </h4>

                        {analysis.analysis.stackType?.includes("SPRING_BOOT") && (
                          <>
                            <EditableField
                              label="Framework"
                              value="Spring Boot"
                              path="analysis.stackType"
                            />
                            <EditableField
                              label="Build Tool"
                              value={safe(analysis.analysis.buildTool)}
                              path="analysis.buildTool"
                            />
                            <EditableField
                              label="Spring Version"
                              value={safe(analysis.analysis.projectDetails?.springBootVersion)}
                              path="analysis.projectDetails.springBootVersion"
                            />
                            <EditableField
                              label="Working Directory"
                              value={safe(analysis.analysis.workingDirectory)}
                              path="analysis.workingDirectory"
                            />
                            <EditableField
                              label="Java Version"
                              value={safe(analysis.analysis.javaVersion)}
                              path="analysis.javaVersion"
                            />
                          </>
                        )}

                        {analysis.analysis.stackType === "NODE_JS" && (
                          <>
                            <EditableField
                              label="Stack Type"
                              value="Node.js"
                              path="analysis.stackType"
                            />
                            <EditableField
                              label="Framework"
                              value={safe(analysis.analysis.projectDetails?.framework)}
                              path="analysis.projectDetails.framework"
                            />
                            <EditableField
                              label="Build Tool"
                              value={safe(analysis.analysis.buildTool)}
                              path="analysis.buildTool"
                            />
                            <EditableField
                              label="Language"
                              value={safe(analysis.analysis.language)}
                              path="analysis.language"
                            />
                            <EditableField
                              label="Node Version"
                              value={safe(analysis.analysis.projectDetails?.nodeVersion)}
                              path="analysis.projectDetails.nodeVersion"
                            />
                          </>
                        )}
                      </div>

                      {/* Database for single */}
                      {analysis.analysis.databaseType &&
                        analysis.analysis.databaseType !== "NONE" && (
                          <div className="service-section">
                            <h4>Base de données:</h4>
                            <EditableField
                              label="Database Type"
                              value={safe(analysis.analysis.databaseType)}
                              path="analysis.databaseType"
                            />
                            <EditableField
                              label="Database Name"
                              value={safe(analysis.analysis.databaseName)}
                              path="analysis.databaseName"
                            />
                          </div>
                        )}
                    </div>
                  )}
                </div>
              ) : (
                <div className="no-analysis">
                  <p>No analysis data available</p>
                </div>
              )}
            </div>

            {/* Actions du modal - SEULEMENT 2 BOUTONS */}
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={onCancel} disabled={isAnalyzing}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={onValidateGenerateCI}
                disabled={isAnalyzing}
              >
                Valider &amp; générer CI
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}