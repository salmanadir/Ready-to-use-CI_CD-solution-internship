package com.example.demo.service;

import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.repository.CiWorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CiWorkflowService {

    @Autowired
    private CiWorkflowRepository workflowRepository;

    public CiWorkflow saveWorkflowToDatabase(Repo repo, String content, String fileName) {
        CiWorkflow workflow = new CiWorkflow();
        workflow.setRepo(repo);
        workflow.setContent(content);
        workflow.setStatus(CiWorkflow.WorkflowStatus.COMMITTED); 
        return workflowRepository.save(workflow);
    }
    
}
