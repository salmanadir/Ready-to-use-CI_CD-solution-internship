import React, { createContext, useContext, useEffect, useMemo, useState } from "react";

const AppContext = createContext(null);
export const useApp = () => useContext(AppContext);

const LS_KEY = "ci_cd_app_state";

export function AppProvider({ children }) {
  const [repoId, setRepoId] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [dockerOptions, setDockerOptions] = useState({ registry: "ghcr.io", imageNameOverride: null });
  const [containerPlans, setContainerPlans] = useState([]);
  const [readyForCi, setReadyForCi] = useState(false);
  const [toast, setToast] = useState(null);

  // recharge depuis localStorage (utile tant que la page analyse n'est pas faite)
  useEffect(() => {
    try {
      const raw = localStorage.getItem(LS_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        setRepoId(parsed.repoId ?? null);
        setAnalysis(parsed.analysis ?? null);
        setDockerOptions(parsed.dockerOptions ?? { registry: "ghcr.io", imageNameOverride: null });
      }
    } catch {}
  }, []);

  // persiste le minimum
  useEffect(() => {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify({ repoId, analysis, dockerOptions }));
    } catch {}
  }, [repoId, analysis, dockerOptions]);

  const value = useMemo(
    () => ({
      repoId, setRepoId,
      analysis, setAnalysis,
      dockerOptions, setDockerOptions,
      containerPlans, setContainerPlans,
      readyForCi, setReadyForCi,
      toast, setToast,
    }),
    [repoId, analysis, dockerOptions, containerPlans, readyForCi, toast]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}
