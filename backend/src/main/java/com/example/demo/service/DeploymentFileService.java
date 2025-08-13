package com.example.demo.service;

import com.example.demo.model.DeploymentFile;
import com.example.demo.model.DeploymentRequest;
import com.example.demo.repository.DeploymentFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeploymentFileService {
    @Autowired
    private DeploymentFileRepository deploymentFileRepository;

    public DeploymentFile saveDeploymentFile(DeploymentRequest request, String fileName, String fileType, String content) {
        DeploymentFile file = new DeploymentFile();
        file.setDeploymentRequest(request);
        file.setFileName(fileName);
        file.setFileType(fileType);
        file.setContent(content);
        file.setCreatedAt(LocalDateTime.now());
        return deploymentFileRepository.save(file);
    }

    public List<DeploymentFile> getFilesByDeploymentRequest(DeploymentRequest request) {
        return deploymentFileRepository.findByDeploymentRequest(request);
    }

    public List<DeploymentFile> getFilesByDeploymentRequestId(Long deploymentRequestId) {
        return deploymentFileRepository.findByDeploymentRequestDeploymentRequestId(deploymentRequestId);
    }
}
