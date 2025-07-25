package com.example.demo.dto;

import com.example.demo.model.Repo;
import com.example.demo.model.TechStackInfo;

public class WorkflowGenerationRequest {
    private Repo repo;
    private TechStackInfo techStackInfo;

    public Repo getRepo() {
        return repo;
    }

    public void setRepo(Repo repo) {
        this.repo = repo;
    }

    public TechStackInfo getTechStackInfo() {
        return techStackInfo;
    }

    public void setTechStackInfo(TechStackInfo techStackInfo) {
        this.techStackInfo = techStackInfo;
    }
}
