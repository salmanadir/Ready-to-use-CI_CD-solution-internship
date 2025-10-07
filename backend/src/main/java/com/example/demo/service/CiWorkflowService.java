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

    /**
     * Sauvegarde le workflow après un push GitHub réussi
     * @param repo Le repository associé
     * @param content Le contenu du fichier workflow
     * @param commitHash Le hash du commit GitHub (obligatoire)
     * @return Le workflow sauvegardé
     */
    public CiWorkflow saveWorkflowAfterPush(Repo repo, String content, String commitHash) {
        if (commitHash == null || commitHash.isEmpty()) {
            throw new IllegalArgumentException("Commit hash cannot be null or empty");
        }
        
        CiWorkflow workflow = new CiWorkflow();
        workflow.setRepo(repo);
        workflow.setContent(content);
        workflow.setGithubCommitHash(commitHash);
        workflow.setStatus(CiWorkflow.WorkflowStatus.COMMITTED);
        
        return workflowRepository.save(workflow);
    }
}