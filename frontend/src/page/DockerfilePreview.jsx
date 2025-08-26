import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApp } from "../store/AppContext";
import { previewDocker, applyDockerfile } from "../services/api";
import StepHeader from "../components/StepHeader";
import MetaCards from "../components/MetaCards";
import CodeViewer from "../components/CodeViewer";
import StickyActions from "../components/StickyActions";
import ServiceList from "../components/ServiceList";

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

function pickPreview(plan) {
  if (!plan) return { content: "", source: "-" };
  const {
    previewDockerfileContent,
    existingDockerfileContent,
    generatedDockerfileContent,
    proposedDockerfileContent,
    previewSource,
  } = plan;

  if (previewDockerfileContent && previewDockerfileContent.trim().length) {
    return { content: previewDockerfileContent, source: previewSource || "-" };
  }
  if (existingDockerfileContent && existingDockerfileContent.trim().length) {
    return { content: existingDockerfileContent, source: "existing" };
  }
  if (generatedDockerfileContent && generatedDockerfileContent.trim().length) {
    return { content: generatedDockerfileContent, source: "generated" };
  }
  if (proposedDockerfileContent && proposedDockerfileContent.trim().length) {
    return { content: proposedDockerfileContent, source: "generated" };
  }
  return { content: "", source: "-" };
}

export default function DockerfilePreview() {
  const nav = useNavigate();
  const {
    repoId, analysis, dockerOptions,
    containerPlans, setContainerPlans,
    readyForCi, setReadyForCi,
    setToast,
  } = useApp();

  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [selectedWD, setSelectedWD] = useState(null);

  const mode = analysis?.mode === "multi" ? "multi" : "single";
  const services = mode === "multi" ? (analysis?.services || []) : [];

  const plansByWD = useMemo(() => {
    const map = {};
    (containerPlans || []).forEach((p) => {
      if (p?.workingDirectory) map[p.workingDirectory] = p;
    });
    return map;
  }, [containerPlans]);

  const currentPlan = useMemo(() => {
    if (mode === "single") return containerPlans?.[0] || null;
    if (!selectedWD) return null;
    return plansByWD[selectedWD] || null;
  }, [mode, containerPlans, plansByWD, selectedWD]);

  useEffect(() => {
    if (!repoId || !analysis) return;
    loadPreview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [repoId, analysis]);

  async function loadPreview() {
    try {
      setLoading(true);
      let payload;
      if (mode === "multi") {
        payload = { repoId, services, docker: dockerOptions };
      } else {
        const tech = buildSingleTechStackInfo(analysis);
        if (!tech) throw new Error("Cannot build techStackInfo (single).");
        payload = { repoId, techStackInfo: tech, docker: dockerOptions };
      }
      const res = await previewDocker(payload);

      if (res.mode === "multi") {
        const plans = res.plans || [];
        setContainerPlans(plans);
        setReadyForCi(Boolean(res.readyForCi));
        const first = services?.[0]?.workingDirectory || plans?.[0]?.workingDirectory || null;
        setSelectedWD(first);
      } else {
        const plan = res.containerPlan ? [res.containerPlan] : [];
        setContainerPlans(plan);
        setReadyForCi(Boolean(res.readyForCi));
        setSelectedWD(plan?.[0]?.workingDirectory || null);
      }
    } catch (e) {
      setToast({ type: "error", message: e.message || "Failed to preview Dockerfile." });
    } finally {
      setLoading(false);
    }
  }

  async function onApplyOne() {
    if (!currentPlan) return;
    try {
      setApplying(true);
      let payload;
      if (mode === "multi") {
        const svc = services.find((s) => s.workingDirectory === selectedWD);
        if (!svc) throw new Error("Service not found.");
        payload = { repoId, services: [svc], docker: dockerOptions };
      } else {
        const tech = buildSingleTechStackInfo(analysis);
        payload = { repoId, techStackInfo: tech, docker: dockerOptions };
      }
      const res = await applyDockerfile(payload);
      const msg = res?.message || "Dockerfile applied.";
      setToast({ type: "success", message: msg + (res.commitHash ? ` (commit ${res.commitHash})` : "") });
      await loadPreview();
    } catch (e) {
      setToast({ type: "error", message: e.message || "Apply failed." });
    } finally {
      setApplying(false);
    }
  }

  async function onApplyAll() {
    try {
      setApplying(true);
      if (mode !== "multi") return;
      const res = await applyDockerfile({ repoId, services, docker: dockerOptions });
      const msg = res?.message || "Dockerfiles applied.";
      setToast({ type: "success", message: msg });
      await loadPreview();
    } catch (e) {
      setToast({ type: "error", message: e.message || "Bulk apply failed." });
    } finally {
      setApplying(false);
    }
  }

  if (!repoId || !analysis) {
    return (
      <div className="page">
        <div className="container">
          <StepHeader title="Dockerfile — Preview" subtitle="No analysis loaded." />
          <div className="card">
            <p>
              Come back after repository analysis (which fills <code>repoId</code> and{" "}
              <code>analysis</code>).
            </p>
          </div>
        </div>
      </div>
    );
  }

  const chosen = pickPreview(currentPlan);

  const metaItems = currentPlan
    ? [
        { label: "Registry", value: currentPlan.registry },
        { label: "Image", value: currentPlan.imageName },
        { label: "Docker context", value: currentPlan.dockerContext },
        { label: "Working dir", value: currentPlan.workingDirectory },
      ]
    : [];

  // prêt pour CI ? (serveur + fallback local)
  const allReady = useMemo(() => {
    if (mode === "multi") {
      return readyForCi && containerPlans.length > 0 && containerPlans.every(p => !p.shouldGenerateDockerfile);
    }
    return readyForCi && currentPlan && !currentPlan.shouldGenerateDockerfile;
  }, [mode, readyForCi, containerPlans, currentPlan]);

  const canProceed = allReady && !loading && !applying;

  // Actions
const primaryAction =
currentPlan?.shouldGenerateDockerfile
  ? {
      label: applying ? "Applying…" : "Apply to GitHub",
      onClick: onApplyOne,
      disabled: applying || loading,
    }
  : {
      label: "Next: Preview CI",
      onClick: () => nav("/ci/preview"),
      disabled: !canProceed, 
    };


  const secondaryAction =
    mode === "multi"
      ? { label: "Refresh", onClick: loadPreview, disabled: loading || applying }
      : { label: "Back", onClick: () => { /* nav(-1) */ }, disabled: loading || applying };

  const rightAction =
    mode === "multi" ? (
      <button
        className="btn"
        disabled={applying || loading || allReady}
        onClick={onApplyAll}
        style={{ marginRight: 8 }}
        aria-disabled={applying || loading || allReady}
        title={allReady ? "All services already have a Dockerfile" : "Apply Dockerfile to all missing services"}
      >
        Apply all missing
      </button>
    ) : null;

  return (
    <div className="page">
      <div className="container">
        <StepHeader
          title="Dockerfile — Preview"
          subtitle={mode === "multi" ? "Multi-service repository" : "Single-service repository"}
          chips={[]}
        />

        <div className={`layout ${mode === "multi" ? "with-sidebar" : ""}`}>
          {mode === "multi" && (
            <aside className="sidebar">
              <ServiceList
                services={services}
                plansByWD={plansByWD}
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
                  content={chosen.content || ""}
                  onCopy={() => setToast({ type: "info", message: "Copied to clipboard." })}
                />
              )}
            </div>
          </main>
        </div>

        <StickyActions primary={primaryAction} secondary={secondaryAction} right={rightAction} />
      </div>
    </div>
  );
}
