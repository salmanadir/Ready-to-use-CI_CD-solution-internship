import React, { useEffect, useMemo, useState } from "react";
import { useLocation, useSearchParams, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import "./RepoAnalysisPage.css";
import { useApp } from "../../store/AppContext";


const ND = "NONE";
const safe = (v) => (v === undefined || v === null || v === "" ? ND : v);
const isND = (v) => v === ND || v === undefined || v === null || v === "";

const JAVA_OPTIONS = ["8", "11", "17", "21"];
const BUILD_TOOLS = ["Maven", "Gradle", "npm"];
const DB_TYPES = [
  "PostgreSQL",
  "MySQL",
  "MongoDB",
  "H2",
  "NONE",
  "Detected (Spring Boot with JPA)",
];
const NODE_FRAMEWORKS = [
  "React",
  "Vue.js",
  "Angular",
  "Express.js",
  "Next.js",
  "Vanilla Node.js",
];


const confirmedKey = (repoId) => `analysisConfirmed:${repoId}`;
const getRepoConfirmed = (repoId) =>
  !!(repoId && localStorage.getItem(confirmedKey(repoId)) === "1");
const setRepoConfirmed = (repoId, v) => {
  if (!repoId) return;
  if (v) localStorage.setItem(confirmedKey(repoId), "1");
  else localStorage.removeItem(confirmedKey(repoId));
};


const canonBuildTool = (v) => {
  const x = (v || "").toLowerCase();
  if (x === "maven") return "Maven";
  if (x === "gradle") return "Gradle";
  if (x === "npm") return "npm";
  return v || "";
};
const canonJava = (v) => (JAVA_OPTIONS.includes(v) ? v : "");
const canonDbType = (v) => (DB_TYPES.includes(v) ? v : "");
const canonFramework = (v) => (NODE_FRAMEWORKS.includes(v) ? v : "");
const canonNodeVersion = (v) =>
  !v ? "" : v === "Latest" || /^\d+(\.\d+)*$/.test(v) ? v : "";


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


  if (raw?.mode === "single" && raw?.analysis) {
    const s = normalizeService(raw.analysis);
    const chk = isSupportedService(s);
    if (!chk.ok) errors.push(chk.reason);

 
    return {
      ok: chk.ok,
      data: {
        mode: "single",
        analysis: s,
        
        databaseType: s.databaseType,
        databaseName: s.databaseName,
      },
      errors,
    };
  }


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
      data: {
        mode: "multi",
        services: supported,
       
        databaseType: canonDbType(raw.databaseType || "NONE"),
        databaseName: safe(raw.databaseName),
      },
      errors,
    };
  }

  errors.push("Format d'analyse inattendu.");
  return { ok: false, data: raw, errors };
}



async function fetchRepositoryAnalysis(repo, apiClient) {
  try {
    if (!repo || !repo.repoId) {
      throw new Error("Repository ID not available");
    }
    if (!apiClient) {
      throw new Error("apiClient not ready");
    }

    const data = await apiClient.post(
      `/api/stack-analysis/analyze/${repo.repoId}`
    );

    if (data?.success) {
      return data;
    } else {
      throw new Error(data?.message || "Erreur lors de l'analyse du repository");
    }
  } catch (error) {
    console.error("Erreur lors de l'analyse:", error);
    throw error;
  }
}

async function fetchRepoFiles(repo, apiClient) {
  if (!repo || !repo.repoId) {
    throw new Error("Repository ID not available");
  }
  if (!apiClient) {
    throw new Error("apiClient not ready");
  }
  const data = await apiClient.get(
    `/api/stack-analysis/repository/${repo.repoId}/all-files`
  );
  if (data?.success) {
    return data.files || [];
  }
  throw new Error(data?.message || "Erreur lors du chargement des fichiers");
}


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


const getAllowedBuildTools = (stackType) => {
  if (stackType?.includes("SPRING_BOOT")) {
    return ["Maven", "Gradle"];
  }
  if (stackType === "NODE_JS") {
    return ["npm"];
  }
  return BUILD_TOOLS;
};

const SelectField = ({
  label,
  value,
  path,
  options,
  setEditingValue,
  setEditingField,
  setEditingPath,
  editingField,
  handleEditKeyDown,
  saveEdit,
  cancelEdit,
}) => {
  const isEditing = editingField === path;
  const startVal =
    value && options?.includes(value) ? value : options?.[0] || "";

  return (
    <div className="editable-field">
      <strong>{label}:</strong>
      {isEditing ? (
        <div className="edit-input-container">
          <select
            value={startVal}
            onChange={(e) => setEditingValue(e.target.value)}
            onKeyDown={handleEditKeyDown}
            autoFocus
            className="edit-input"
          >
            {options?.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
          <button onClick={saveEdit} className="edit-confirm-btn">
            ✓
          </button>
          <button onClick={cancelEdit} className="edit-cancel-btn">
            ✗
          </button>
        </div>
      ) : (
        <span className="editable-value">
          {value}
          <i
            className="fa-solid fa-pen-to-square edit-icon"
            title="Edit"
            onClick={() => {
              setEditingValue(startVal);
              setEditingField(path);
              setEditingPath(path);
            }}
            style={{ cursor: "pointer", marginLeft: "5px" }}
          ></i>
        </span>
      )}
    </div>
  );
};

const BuildToolSelect = ({
  label,
  value,
  path,
  stackType,
  setEditingValue,
  setEditingField,
  setEditingPath,
  editingField,
  handleEditKeyDown,
  saveEdit,
  cancelEdit,
}) => {
  const allowedOptions = getAllowedBuildTools(stackType);
  const isEditing = editingField === path;
  const startVal =
    value && allowedOptions.includes(value) ? value : allowedOptions[0];

  return (
    <div className="editable-field">
      <strong>{label}:</strong>
      {isEditing ? (
        <div className="edit-input-container">
          <select
            value={startVal}
            onChange={(e) => setEditingValue(e.target.value)}
            onKeyDown={handleEditKeyDown}
            autoFocus
            className="edit-input"
          >
            {allowedOptions.map((opt) => (
              <option key={opt} value={opt}>
                {opt}
              </option>
            ))}
          </select>
          <button onClick={saveEdit} className="edit-confirm-btn">
            ✓
          </button>
          <button onClick={cancelEdit} className="edit-cancel-btn">
            ✗
          </button>
        </div>
      ) : (
        <span className="editable-value">
          {value}
          <i
            className="fa-solid fa-pen-to-square edit-icon"
            title="Edit"
            onClick={() => {
              setEditingValue(startVal);
              setEditingField(path);
              setEditingPath(path);
            }}
            style={{ cursor: "pointer", marginLeft: "5px" }}
          ></i>
        </span>
      )}
    </div>
  );
};

const ReadonlyField = ({ label, value }) => (
  <div className="editable-field">
    <strong>{label}:</strong> <span className="readonly-value">{value}</span>
  </div>
);


export default function RepoAnalysisPage() {
  const location = useLocation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { apiClient } = useAuth();
  const { setRepoId, setAnalysis: setAppAnalysis } = useApp();

  const repo = location.state?.repo || null;
  const repoFullName = repo?.fullName || params.get("repo") || "unknown/repo";

  const [visibleFiles, setVisibleFiles] = useState([]);
  const [filesError, setFilesError] = useState(null);

  const [analysisOpen, setAnalysisOpen] = useState(() => {
    const rid = location.state?.repo?.repoId;
    return rid ? !getRepoConfirmed(rid) : true;
  });

  const [isAnalyzing, setIsAnalyzing] = useState(true);
  const [analysis, setAnalysis] = useState(null);
  const [analysisError, setAnalysisError] = useState(null);

  const [showContinueButton, setShowContinueButton] = useState(false);

  const [editingField, setEditingField] = useState(null);
  const [editingValue, setEditingValue] = useState("");
  const [editingPath, setEditingPath] = useState("");

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const [confirmedOnce, setConfirmedOnce] = useState(() => {
    const rid = location.state?.repo?.repoId;
    return rid ? getRepoConfirmed(rid) : false;
  });

  
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

 
  useEffect(() => {
    if (repo?.repoId) {
      const already = getRepoConfirmed(repo.repoId);
      setConfirmedOnce(already);
      setAnalysisOpen(!already);
    }
  }, [repo]);

 
  useEffect(() => {
    let interval;
    let alive = true;

    (async () => {
      try {
        setFilesError(null);
        if (!apiClient) return;

        const files = await fetchRepoFiles(repo, apiClient);
        if (!alive) return;

        let i = 0;
        interval = setInterval(() => {
          i++;
          setVisibleFiles(files.slice(0, i));
          if (i >= files.length) clearInterval(interval);
        }, 120);
      } catch (e) {
        if (!alive) return;
        setFilesError(e.message || "Impossible de charger les fichiers.");
        setVisibleFiles([]);
      }
    })();

    return () => {
      alive = false;
      clearInterval(interval);
    };
  }, [repo, apiClient]);

 
  useEffect(() => {
    if (!repo) return;
    let alive = true;

    (async () => {
      try {
        setIsAnalyzing(true);
        setAnalysisError(null);
        const analysisData = await fetchRepositoryAnalysis(repo, apiClient);
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
  }, [repo, apiClient]);

  const tree = useMemo(() => buildTree(visibleFiles), [visibleFiles]);

  const startEditing = (path, value) => {
    setEditingPath(path);
    setEditingValue(value);
    setEditingField(path);
  };

  
  const saveAnalysisToBackend = async () => {
    try {
      if (!analysis) {
        console.error("Aucune analyse disponible pour la sauvegarde");
        return false;
      }

      const updatedData = {};

      if (analysis.mode === "multi" && Array.isArray(analysis.services)) {
        analysis.services.forEach((service, index) => {
          if (!service) return;

          const bt = canonBuildTool(service.buildTool);
          if (!isND(bt)) updatedData[`services.${index}.buildTool`] = bt;

          const jv = canonJava(service.javaVersion);
          if (!isND(jv) && service.stackType?.includes("SPRING_BOOT"))
            updatedData[`services.${index}.javaVersion`] = jv;

          const wd = service.workingDirectory;
          if (!isND(wd)) updatedData[`services.${index}.workingDirectory`] = wd;

          const lang = service.language;
          if (!isND(lang)) updatedData[`services.${index}.language`] = lang;

          if (service.projectDetails) {
            const sbv = service.projectDetails.springBootVersion;
            if (!isND(sbv))
              updatedData[
                `services.${index}.projectDetails.springBootVersion`
              ] = sbv;

            const fw = canonFramework(service.projectDetails.framework);
            if (!isND(fw))
              updatedData[`services.${index}.projectDetails.framework`] = fw;

            const nv = canonNodeVersion(service.projectDetails.nodeVersion);
            if (!isND(nv))
              updatedData[`services.${index}.projectDetails.nodeVersion`] = nv;
          }
        });

        const dbt = canonDbType(analysis.databaseType || "NONE");
        if (dbt) updatedData.databaseType = dbt;

        const dbn = analysis.databaseName || "my_database";
        if (!isND(dbn)) updatedData.databaseName = dbn;
      } else if (analysis.mode === "single" && analysis.analysis) {
        const s = analysis.analysis;

        const bt = canonBuildTool(s.buildTool);
        if (!isND(bt)) updatedData["analysis.buildTool"] = bt;

        const jv = canonJava(s.javaVersion);
        if (!isND(jv) && s.stackType?.includes("SPRING_BOOT"))
          updatedData["analysis.javaVersion"] = jv;

        const wd = s.workingDirectory;
        if (!isND(wd)) updatedData["analysis.workingDirectory"] = wd;

        const lang = s.language;
        if (!isND(lang)) updatedData["analysis.language"] = lang;

        const dbt = canonDbType(s.databaseType || "NONE");
        if (dbt) updatedData["analysis.databaseType"] = dbt;

        const dbn = s.databaseName || "my_database";
        if (!isND(dbn)) updatedData["analysis.databaseName"] = dbn;

        if (s.projectDetails) {
          const sbv = s.projectDetails.springBootVersion;
          if (!isND(sbv))
            updatedData["analysis.projectDetails.springBootVersion"] = sbv;

          const fw = canonFramework(s.projectDetails.framework);
          if (!isND(fw))
            updatedData["analysis.projectDetails.framework"] = fw;

          const nv = canonNodeVersion(s.projectDetails.nodeVersion);
          if (!isND(nv))
            updatedData["analysis.projectDetails.nodeVersion"] = nv;
        }
      }

      const data = await apiClient.put(
        `/api/stack-analysis/repository/${repo.repoId}/update-parameters`,
        updatedData
      );

      if (data?.success) {
        return true;
      } else {
        throw new Error(data?.message || "Échec de la sauvegarde");
      }
    } catch (error) {
      console.error("Erreur lors de la sauvegarde:", error);
      return false;
    }
  };


  const updateFieldSafely = (obj, path, value) => {
    const pathParts = path.split(".");
    const updated = JSON.parse(JSON.stringify(obj));

    if (path === "databaseType" || path === "databaseName") {
      updated[path] = value;
      return updated;
    }

    let current = updated;
    for (let i = 0; i < pathParts.length - 1; i++) {
      if (!current[pathParts[i]]) {
        if (pathParts[i] === "projectDetails") {
          current[pathParts[i]] = {};
        } else {
          console.error(`Invalid path: ${path}`);
          return obj;
        }
      }
      current = current[pathParts[i]];
    }

    current[pathParts[pathParts.length - 1]] = value;
    return updated;
  };

  const saveEdit = async () => {
    if (!analysis || !editingPath) return;

    const updatedAnalysis = updateFieldSafely(
      analysis,
      editingPath,
      editingValue
    );

    setAnalysis(updatedAnalysis);
    setEditingField(null);
    setEditingValue("");
    setEditingPath("");

  
    setHasUnsavedChanges(true);
  };

  const cancelEdit = () => {
    setEditingField(null);
    setEditingValue("");
    setEditingPath("");
  };

  const handleEditChange = (e) => {
    setEditingValue(e.target.value);
  };

  const handleEditKeyDown = (e) => {
    if (e.key === "Enter") {
      saveEdit();
    } else if (e.key === "Escape") {
      cancelEdit();
    }
  };

  
  const goToNextPage = () => {
    navigate(`/next-step/${repo?.repoId}`, { state: { repo } });
  };

 
  const onValidateGenerateCI = () => {
  
    if (editingField) {
      saveEdit();
    }

    if (!confirmedOnce || hasUnsavedChanges) {
      setConfirmOpen(true); 
    } else {
      setRepoConfirmed(repo?.repoId, true);
      goToNextPage();
    }
  };

  const onCancel = () => {
    setAnalysisOpen(false);
    setShowContinueButton(true);
  };

  const onContinueAnalysis = () => {
    setAnalysisOpen(true);
    setShowContinueButton(false);
  };

  const onConfirmCancel = () => {
    if (confirmLoading) return;
    setConfirmOpen(false);
  };

  
  const onConfirmProceed = async () => {
        try {
          setConfirmLoading(true);
          const ok = await saveAnalysisToBackend();
          if (!ok) {
            // on ne bouge pas si la sauvegarde a échoué
            setConfirmLoading(false);
            return;
          }
    
         // Marque comme confirmé côté localStorage
         setRepoConfirmed(repo?.repoId, true);
          setHasUnsavedChanges(false);
          setConfirmedOnce(true);
    
          // ✅ Propager les données au contexte utilisé par Docker/CI
          if (repo?.repoId) setRepoId(repo.repoId);
          if (analysis)      setAppAnalysis(analysis); // { mode, analysis || services, ... }
    
          // Ferme la modal puis redirige vers la page Docker
          setConfirmOpen(false);
          navigate("/docker/preview");
        } finally {
          setConfirmLoading(false);
       }
      };

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
            <button onClick={saveEdit} className="edit-confirm-btn">
              ✓
            </button>
            <button onClick={cancelEdit} className="edit-cancel-btn">
              ✗
            </button>
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

 
  if (!apiClient) {
    return (
      <div className="analysis-page">
        <div className="loading-indicator">
          <p>Chargement de l'authentification...</p>
        </div>
      </div>
    );
  }

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
            Analysis completed! Continue to generate CI configuration for this
            repository.
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
          <div
            className="modal modal-large"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-header">
              <h3>Repository Analysis</h3>
              <p className="repo-name">{repoFullName}</p>
            </div>
            <div className="modal-body">
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
                    <h4>Orchestrator: GitHub Actions</h4>
                  </div>

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

                          {service.stackType?.includes("SPRING_BOOT") && (
                            <>
                              <ReadonlyField
                                label="Framework"
                                value="Spring Boot"
                              />

                              <BuildToolSelect
                                label="Build Tool"
                                value={safe(service.buildTool)}
                                path={`services.${index}.buildTool`}
                                stackType={service.stackType}
                                setEditingValue={setEditingValue}
                                setEditingField={setEditingField}
                                setEditingPath={setEditingPath}
                                editingField={editingField}
                                handleEditKeyDown={handleEditKeyDown}
                                saveEdit={saveEdit}
                                cancelEdit={cancelEdit}
                              />

                              <EditableField
                                label="Working Directory"
                                value={safe(service.workingDirectory)}
                                path={`services.${index}.workingDirectory`}
                              />

                              <SelectField
                                label="Java Version"
                                value={safe(service.javaVersion)}
                                path={`services.${index}.javaVersion`}
                                options={JAVA_OPTIONS}
                                setEditingValue={setEditingValue}
                                setEditingField={setEditingField}
                                setEditingPath={setEditingPath}
                                editingField={editingField}
                                handleEditKeyDown={handleEditKeyDown}
                                saveEdit={saveEdit}
                                cancelEdit={cancelEdit}
                              />

                              <EditableField
                                label="Spring Version"
                                value={safe(
                                  service.projectDetails?.springBootVersion
                                )}
                                path={`services.${index}.projectDetails.springBootVersion`}
                              />
                            </>
                          )}

                          {service.stackType === "NODE_JS" && (
                            <>
                              <ReadonlyField
                                label="Stack Type"
                                value="Node.js"
                              />

                              <SelectField
                                label="Framework"
                                value={safe(
                                  service.projectDetails?.framework
                                )}
                                path={`services.${index}.projectDetails.framework`}
                                options={NODE_FRAMEWORKS}
                                setEditingValue={setEditingValue}
                                setEditingField={setEditingField}
                                setEditingPath={setEditingPath}
                                editingField={editingField}
                                handleEditKeyDown={handleEditKeyDown}
                                saveEdit={saveEdit}
                                cancelEdit={cancelEdit}
                              />

                              <BuildToolSelect
                                label="Build Tool"
                                value={safe(service.buildTool)}
                                path={`services.${index}.buildTool`}
                                stackType={service.stackType}
                                setEditingValue={setEditingValue}
                                setEditingField={setEditingField}
                                setEditingPath={setEditingPath}
                                editingField={editingField}
                                handleEditKeyDown={handleEditKeyDown}
                                saveEdit={saveEdit}
                                cancelEdit={cancelEdit}
                              />

                              <EditableField
                                label="Language"
                                value={safe(service.language)}
                                path={`services.${index}.language`}
                              />

                              <EditableField
                                label="Node Version"
                                value={safe(
                                  service.projectDetails?.nodeVersion
                                )}
                                path={`services.${index}.projectDetails.nodeVersion`}
                              />
                            </>
                          )}
                        </div>
                      ))}

                      {analysis.services.some((s) =>
                        s.stackType?.includes("SPRING_BOOT")
                      ) && (
                        <div className="service-section">
                          <h4>Base de données:</h4>

                          <SelectField
                            label="Database Type"
                            value={safe(analysis.databaseType)}
                            path="databaseType"
                            options={DB_TYPES}
                            setEditingValue={setEditingValue}
                            setEditingField={setEditingField}
                            setEditingPath={setEditingPath}
                            editingField={editingField}
                            handleEditKeyDown={handleEditKeyDown}
                            saveEdit={saveEdit}
                            cancelEdit={cancelEdit}
                          />

                          <EditableField
                            label="Database Name"
                            value={safe(analysis.databaseName)}
                            path="databaseName"
                          />
                        </div>
                      )}
                    </div>
                  )}

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
                            <ReadonlyField
                              label="Framework"
                              value="Spring Boot"
                            />

                            <BuildToolSelect
                              label="Build Tool"
                              value={safe(analysis.analysis.buildTool)}
                              path="analysis.buildTool"
                              stackType={analysis.analysis.stackType}
                              setEditingValue={setEditingValue}
                              setEditingField={setEditingField}
                              setEditingPath={setEditingPath}
                              editingField={editingField}
                              handleEditKeyDown={handleEditKeyDown}
                              saveEdit={saveEdit}
                              cancelEdit={cancelEdit}
                            />

                            <EditableField
                              label="Working Directory"
                              value={safe(
                                analysis.analysis.workingDirectory
                              )}
                              path="analysis.workingDirectory"
                            />

                            <SelectField
                              label="Java Version"
                              value={safe(analysis.analysis.javaVersion)}
                              path="analysis.javaVersion"
                              options={JAVA_OPTIONS}
                              setEditingValue={setEditingValue}
                              setEditingField={setEditingField}
                              setEditingPath={setEditingPath}
                              editingField={editingField}
                              handleEditKeyDown={handleEditKeyDown}
                              saveEdit={saveEdit}
                              cancelEdit={cancelEdit}
                            />

                            <EditableField
                              label="Spring Version"
                              value={safe(
                                analysis.analysis.projectDetails
                                  ?.springBootVersion
                              )}
                              path="analysis.projectDetails.springBootVersion"
                            />
                          </>
                        )}

                        {analysis.analysis.stackType === "NODE_JS" && (
                          <>
                            <ReadonlyField label="Stack Type" value="Node.js" />

                            <SelectField
                              label="Framework"
                              value={safe(
                                analysis.analysis.projectDetails?.framework
                              )}
                              path="analysis.projectDetails.framework"
                              options={NODE_FRAMEWORKS}
                              setEditingValue={setEditingValue}
                              setEditingField={setEditingField}
                              setEditingPath={setEditingPath}
                              editingField={editingField}
                              handleEditKeyDown={handleEditKeyDown}
                              saveEdit={saveEdit}
                              cancelEdit={cancelEdit}
                            />

                            <BuildToolSelect
                              label="Build Tool"
                              value={safe(analysis.analysis.buildTool)}
                              path="analysis.buildTool"
                              stackType={analysis.analysis.stackType}
                              setEditingValue={setEditingValue}
                              setEditingField={setEditingField}
                              setEditingPath={setEditingPath}
                              editingField={editingField}
                              handleEditKeyDown={handleEditKeyDown}
                              saveEdit={saveEdit}
                              cancelEdit={cancelEdit}
                            />

                            <EditableField
                              label="Language"
                              value={safe(analysis.analysis.language)}
                              path="analysis.language"
                            />

                            <EditableField
                              label="Node Version"
                              value={safe(
                                analysis.analysis.projectDetails?.nodeVersion
                              )}
                              path="analysis.projectDetails.nodeVersion"
                            />
                          </>
                        )}
                      </div>

                      {analysis.analysis.databaseType &&
                        analysis.analysis.databaseType !== "NONE" && (
                          <div className="service-section">
                            <h4>Base de données:</h4>

                            <SelectField
                              label="Database Type"
                              value={safe(analysis.analysis.databaseType)}
                              path="analysis.databaseType"
                              options={DB_TYPES}
                              setEditingValue={setEditingValue}
                              setEditingField={setEditingField}
                              setEditingPath={setEditingPath}
                              editingField={editingField}
                              handleEditKeyDown={handleEditKeyDown}
                              saveEdit={saveEdit}
                              cancelEdit={cancelEdit}
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

            <div className="modal-actions">
              <button
                className="btn btn-secondary"
                onClick={onCancel}
                disabled={isAnalyzing}
              >
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={onValidateGenerateCI}
                disabled={isAnalyzing}
              >
                go to the next step
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmOpen && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          onClick={() => !confirmLoading && setConfirmOpen(false)}
        >
          <div className="modal-s" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Confirm Message</h3>
            </div>
            <div className="modal-body">
              <p style={{ marginBottom: 0 }}>
                ⚠️ Please make sure of the parameters' values because these
                parameters will be used in the next process.
                <br />
                Do you want to continue?
              </p>
            </div>
            <div className="modal-actions">
              <button
                className="btn btn-secondary"
                onClick={onConfirmCancel}
                disabled={confirmLoading}
              >
                Cancel
              </button>
              <button
                className="btn btn-primary-modal"
                onClick={onConfirmProceed}
                disabled={confirmLoading}
              >
                {confirmLoading ? "Saving..." : "Yes, continue"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
