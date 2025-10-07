package com.example.demo.service;

import org.springframework.stereotype.Service;
import com.example.demo.dto.StackAnalysis;

@Service
public class WorkflowTemplateService {
  public String getTemplatePath(StackAnalysis info) {
    String tool = info.getBuildTool() == null ? "" : info.getBuildTool().toLowerCase();
    if ("maven".equals(tool))  return "templates/CI/maven-ci-docker.yml";
    if ("gradle".equals(tool)) return "templates/CI/gradle-ci-docker.yml";
    if ("npm".equals(tool))    return "templates/CI/node-ci-docker.yml";
    return "templates/ci/ci.yml";
  }
}