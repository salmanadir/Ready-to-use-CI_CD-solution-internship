import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useApp } from "../../store/AppContext";
import { previewDocker, applyDockerfile, setApiClient } from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import StepHeader from "../../components/StepHeader";
import MetaCards from "../../components/MetaCards";
import CodeViewer from "../../components/CodeViewer";
import StickyActions from "../../components/StickyActions";
import ServiceList from "../../components/ServiceList";

// 🎨 styles scopés à ces pages
import "./styles/pipeline.css";

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

function pickDockerSuccessToastTypeAndMessage(res) {
  const msg = res?.message || "";
  const lower = msg.toLowerCase();
  if (lower.includes("already present") || lower.includes("nothing to apply")) {
    return { type: "info", message: msg || "Nothing to apply." };
  }
  return { type: "success", message: msg || "Dockerfile applied." };
}

function humanizeDockerError(err, strategy) {
  const m = err?.payload?.message || err?.message || "Unknown error.";
  const s = err?.status;

  if (s === 401) return "Authentication required or repository not owned.";
  if (s === 400) {
    if (m.toLowerCase().includes("github token not found")) {
      return "GitHub token not found for user — connect your GitHub account.";
    }
    if (
      String(strategy).toUpperCase() === "FAIL_IF_EXISTS" &&
      (m.toLowerCase().includes("already exists") || m.toLowerCase().includes("exists"))
    ) {
      return "File already exists and strategy is FAIL_IF_EXISTS — push aborted.";
    }
    return m;
  }
  if (s === 500 && /^io error:/i.test(m)) {
    return m.replace(/^io error:\s*/i, "I/O error: ");
  }
  return m;
}

export default function DockerfilePreview() {
  const nav = useNavigate();
  const { apiClient } = useAuth(); // ✅ client auth (JWT)
  const {
    repoId,
    analysis,
    dockerOptions,
    containerPlans,
    setContainerPlans,
    readyForCi,
    setReadyForCi,
    setToast,
  } = useApp();

  // ✅ branche l'apiClient (JWT) dans le SDK une seule fois
  useEffect(() => {
    if (apiClient) setApiClient(apiClient);
  }, [apiClient]);

  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [selectedWD, setSelectedWD] = useState(null);

  const mode = analysis?.mode === "multi" ? "multi" : "single";
  const services = mode === "multi" ? analysis?.services || [] : [];

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

  // 🔙 helper: retourner vers l’analyse du repo courant
  const backToAnalysis = () => {
    if (repoId) {
      nav(`/analysis/${encodeURIComponent(repoId)}`);
    } else {
      nav("/dashboard/select-repo");
    }
  };

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
        const first =
          services?.[0]?.workingDirectory ||
          plans?.[0]?.workingDirectory ||
          null;
        setSelectedWD(first);
      } else {
        const plan = res.containerPlan ? [res.containerPlan] : [];
        setContainerPlans(plan);
        setReadyForCi(Boolean(res.readyForCi));
        setSelectedWD(plan?.[0]?.workingDirectory || null);
      }
    } catch (e) {
      setToast({
        type: "error",
        message: e.message || "Failed to preview Dockerfile.",
        position: "center",
      });
    } finally {
      setLoading(false);
    }
  }

  async function onApplyOne() {
    if (!currentPlan) return;
    const strategy = (dockerOptions && dockerOptions.dockerfileStrategy) || "UPDATE_IF_EXISTS";
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
      const { type, message } = pickDockerSuccessToastTypeAndMessage(res);
      setToast({ type, message, position: "center" });
      await loadPreview();
    } catch (e) {
      setToast({ type: "error", message: humanizeDockerError(e, strategy), position: "center" });
    } finally {
      setApplying(false);
    }
  }

  async function onApplyAll() {
    const strategy = (dockerOptions && dockerOptions.dockerfileStrategy) || "UPDATE_IF_EXISTS";
    try {
      setApplying(true);
      if (mode !== "multi") return;
      const res = await applyDockerfile({ repoId, services, docker: dockerOptions });
      const { type, message } = pickDockerSuccessToastTypeAndMessage(res);
      setToast({ type, message, position: "center" });
      await loadPreview();
    } catch (e) {
      setToast({ type: "error", message: humanizeDockerError(e, strategy), position: "center" });
    } finally {
      setApplying(false);
    }
  }

  if (!repoId || !analysis) {
    return (
      <div className="pipeline page">
        <div className="container">
          <StepHeader title="Dockerfile — Preview" subtitle="No analysis loaded." />
          <div className="card">
            <p>
              Come back after repository analysis (which fills <code>repoId</code> and{" "}
              <code>analysis</code>).
            </p>
          </div>
          <StickyActions
            primary={{ label: "Go to Analyze", onClick: backToAnalysis }}
            secondary={{ label: "Back", onClick: backToAnalysis }}
            right={null}
            center={
              <button
                className="btn ghost"
                onClick={() => nav("/")}
                style={{ minWidth: 260 }}
                aria-label="Go back Home"
                title="Go back Home"
              >
                Go back Home
              </button>
            }
          />
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

  const allReady = useMemo(() => {
    if (mode === "multi") {
      return (
        readyForCi &&
        containerPlans.length > 0 &&
        containerPlans.every((p) => !p.shouldGenerateDockerfile)
      );
    }
    return readyForCi && currentPlan && !currentPlan.shouldGenerateDockerfile;
  }, [mode, readyForCi, containerPlans, currentPlan]);

  const canProceed = allReady && !loading && !applying;

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

  const secondaryAction = [
    { label: "Back", onClick: backToAnalysis, disabled: loading || applying },
    { label: "Refresh", onClick: loadPreview, disabled: loading || applying, title: "Refresh previews" },
  ];

  const rightAction =
    mode === "multi" ? (
      <button
        className="btn"
        disabled={applying || loading || allReady}
        onClick={onApplyAll}
        style={{ marginRight: 8 }}
        aria-disabled={applying || loading || allReady}
        title={
          allReady
            ? "All services already have a Dockerfile"
            : "Apply Dockerfile to all missing services"
        }
      >
        Apply all missing
      </button>
    ) : null;

  return (
    <div className="pipeline page">
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

        <StickyActions
          primary={primaryAction}
          secondary={secondaryAction}
          right={rightAction}
          center={
            <button
              className="btn ghost"
              onClick={() => nav("/dashboard")}
              style={{ minWidth: 260, backgroundColor: "purple" }}
              aria-label="Go back Home"
              title="Go back Home"
            >
              Go back Home
            </button>
          }
        />
      </div>
    </div>
  );
}
