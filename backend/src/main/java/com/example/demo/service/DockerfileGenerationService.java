package com.example.demo.service;

import com.example.demo.model.DeploymentFile;
import com.example.demo.model.DeploymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DockerfileGenerationService {
    @Autowired
    private DeploymentFileService deploymentFileService;

    public void generateDockerfilesForServices(List<Map<String, Object>> services, DeploymentRequest deploymentRequest) {
        for (Map<String, Object> service : services) {
            String kind = (String) service.get("kind");
            String buildTool = (String) service.get("buildTool");
            String contextDir = (String) service.get("contextDir");
            Map<String, Object> env = (Map<String, Object>) service.get("env");
            Map<String, Object> runtime = (Map<String, Object>) service.get("runtime");
            String startScript = runtime != null ? (String) runtime.get("startScript") : null;
            Integer port = runtime != null && runtime.get("port") != null ? (Integer) runtime.get("port") : null;
            if (port == null) {
                port = kind != null && kind.toLowerCase().contains("node") ? 3000 : 8080;
            }

            String dockerfileContent = null;
            String fileName = "Dockerfile";

            if (kind != null && kind.toLowerCase().contains("spring")) {
                if (buildTool != null && buildTool.toLowerCase().contains("maven")) {
                    dockerfileContent = getSpringBootMavenDockerfile(contextDir, port);
                } else if (buildTool != null && buildTool.toLowerCase().contains("gradle")) {
                    dockerfileContent = getSpringBootGradleDockerfile(contextDir, port);
                }
            } else if (kind != null && kind.toLowerCase().contains("node")) {
                if (buildTool != null && buildTool.toLowerCase().contains("yarn")) {
                    dockerfileContent = getNodeYarnDockerfile(contextDir, port, startScript, env);
                } else {
                    dockerfileContent = getNodeNpmDockerfile(contextDir, port, startScript, env);
                }
            }

            if (dockerfileContent != null) {
                deploymentFileService.saveDeploymentFile(deploymentRequest, fileName, "DOCKERFILE", dockerfileContent);
            }
        }
    }

    private String getSpringBootMavenDockerfile(String contextDir, int port) {
        return "# Multi-stage build for Spring Boot (Maven)\n" +
                "FROM maven:3.9.6-eclipse-temurin-17 AS build\n" +
                "WORKDIR /app\n" +
                "COPY . .\n" +
                "RUN mvn clean package -DskipTests\n" +
                "\n" +
                "FROM eclipse-temurin:17-jre\n" +
                "WORKDIR /app\n" +
                "COPY --from=build /app/target/*.jar app.jar\n" +
                "EXPOSE " + port + "\n" +
                "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
    }

    private String getSpringBootGradleDockerfile(String contextDir, int port) {
        return "# Multi-stage build for Spring Boot (Gradle)\n" +
                "FROM gradle:8.7.0-jdk17 AS build\n" +
                "WORKDIR /app\n" +
                "COPY . .\n" +
                "RUN gradle build -x test\n" +
                "\n" +
                "FROM eclipse-temurin:17-jre\n" +
                "WORKDIR /app\n" +
                "COPY --from=build /app/build/libs/*.jar app.jar\n" +
                "EXPOSE " + port + "\n" +
                "ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
    }

    private String getNodeNpmDockerfile(String contextDir, int port, String startScript, Map<String, Object> env) {
        StringBuilder sb = new StringBuilder();
        sb.append("FROM node:18\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY package*.json ./\n");
        sb.append("RUN npm install --production\n");
        sb.append("COPY . .\n");
        sb.append("EXPOSE ").append(port).append("\n");
        if (env != null && !env.isEmpty()) {
            for (Map.Entry<String, Object> entry : env.entrySet()) {
                sb.append("ENV ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
        }
        if (startScript != null && !startScript.isEmpty()) {
            sb.append("CMD [\"").append(startScript.replace(" ", "\", \"")).append("\"]\n");
        } else {
            sb.append("CMD [\"npm\", \"start\"]\n");
        }
        return sb.toString();
    }

    private String getNodeYarnDockerfile(String contextDir, int port, String startScript, Map<String, Object> env) {
        StringBuilder sb = new StringBuilder();
        sb.append("FROM node:18\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY package*.json ./\n");
        sb.append("RUN yarn install --production\n");
        sb.append("COPY . .\n");
        sb.append("EXPOSE ").append(port).append("\n");
        if (env != null && !env.isEmpty()) {
            for (Map.Entry<String, Object> entry : env.entrySet()) {
                sb.append("ENV ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
        }
        if (startScript != null && !startScript.isEmpty()) {
            sb.append("CMD [\"").append(startScript.replace(" ", "\", \"")).append("\"]\n");
        } else {
            sb.append("CMD [\"yarn\", \"start\"]\n");
        }
        return sb.toString();
    }
}
