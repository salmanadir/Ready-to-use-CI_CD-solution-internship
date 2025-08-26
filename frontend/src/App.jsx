import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import Analyze from "./page/Analyze";
import DockerfilePreview from "./page/DockerfilePreview";
import CiPreview from "./page/CiPreview"; 


export default function App() {
  return (
    <Routes>
      <Route path="/analyze" element={<Analyze />} />
      <Route path="/docker/preview" element={<DockerfilePreview />} />
      <Route path="/ci/preview" element={<CiPreview />} /> 
      <Route path="/" element={<Navigate to="/analyze" replace />} />
      <Route path="*" element={<div style={{ padding: 24 }}>404</div>} />
    </Routes>
  );
}
