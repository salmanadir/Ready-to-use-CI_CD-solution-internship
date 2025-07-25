package com.example.demo.controller;

import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.TechStackInfo;
import com.example.demo.service.*;
import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.dto.WorkflowGenerationRequest;
import com.example.demo.service.WorkflowTemplateService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class CiWorkflowController {

    @Autowired
    private WorkflowTemplateService templateService;

    @Autowired
    private TemplateRenderer templateRenderer;

    @Autowired
    private GitHubService gitHubService;

    @Autowired
    private CiWorkflowService workflowService;

    @PostMapping("/generate")
    public String generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request) throws IOException {
        Repo repo = request.getRepo();
        TechStackInfo info = request.getTechStackInfo();

        String templatePath = templateService.getTemplatePath(info);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("javaVersion", info.getJavaVersion());
        placeholders.put("workingDirectory", info.getWorkingDirectory());

        String content = templateRenderer.renderTemplate(templatePath, placeholders);

        String filePath = ".github/workflows/" + info.getBuildTool().toLowerCase() + ".yml";

        gitHubService.pushWorkflowToGitHub(repo.getUser().getToken(), repo.getFullName(), repo.getDefaultBranch(), filePath, content);

        workflowService.saveWorkflowToDatabase(repo, content, filePath);

        return "Workflow pushed and saved successfully.";
    }
}
