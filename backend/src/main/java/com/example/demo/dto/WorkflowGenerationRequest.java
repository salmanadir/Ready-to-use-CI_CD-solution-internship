package com.example.demo.dto;


import com.example.demo.model.TechStackInfo;

public class WorkflowGenerationRequest {
    private Long repoId;
    private TechStackInfo techStackInfo;

    public Long getRepoId(){
            return repoId;
        
    }
    public void setRepoId(Long repoId){
        this.repoId=repoId;
    }

    public TechStackInfo getTechStackInfo() {
        return techStackInfo;
    }

    public void setTechStackInfo(TechStackInfo techStackInfo) {
        this.techStackInfo = techStackInfo;
    }
}
