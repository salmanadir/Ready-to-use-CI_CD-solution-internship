import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApp } from "../store/AppContext";
import { previewCi, generateCi, setApiClient } from "../services/api";
import { useAuth } from "../context/AuthContext";
import StepHeader from "../components/StepHeader";
import CodeViewer from "../components/CodeViewer";
import StickyActions from "../components/StickyActions";
import MetaCards from "../components/MetaCards";
import CiServiceList from "../components/CiServiceList";

// 🎨 styles scopés à ces pages
import "../styles/pipeline.css";

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

function humanizeCiSuccess(res, n) {
  if (Array.isArray(res?.workflows)) {
    const count = res.workflows.length;
    if (count === 0) return { type: "info", message: "No changes to push." };
    return { type: "success", message: `Pushed ${count} workflow(s).` };
  }
  const msg = res?.message || "";
  const low = msg.toLowerCase();
  if (low.includes("skipped") || low.includes("nothing to apply") || low.includes("no changes")) {
    return { type: "info", message: msg || "No changes (SKIPPED)." };
  }
  if (low.includes("updated") || low.includes("created") || low.includes("pushed")) {
    return { type: "success", message: msg };
  }
  return { type: "success", message: msg || (n ? `Pushed ${n} workflow(s).` : "Pushed.") };
}

function humanizeCiError(err, strategy) {
  const raw = (err?.payload?.message || err?.message || "Unknown error.").trim();
  const s = err?.status;

  if (/^unexpected error:\s*null$/i.test(raw)) {
    return "Some files already exists. Try to push only unexisted. Check your GitHub repo and branch.";
  }
  if (s === 401) return "Authentication required or repository not owned.";
  if (s === 400) {
    const low = raw.toLowerCase();
    if (low.includes("github token not found")) return "GitHub token not found for user — connect your GitHub account.";
    if (low.includes("github connection failed")) return "GitHub connection failed — check your token permissions.";
    if (String(strategy).toUpperCase() === "FAIL_IF_EXISTS" && low.includes("exists"))
      return "File already exists and strategy is FAIL_IF_EXISTS — generation aborted.";
    return raw;
  }
  if (s === 428) return "Dockerfile not applied yet — preview & apply the Dockerfile first.";
  if (s === 500 && /^io error:/i.test(raw)) return raw.replace(/^io error:\s*/i, "I/O error: ");
  return raw;
}

function preflightForStatus(status, strategy) {
  const strat = String(strategy || "UPDATE_IF_EXISTS").toUpperCase();
  const st = (status || "").toUpperCase(); // NOT_FOUND | IDENTICAL | DIFFERENT
  if (st === "IDENTICAL" && strat === "UPDATE_IF_EXISTS") {
    return { block: true, type: "info", message: "Already up to date — nothing to push." };
  }
  if ((st === "IDENTICAL" || st === "DIFFERENT") && strat === "FAIL_IF_EXISTS") {
    return { block: true, type: "error", message: "File already exists and strategy is FAIL_IF_EXISTS — choose UPDATE_IF_EXISTS or CREATE_NEW_ALWAYS." };
  }
  return { block: false };
}

export default function CiPreview() {
  const nav = useNavigate();
  const { apiClient } = useAuth();
  const { repoId, analysis, dockerOptions, setToast } = useApp();

  useEffect(() => { if (apiClient) setApiClient(apiClient); }, [apiClient]);

  const mode = analysis?.mode === "multi" ? "multi" : "single";
  const services = mode === "multi" ? analysis?.services || [] : [];

  const [loading, setLoading] = useState(false);
  const [pushing, setPushing] = useState(false);

  // single
  const [singleYaml, setSingleYaml] = useState("");
  const [singlePath, setSinglePath] = useState("");
  const [singleStatus, setSingleStatus] = useState("NOT_FOUND");

  // multi
  const [previews, setPreviews] = useState([]); // [{service,filePath,status,content}]
  const [selectedWD, setSelectedWD] = useState(null);

  // modal CD
  const [showCdModal, setShowCdModal] = useState(false);

  async function refreshPreview() {
    if (!repoId || !analysis) return;
    try {
      setLoading(true);
      if (mode === "multi") {
        const res = await previewCi({ repoId, services, docker: dockerOptions });
        const arr = res?.previews || [];
        setPreviews(arr);
        setSelectedWD((prev) => {
          if (!prev) return arr[0]?.service || services[0]?.workingDirectory || ".";
          const still = arr.find((p) => (p.service || ".") === prev);
          return still ? prev : (arr[0]?.service || services[0]?.workingDirectory || ".");
        });
      } else {
        const tech = buildSingleTechStackInfo(analysis);
        const res = await previewCi({ repoId, techStackInfo: tech, docker: dockerOptions });
        setSingleYaml(res?.content || "");
        setSinglePath(res?.filePath || ".github/workflows/ci.yml");
        setSingleStatus(res?.status || "NOT_FOUND");
      }
    } catch (e) {
      setToast({ type: "error", message: e.message || "Failed to preview CI.", position: "center" });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refreshPreview(); /* eslint-disable-next-line */ }, [repoId, analysis]);

  const mapByWD = useMemo(() => {
    const m = {};
    previews.forEach((p) => (m[p.service || "."] = p));
    return m;
  }, [previews]);

  const current = mode === "multi" ? mapByWD[selectedWD || "."] : null;
  const shownYaml = mode === "multi" ? (current?.content || "") : singleYaml;
  const shownPath = mode === "multi" ? (current?.filePath || "") : singlePath;
  const shownStatus = mode === "multi" ? (current?.status || "NOT_FOUND") : singleStatus;

  const metaItems = [
    { label: "Workflow path", value: shownPath },
    { label: "Status", value: shownStatus },
  ];

  const allApplied =
    mode === "multi"
      ? previews.length > 0 && previews.every((p) => p.status === "IDENTICAL")
      : singleStatus === "IDENTICAL";

  async function pushOne() {
    const currentStrategy = "UPDATE_IF_EXISTS";
    try {
      setPushing(true);
      const statusToCheck = mode === "multi" ? (current?.status || "NOT_FOUND") : singleStatus;
      const pf = preflightForStatus(statusToCheck, currentStrategy);
      if (pf.block) { setToast({ type: pf.type, message: pf.message, position: "center" }); return; }

      if (mode === "multi") {
        const svc = services.find((s) => s.workingDirectory === (selectedWD || "."));
        const res = await generateCi({ repoId, services: [svc], docker: dockerOptions, fileHandlingStrategy: currentStrategy });
        const { type, message } = humanizeCiSuccess(res);
        setToast({ type, message, position: "center" });
      } else {
        const tech = buildSingleTechStackInfo(analysis);
        const res = await generateCi({ repoId, techStackInfo: tech, docker: dockerOptions, fileHandlingStrategy: currentStrategy });
        const { type, message } = humanizeCiSuccess(res);
        setToast({ type, message, position: "center" });
        if (res?.filePath) setSinglePath(res.filePath);
        setSingleStatus("IDENTICAL");
      }
      await refreshPreview();
    } catch (e) {
      setToast({ type: "error", message: humanizeCiError(e, "UPDATE_IF_EXISTS"), position: "center" });
    } finally {
      setPushing(false);
    }
  }

  async function pushAll() {
    const currentStrategy = "UPDATE_IF_EXISTS";
    try {
      setPushing(true);

      if (mode === "multi") {
        const statuses = previews.map((p) => (p.status || "NOT_FOUND").toUpperCase());
        const allIdentical = statuses.length > 0 && statuses.every((s) => s === "IDENTICAL");
        const anyExists = statuses.some((s) => s === "IDENTICAL" || s === "DIFFERENT");

        if (allIdentical && currentStrategy === "UPDATE_IF_EXISTS") {
          setToast({ type: "info", message: "Already up to date — nothing to push.", position: "center" });
          return;
        }
        if (anyExists && currentStrategy === "FAIL_IF_EXISTS") {
          setToast({ type: "error", message: "Some files already exist and strategy is FAIL_IF_EXISTS — choose UPDATE_IF_EXISTS or CREATE_NEW_ALWAYS.", position: "center" });
          return;
        }
      }

      const res = await generateCi({ repoId, services, docker: dockerOptions, fileHandlingStrategy: currentStrategy });
      const n = Array.isArray(res?.workflows) ? res.workflows.length : 0;
      const { type, message } = humanizeCiSuccess(res, n);
      setToast({ type, message, position: "center" });
      await refreshPreview();
    } catch (e) {
      setToast({ type: "error", message: humanizeCiError(e, currentStrategy), position: "center" });
    } finally {
      setPushing(false);
    }
  }

  // 🔙 Back → Dockerfile preview du repo courant (et wd si multi)
  const backToDockerPreview = () => {
    const params = new URLSearchParams();
    if (repoId) params.set("repo", repoId);
    if (mode === "multi" && selectedWD) params.set("wd", selectedWD);
    nav(`/docker/preview?${params.toString()}`, {
      state: { repoId, selectedWD }, // envoyé aussi via state (fallback si tu veux le lire côté DockerfilePreview)
    });
  };

  const primary =
    mode === "multi"
      ? { label: pushing ? "Pushing…" : "Push to GitHub", onClick: pushOne, disabled: pushing || loading || !current }
      : { label: pushing ? "Pushing…" : "Push to GitHub", onClick: pushOne, disabled: pushing || loading };

  const secondary = [
    { label: "Back", onClick: backToDockerPreview, disabled: loading || pushing },
    { label: "Refresh", onClick: refreshPreview, disabled: loading || pushing, title: "Recharger l’aperçu" },
  ];

  const cdButton = allApplied ? (
    <button className="btn" onClick={() => setShowCdModal(true)} style={{ marginRight: 8 }} title="Proceed to CD">
      Proceed to CD
    </button>
  ) : null;

  const right =
    mode === "multi" ? (
      <>
        <button className="btn" disabled={pushing || loading} onClick={pushAll} style={{ marginRight: 8 }}>
          Push all
        </button>
        {cdButton}
      </>
    ) : (
      cdButton
    );

  return (
    <div className="pipeline page">
      {/* Modal CD */}
      {showCdModal && (
        <div role="dialog" aria-modal="true" aria-label="Proceed to CD" style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 10000, padding: 24 }}>
          <div style={{ width: "min(92vw, 600px)", background: "#111827", color: "#e5e7eb", border: "1px solid #1f2937", borderRadius: 16, boxShadow: "0 20px 50px rgba(0,0,0,.35)", position: "relative", padding: 20 }}>
            <button onClick={() => setShowCdModal(false)} aria-label="Close modal" title="Close" style={{ position: "absolute", top: 10, right: 10, background: "transparent", color: "#9ca3af", border: "1px solid #1f2937", borderRadius: 8, padding: "4px 8px", cursor: "pointer" }}>
              ✕
            </button>
            <h2 style={{ margin: "4px 0 8px", fontSize: 20 }}>Proceed to CD workflow?</h2>
            <p style={{ margin: 0, color: "#94a3b8" }}>
              All CI workflows are applied. Do you want to continue to the deployment (CD) stage?
            </p>
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 16 }}>
              <button className="btn ghost" onClick={() => setShowCdModal(false)} title="Denied">Denied</button>
              <button className="btn primary" onClick={() => nav("/dashboard/cd-generation")} title="Approve and go to CD">Approve</button>
            </div>
          </div>
        </div>
      )}

      <div className="container">
        <StepHeader title="CI — Preview" subtitle={mode === "multi" ? "Multi-service repository" : "Single-service repository"} />

        <div className={`layout ${mode === "multi" ? "with-sidebar" : ""}`}>
          {mode === "multi" && (
            <aside className="sidebar">
              <CiServiceList previews={previews} selectedWD={selectedWD} onSelect={setSelectedWD} />
            </aside>
          )}

          <main className="main">
            <MetaCards items={[{ label: "Workflow path", value: shownPath }, { label: "Status", value: shownStatus }]} />
            <div className="card">
              {loading ? <div className="skeleton">Loading preview…</div> : <CodeViewer content={shownYaml} onCopy={() => setToast({ type: "info", message: "Copied to clipboard." })} />}
            </div>
          </main>
        </div>

        <StickyActions primary={primary} secondary={secondary} right={right} center={
          <button className="btn ghost" onClick={() => nav("/dashboard")} style={{ minWidth: 260, backgroundColor: "purple" }} aria-label="Go back Home" title="Go back Home">
            Go back Home
          </button>}
        />
      </div>
    </div>
  );
}
