package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import com.example.demo.model.TechStackInfo;

@Service
public class WorkflowTemplateService {
    public String getTemplatePath(TechStackInfo info) {
        String buildTool = info.getBuildTool().toLowerCase();
        if (!List.of("maven", "gradle", "node").contains(buildTool)) {
            throw new IllegalArgumentException("Unsupported build tool: " + buildTool);
        }
        return "templates/CI/" + buildTool + "_ci.yml";
    }
    
}
