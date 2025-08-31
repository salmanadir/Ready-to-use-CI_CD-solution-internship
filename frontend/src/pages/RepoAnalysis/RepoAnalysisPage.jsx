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

// Un service est supporté s’il est SPRING_BOOT (Maven/Gradle) ou NODE_JS
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

  const onValidateGenerateCI = async () => {
    alert("CI generation triggered (stub).");
    setAnalysisOpen(false);
    setShowContinueButton(false);
  };

  const onCancel = () => {
    setAnalysisOpen(false);
    setShowContinueButton(true);
  };

  const onContinueAnalysis = () => {
    setAnalysisOpen(true);
    setShowContinueButton(false);
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
                              <div>
                                <strong>Framework:</strong> Spring Boot{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Build Tool:</strong> {safe(service.buildTool)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Spring Version:</strong>{" "}
                                {safe(service.projectDetails?.springBootVersion)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Working Directory:</strong>{" "}
                                {safe(service.workingDirectory)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Java Version:</strong> {safe(service.javaVersion)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                            </>
                          )}

                          {/* Frontend Details */}
                          {service.stackType === "NODE_JS" && (
                            <>
                              <div>
                                <strong>Stack Type:</strong> Node.js{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Framework:</strong>{" "}
                                {safe(service.projectDetails?.framework)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Build Tool:</strong> {safe(service.buildTool)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Language:</strong> {safe(service.language)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                              <div>
                                <strong>Node Version:</strong>{" "}
                                {safe(service.projectDetails?.nodeVersion)}{" "}
                                <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                              </div>
                            </>
                          )}
                        </div>
                      ))}

                      {/* Database Section (indicative) */}
                      {analysis.services.some((s) => s.stackType?.includes("SPRING_BOOT")) && (
                        <div className="service-section">
                          <h4>Base de données:</h4>
                          <div>
                            <strong>Database Type:</strong> Detected (Spring Boot with JPA){" "}
                            <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                          </div>
                          <div>
                            <strong>Database Name:</strong> {ND}{" "}
                            <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                          </div>
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
                            <div>
                              <strong>Framework:</strong> Spring Boot{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Build Tool:</strong> {safe(analysis.analysis.buildTool)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Spring Version:</strong>{" "}
                              {safe(analysis.analysis.projectDetails?.springBootVersion)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Working Directory:</strong>{" "}
                              {safe(analysis.analysis.workingDirectory)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Java Version:</strong> {safe(analysis.analysis.javaVersion)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                          </>
                        )}

                        {analysis.analysis.stackType === "NODE_JS" && (
                          <>
                            <div>
                              <strong>Stack Type:</strong> Node.js{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Framework:</strong>{" "}
                              {safe(analysis.analysis.projectDetails?.framework)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Build Tool:</strong> {safe(analysis.analysis.buildTool)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Language:</strong> {safe(analysis.analysis.language)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Node Version:</strong>{" "}
                              {safe(analysis.analysis.projectDetails?.nodeVersion)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                          </>
                        )}
                      </div>

                      {/* Database for single */}
                      {analysis.analysis.databaseType &&
                        analysis.analysis.databaseType !== "NONE" && (
                          <div className="service-section">
                            <h4>Base de données:</h4>
                            <div>
                              <strong>Database Type:</strong>{" "}
                              {safe(analysis.analysis.databaseType)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                            <div>
                              <strong>Database Name:</strong>{" "}
                              {safe(analysis.analysis.databaseName)}{" "}
                              <i className="fa-solid fa-pen-to-square edit-icon" title="Edit"></i>
                            </div>
                          </div>
                        )}
                    </div>
                  )}

                  {/* Orchestrator */}
                  
                </div>
              ) : (
                <div className="no-analysis">
                  <p>No analysis data available</p>
                </div>
              )}
            </div>
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
