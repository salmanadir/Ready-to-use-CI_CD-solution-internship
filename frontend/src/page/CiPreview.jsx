import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApp } from "../store/AppContext";
import { previewCi, generateCi } from "../services/api";
import StepHeader from "../components/StepHeader";
import CodeViewer from "../components/CodeViewer";
import StickyActions from "../components/StickyActions";
import MetaCards from "../components/MetaCards";
import CiServiceList from "../components/CiServiceList";

function buildSingleTechStackInfo(analysis) {
  const src = analysis?.analysis || (analysis?.services && analysis.services[0]) || null;
  if (!src) return null;
  return {
    workingDirectory: src.workingDirectory || ".",
    buildTool: (src.buildTool || "").toLowerCase(),
    orchestrator: src.orchestrator || "github-actions",
    javaVersion: src.javaVersion || null,
    projectDetails: src.projectDetails || null,
  };
}

export default function CiPreview() {
  const nav = useNavigate();
  const { repoId, analysis, dockerOptions, setToast } = useApp();

  const mode = analysis?.mode === "multi" ? "multi" : "single";
  const services = mode === "multi" ? (analysis?.services || []) : [];

  const [loading, setLoading] = useState(false);
  const [pushing, setPushing] = useState(false);

  // single
  const [singleYaml, setSingleYaml] = useState("");
  const [singlePath, setSinglePath] = useState("");
  const [singleStatus, setSingleStatus] = useState("NOT_FOUND");

  // multi
  const [previews, setPreviews] = useState([]); // [{service,filePath,status,content}]
  const [selectedWD, setSelectedWD] = useState(null);

  // --- load preview
  useEffect(() => {
    if (!repoId || !analysis) return;
    (async () => {
      try {
        setLoading(true);
        if (mode === "multi") {
          const res = await previewCi({ repoId, services, docker: dockerOptions });
          const arr = res?.previews || [];
          setPreviews(arr);
          setSelectedWD(arr[0]?.service || services[0]?.workingDirectory || ".");
        } else {
          const tech = buildSingleTechStackInfo(analysis);
          const res = await previewCi({ repoId, techStackInfo: tech, docker: dockerOptions });
          setSingleYaml(res?.content || "");
          setSinglePath(res?.filePath || ".github/workflows/ci.yml");
          setSingleStatus(res?.status || "NOT_FOUND");
        }
      } catch (e) {
        setToast({ type: "error", message: e.message || "Failed to preview CI." });
      } finally {
        setLoading(false);
      }
    })();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repoId, analysis]);

  // --- computed for multi
  const mapByWD = useMemo(() => {
    const m = {};
    previews.forEach((p) => (m[p.service || "."] = p));
    return m;
  }, [previews]);

  const current = mode === "multi" ? mapByWD[selectedWD || "."] : null;
  const shownYaml = mode === "multi" ? (current?.content || "") : singleYaml;
  const shownPath = mode === "multi" ? (current?.filePath || "") : singlePath;
  const shownStatus = mode === "multi" ? (current?.status || "NOT_FOUND") : singleStatus;

  // --- meta
  const metaItems = [
    { label: "Workflow path", value: shownPath },
    { label: "Status", value: shownStatus },
  ];

  // --- push handlers
  async function pushOne() {
    try {
      setPushing(true);
      if (mode === "multi") {
        // push uniquement le service sélectionné
        const svc = services.find((s) => s.workingDirectory === (selectedWD || "."));
        const res = await generateCi({ repoId, services: [svc], docker: dockerOptions });
        const ok = Array.isArray(res?.workflows) && res.workflows.length > 0;
        setToast({ type: ok ? "success" : "info", message: "Pushed. Check your repo." });
      } else {
        const tech = buildSingleTechStackInfo(analysis);
        const res = await generateCi({ repoId, techStackInfo: tech, docker: dockerOptions });
        setToast({ type: "success", message: "Pushed. Check your repo." });
        // optionnel: mettre à jour status local
        if (res?.filePath) setSinglePath(res.filePath);
        setSingleStatus("IDENTICAL");
      }
    } catch (e) {
      setToast({ type: "error", message: e.message || "Push failed." });
    } finally {
      setPushing(false);
    }
  }

  async function pushAll() {
    try {
      setPushing(true);
      const res = await generateCi({ repoId, services, docker: dockerOptions });
      const n = Array.isArray(res?.workflows) ? res.workflows.length : 0;
      setToast({ type: "success", message: `Pushed ${n} workflow(s). Check your repo.` });
    } catch (e) {
      setToast({ type: "error", message: e.message || "Bulk push failed." });
    } finally {
      setPushing(false);
    }
  }

  // --- actions
  const primary =
    mode === "multi"
      ? { label: pushing ? "Pushing…" : "Push to GitHub", onClick: pushOne, disabled: pushing || loading || !current }
      : { label: pushing ? "Pushing…" : "Push to GitHub", onClick: pushOne, disabled: pushing || loading };

  const secondary = { label: "Back", onClick: () => nav("/docker/preview") };

  const right =
    mode === "multi" ? (
      <button className="btn" disabled={pushing || loading} onClick={pushAll} style={{ marginRight: 8 }}>
        Push all
      </button>
    ) : null;

  return (
    <div className="page">
      <div className="container">
        <StepHeader
          title="CI — Preview"
          subtitle={mode === "multi" ? "Multi-service repository" : "Single-service repository"}
        />

        <div className={`layout ${mode === "multi" ? "with-sidebar" : ""}`}>
          {mode === "multi" && (
            <aside className="sidebar">
              <CiServiceList
                previews={previews}
                selectedWD={selectedWD}
                onSelect={setSelectedWD}
              />
            </aside>
          )}

          <main className="main">
            <MetaCards items={metaItems} />
            <div className="card">
              {loading ? (
                <div className="skeleton">Loading preview…</div>
              ) : (
                <CodeViewer
                  content={shownYaml}
                  onCopy={() => setToast({ type: "info", message: "Copied to clipboard." })}
                />
              )}
            </div>
          </main>
        </div>

        <StickyActions primary={primary} secondary={secondary} right={right} />
      </div>
    </div>
  );
}
