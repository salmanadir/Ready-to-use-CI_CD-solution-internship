package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.dto.StackAnalysis;

@Service
public class StackDetectionService {

    @Autowired
    private GitHubService gitHubService;

    /**
     * Analyse un repository GitHub pour détecter sa stack technique
     */
    public StackAnalysis analyzeRepository(String repoUrl, String token, String defaultBranch) {
        List<Map<String, Object>> files = gitHubService.getRepositoryContents(repoUrl, token, null);

        DetectedStack detectedStack = detectStackTypeRecursively(files, repoUrl, token, "");

        String javaVersion = detectJavaVersion(repoUrl, token, detectedStack.stackType, detectedStack.workingDirectory);
        String buildTool = detectBuildTool(detectedStack.stackType);
        String language = detectLanguage(detectedStack.stackType);

        StackAnalysis analysis = new StackAnalysis(
                detectedStack.stackType,
                javaVersion,
                "github-actions",
                detectedStack.workingDirectory,
                files
        );
        analysis.setBuildTool(buildTool);
        analysis.setLanguage(language);

        Map<String, Object> projectDetails = analyzeProjectDetails(repoUrl, token, detectedStack.stackType, detectedStack.workingDirectory);
        analysis.setProjectDetails(projectDetails);

        // Infos Docker/DB
        String databaseType = detectDatabaseTypeFromStack(repoUrl, token, detectedStack.stackType, detectedStack.workingDirectory);
        String databaseName = extractDatabaseName(repoUrl, token, detectedStack.workingDirectory);
        analysis.setDatabaseType(databaseType);
        analysis.setDatabaseName(databaseName);

        return analysis;
    }

    /**
     * Génère une configuration de services structurée pour Docker
     */
    public Map<String, Object> generateStructuredServices(String repoUrl, String token, String defaultBranch) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> files = gitHubService.getRepositoryContents(repoUrl, token, null);

        List<DetectedStack> detectedServices = detectAllServices(files, repoUrl, token, "");
        List<Map<String, Object>> services = new ArrayList<>();
        List<Map<String, Object>> relationships = new ArrayList<>();

        String databaseServiceId = null;

        for (int i = 0; i < detectedServices.size(); i++) {
            DetectedStack detected = detectedServices.get(i);
            Map<String, Object> service = createStructuredService(detected, repoUrl, token, i);
            services.add(service);

            if (service.containsKey("databaseType") && !"NONE".equals(service.get("databaseType"))) {
                if (databaseServiceId == null) {
                    Map<String, Object> dbService = createDatabaseService(
                            (String) service.get("databaseType"),
                            (String) service.get("databaseName")
                    );
                    services.add(dbService);
                    databaseServiceId = (String) dbService.get("id");
                }
                
                relationships.add(createRelationship((String) service.get("id"), databaseServiceId, "database"));
            }
        }

      
        addFrontendBackendRelationships(services, relationships);

        result.put("services", services);
        result.put("relationships", relationships);
        result.put("totalServices", services.size());

        return result;
    }

    /**
     * Crée un service structuré avec toutes les métadonnées nécessaires
     */
    private Map<String, Object> createStructuredService(DetectedStack detected, String repoUrl, String token, int index) {
        Map<String, Object> service = new HashMap<>();

        // Métadonnées
        service.put("id", generateServiceId(detected, index));
        service.put("name", generateServiceName(detected));
        service.put("kind", determineServiceKind(detected, repoUrl, token));
        service.put("framework", detectDetailedFramework(detected, repoUrl, token));
        service.put("contextDir", detected.workingDirectory);
        service.put("buildTool", detectBuildTool(detected.stackType));

        // Build / Runtime / Env
        Map<String, Object> artifact = createArtifactConfig(detected, repoUrl, token);
        Map<String, Object> runtime = createRuntimeConfig(detected, repoUrl, token);
        Map<String, Object> env = createEnvironmentConfig(detected, repoUrl, token);

        service.put("artifact", artifact);
        service.put("runtime", runtime);
        service.put("env", env);

        // DB si Spring
        if (detected.stackType.contains("SPRING_BOOT")) {
            try {
                String buildFileContent = getBuildFileContent(repoUrl, token, detected);
                String databaseType = detectDatabaseType(buildFileContent);
                if (!"NONE".equals(databaseType)) {
                    service.put("databaseType", databaseType);
                    service.put("databaseName", extractDatabaseName(repoUrl, token, detected.workingDirectory));
                }
            } catch (Exception ignored) {}
        }

        return service;
    }

    private String generateServiceId(DetectedStack detected, int index) {
        if (detected.stackType.contains("SPRING_BOOT")) return "backend-" + index;
        if ("NODE_JS".equals(detected.stackType)) return "frontend-" + index;
        return "service-" + index;
    }

    private String generateServiceName(DetectedStack detected) {
        String dirName = detected.workingDirectory.equals(".") ? "root" : detected.workingDirectory.replace("./", "");
        if (detected.stackType.contains("SPRING_BOOT")) return dirName + "-backend";
        if ("NODE_JS".equals(detected.stackType)) return dirName + "-frontend";
        return dirName + "-service";
    }

    private String determineServiceKind(DetectedStack detected, String repoUrl, String token) {
        if (detected.stackType.contains("SPRING_BOOT")) return "backend";
        if ("NODE_JS".equals(detected.stackType)) {
            try {
                String pkg = gitHubService.getFileContent(repoUrl, token, detected.workingDirectory + "/package.json");
                return isNodeBackend(pkg) ? "backend" : "frontend";
            } catch (Exception e) {
                return "frontend";
            }
        }
        return "service";
    }

    private String detectDetailedFramework(DetectedStack detected, String repoUrl, String token) {
        if (detected.stackType.contains("SPRING_BOOT")) return "spring-boot";
        if ("NODE_JS".equals(detected.stackType)) {
            try {
                String pkg = gitHubService.getFileContent(repoUrl, token, detected.workingDirectory + "/package.json");
                String fw = detectNodeFramework(pkg);
                if ("React".equals(fw)) {
                    if (pkg.contains("\"vite\"")) return "react-vite";
                    if (pkg.contains("\"react-scripts\"")) return "react-cra";
                    return "react";
                }
                return fw.toLowerCase().replace(".js", "").replace(" ", "-");
            } catch (Exception e) {
                return "nodejs";
            }
        }
        return "generic";
    }

    private Map<String, Object> createArtifactConfig(DetectedStack detected, String repoUrl, String token) {
        Map<String, Object> artifact = new HashMap<>();

        if (detected.stackType.contains("SPRING_BOOT")) {
            if ("SPRING_BOOT_MAVEN".equals(detected.stackType)) {
                artifact.put("buildCommand", "mvn clean package -DskipTests");
                artifact.put("outputPath", "target/*.jar");
            } else {
                artifact.put("buildCommand", "gradle build -x test");
                artifact.put("outputPath", "build/libs/*.jar");
            }
        } else if ("NODE_JS".equals(detected.stackType)) {
            try {
                String pkg = gitHubService.getFileContent(repoUrl, token, detected.workingDirectory + "/package.json");
                String fw = detectNodeFramework(pkg);

                if ("Next.js".equals(fw)) {
                    artifact.put("buildCommand", "npm run build");
                    artifact.put("outputPath", ".next");
                    artifact.put("isSSR", true);
                } else if ("React".equals(fw)) {
                    artifact.put("buildCommand", "npm run build");
                    artifact.put("outputPath", detectReactOutputPath(pkg));
                    artifact.put("isSSR", false);
                } else if ("Vue.js".equals(fw)) {
                    artifact.put("buildCommand", "npm run build");
                    artifact.put("outputPath", "dist");
                    artifact.put("isSSR", false);
                } else if ("Angular".equals(fw)) {
                    artifact.put("buildCommand", "npm run build");
                    artifact.put("outputPath", "dist");
                    artifact.put("isSSR", false);
                } else if (isNodeBackend(pkg)) {
                    artifact.put("buildCommand", "npm install");
                    artifact.put("outputPath", ".");
                    artifact.put("isSSR", true);
                }
            } catch (Exception e) {
                artifact.put("buildCommand", "npm run build");
                artifact.put("outputPath", "dist");
                artifact.put("isSSR", false);
            }
        }

        return artifact;
    }

    private String detectReactOutputPath(String packageContent) {
        if (packageContent != null && packageContent.contains("\"vite\"")) return "dist";
        if (packageContent != null && packageContent.contains("\"react-scripts\"")) return "build";
        return "build";
    }

    private Map<String, Object> createRuntimeConfig(DetectedStack detected, String repoUrl, String token) {
        Map<String, Object> runtime = new HashMap<>();

        if (detected.stackType.contains("SPRING_BOOT")) {
            runtime.put("port", extractSpringBootPort(repoUrl, token, detected.workingDirectory));
            runtime.put("startCommand", "java -jar app.jar");
        } else if ("NODE_JS".equals(detected.stackType)) {
            try {
                String pkg = gitHubService.getFileContent(repoUrl, token, detected.workingDirectory + "/package.json");
                String fw = detectNodeFramework(pkg);

                if ("Next.js".equals(fw)) {
                    runtime.put("port", "3000");
                    runtime.put("startCommand", "npm start");
                } else if (isNodeBackend(pkg)) {
                    runtime.put("port", extractNodePort(pkg));
                    runtime.put("startCommand", "npm start");
                } else {
                    runtime.put("port", "80");       
                    runtime.put("startCommand", null);
                }
            } catch (Exception e) {
                runtime.put("port", "3000");
                runtime.put("startCommand", "npm start");
            }
        }

        return runtime;
    }

    private String extractSpringBootPort(String repoUrl, String token, String workingDirectory) {
        try {
            String props = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/src/main/resources/application.properties");
            if (props != null) {
                Matcher m = Pattern.compile("server\\.port\\s*=\\s*(\\d+)").matcher(props);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}

        try {
            String yml = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/src/main/resources/application.yml");
            if (yml != null) {
                Matcher m = Pattern.compile("port:\\s*(\\d+)").matcher(yml);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}

        return "8080";
        }

    private String extractNodePort(String packageContent) {
        if (packageContent == null) return "3000";
        Matcher m = Pattern.compile("PORT[=:]\\s*(\\d+)").matcher(packageContent);
        if (m.find()) return m.group(1);
        return "3000";
    }

    private Map<String, Object> createEnvironmentConfig(DetectedStack detected, String repoUrl, String token) {
        Map<String, Object> env = new HashMap<>();

        if (detected.stackType.contains("SPRING_BOOT")) {
            try {
                String buildFileContent = getBuildFileContent(repoUrl, token, detected);
                String dbType = detectDatabaseType(buildFileContent);

                if (!"NONE".equals(dbType)) {
                    switch (dbType) {
                        case "PostgreSQL":
                            env.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://database:5432/${DB_NAME}");
                            env.put("SPRING_DATASOURCE_USERNAME", "${DB_USER}");
                            env.put("SPRING_DATASOURCE_PASSWORD", "${DB_PASSWORD}");
                            break;
                        case "MySQL":
                            env.put("SPRING_DATASOURCE_URL", "jdbc:mysql://database:3306/${DB_NAME}");
                            env.put("SPRING_DATASOURCE_USERNAME", "${DB_USER}");
                            env.put("SPRING_DATASOURCE_PASSWORD", "${DB_PASSWORD}");
                            break;
                        case "MongoDB":
                            env.put("SPRING_DATA_MONGODB_URI", "mongodb://database:27017/${DB_NAME}");
                            break;
                        default:
                            break;
                    }
                }

                env.put("SPRING_PROFILES_ACTIVE", "production");
                env.put("JAVA_OPTS", "-Xmx512m -Xms256m");
            } catch (Exception e) {
                env.put("SPRING_PROFILES_ACTIVE", "production");
            }

        } else if ("NODE_JS".equals(detected.stackType)) {
            try {
                String pkg = gitHubService.getFileContent(repoUrl, token, detected.workingDirectory + "/package.json");
                String fw = detectNodeFramework(pkg);

                env.put("NODE_ENV", "production");

                if ("React".equals(fw)) {
                    env.put("REACT_APP_API_URL", "${API_URL}");
                } else if ("Vue.js".equals(fw)) {
                    env.put("VUE_APP_API_URL", "${API_URL}");
                } else if ("Next.js".equals(fw)) {
                    env.put("NEXT_PUBLIC_API_URL", "${API_URL}");
                } else if (isNodeBackend(pkg)) {
                    env.put("PORT", "3000"); // fallback
                }
            } catch (Exception e) {
                env.put("NODE_ENV", "production");
            }
        }

        return env;
    }

    private Map<String, Object> createDatabaseService(String databaseType, String databaseName) {
        Map<String, Object> dbService = new HashMap<>();

        dbService.put("id", "database");
        dbService.put("name", databaseName);
        dbService.put("kind", "database");
        dbService.put("framework", databaseType.toLowerCase());

        Map<String, Object> runtime = new HashMap<>();
        Map<String, Object> env = new HashMap<>();

        switch (databaseType) {
            case "PostgreSQL":
                runtime.put("port", "5432");
                env.put("POSTGRES_DB", databaseName);
                env.put("POSTGRES_USER", "postgres");
                env.put("POSTGRES_PASSWORD", "postgres");
                dbService.put("volume", "/var/lib/postgresql/data");
                break;
            case "MySQL":
                runtime.put("port", "3306");
                env.put("MYSQL_DATABASE", databaseName);
                env.put("MYSQL_USER", "mysql");
                env.put("MYSQL_PASSWORD", "mysql");
                env.put("MYSQL_ROOT_PASSWORD", "rootpassword");
                dbService.put("volume", "/var/lib/mysql");
                break;
            case "MongoDB":
                runtime.put("port", "27017");
                env.put("MONGO_INITDB_DATABASE", databaseName);
                dbService.put("volume", "/data/db");
                break;
            case "H2":
                runtime.put("port", "9092");
                env.put("H2_OPTIONS", "-ifNotExists");
                dbService.put("volume", "/opt/h2-data");
                break;
            default:
                break;
        }

        dbService.put("runtime", runtime);
        dbService.put("env", env);

        return dbService;
    }

    private Map<String, Object> createRelationship(String fromId, String toId, String type) {
        Map<String, Object> relationship = new HashMap<>();
        relationship.put("from", fromId);
        relationship.put("to", toId);
        relationship.put("type", type);
        return relationship;
    }

    private void addFrontendBackendRelationships(List<Map<String, Object>> services, List<Map<String, Object>> relationships) {
        String backendId = null;
        List<String> frontendIds = new ArrayList<>();

        for (Map<String, Object> service : services) {
            String kind = (String) service.get("kind");
            String id = (String) service.get("id");

            if ("backend".equals(kind) && backendId == null) backendId = id;
            else if ("frontend".equals(kind)) frontendIds.add(id);
        }

        if (backendId != null) {
            for (String frontendId : frontendIds) {
                relationships.add(createRelationship(frontendId, backendId, "api"));
            }
        }
    }

   
    public Map<String, Object> generateDockerConfiguration(String repoUrl, String token, String defaultBranch) {
        Map<String, Object> dockerConfig = new HashMap<>();
        List<Map<String, Object>> files = gitHubService.getRepositoryContents(repoUrl, token, null);

        List<DetectedStack> services = detectAllServices(files, repoUrl, token, "");
        List<Map<String, Object>> serviceConfigs = new ArrayList<>();

        for (DetectedStack service : services) {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("type", service.stackType);
            cfg.put("workingDirectory", service.workingDirectory);
            cfg.put("buildTool", detectBuildTool(service.stackType));
            cfg.put("language", detectLanguage(service.stackType));

            if (service.stackType.contains("SPRING_BOOT")) {
                configureSpringBootService(cfg, repoUrl, token, service);
            } else if ("NODE_JS".equals(service.stackType)) {
                configureNodeJsService(cfg, repoUrl, token, service);
            }

            serviceConfigs.add(cfg);
        }

        dockerConfig.put("services", serviceConfigs);
        dockerConfig.put("hasDatabase", serviceConfigs.stream().anyMatch(s -> s.containsKey("databaseType")));
        dockerConfig.put("totalServices", serviceConfigs.size());

        return dockerConfig;
    }

    private void configureSpringBootService(Map<String, Object> serviceConfig, String repoUrl, String token, DetectedStack service) {
        try {
            String javaVersion = detectJavaVersion(repoUrl, token, service.stackType, service.workingDirectory);
            serviceConfig.put("javaVersion", javaVersion);

            String buildFileContent = getBuildFileContent(repoUrl, token, service);

            String databaseType = detectDatabaseType(buildFileContent);
            if (!"NONE".equals(databaseType)) {
                serviceConfig.put("databaseType", databaseType);
                serviceConfig.put("databaseName", extractDatabaseName(repoUrl, token, service.workingDirectory));
            }

            serviceConfig.put("dependencies", extractDependencies(buildFileContent, service.stackType));
            serviceConfig.put("packaging", extractPackaging(buildFileContent));
            serviceConfig.put("springBootVersion", extractSpringBootVersion(buildFileContent, service.stackType));

        } catch (Exception e) {
            serviceConfig.put("error", "Failed to analyze Spring Boot service: " + e.getMessage());
        }
    }

    private void configureNodeJsService(Map<String, Object> serviceConfig, String repoUrl, String token, DetectedStack service) {
        try {
            String pkg = gitHubService.getFileContent(repoUrl, token, service.workingDirectory + "/package.json");

            serviceConfig.put("framework", detectNodeFramework(pkg));
            serviceConfig.put("nodeVersion", extractNodeVersion(pkg));
            serviceConfig.put("scripts", extractNpmScripts(pkg));
            serviceConfig.put("isBackend", isNodeBackend(pkg));
            serviceConfig.put("isFrontend", isNodeFrontend(pkg));
            serviceConfig.put("dependencies", extractNodeDependencies(pkg));

        } catch (Exception e) {
            serviceConfig.put("error", "Failed to analyze Node.js service: " + e.getMessage());
        }
    }

    /**
     * Scan récursif de tous les services
     */
    private List<DetectedStack> detectAllServices(List<Map<String, Object>> files, String repoUrl, String token, String currentPath) {
        List<DetectedStack> services = new ArrayList<>();

        for (Map<String, Object> file : files) {
            String fileName = (String) file.get("name");
            String type = (String) file.get("type");
            String newPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if ("Dockerfile".equals(fileName) || "docker-compose.yml".equals(fileName)) continue;

            if ("pom.xml".equals(fileName))
                services.add(new DetectedStack("SPRING_BOOT_MAVEN", currentPath.isEmpty() ? "." : "./" + currentPath));
            if ("build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName))
                services.add(new DetectedStack("SPRING_BOOT_GRADLE", currentPath.isEmpty() ? "." : "./" + currentPath));
            if ("package.json".equals(fileName))
                services.add(new DetectedStack("NODE_JS", currentPath.isEmpty() ? "." : "./" + currentPath));

            if ("dir".equals(type)) {
                List<Map<String, Object>> subFiles = gitHubService.getRepositoryContents(repoUrl, token, newPath);
                services.addAll(detectAllServices(subFiles, repoUrl, token, newPath));
            }
        }

        return services;
    }

    private String detectDatabaseType(String buildFileContent) {
        if (buildFileContent == null || buildFileContent.isEmpty()) return "NONE";
        if (buildFileContent.contains("spring-boot-starter-data-mongodb")) return "MongoDB";
        if (buildFileContent.contains("mysql-connector-java") || buildFileContent.contains("mysql")) return "MySQL";
        if (buildFileContent.contains("postgresql") || buildFileContent.contains("postgres")) return "PostgreSQL";
        if (buildFileContent.contains("h2database") || buildFileContent.contains("com.h2database")) return "H2";
        if (buildFileContent.contains("oracle") || buildFileContent.contains("ojdbc")) return "Oracle";
        if (buildFileContent.contains("sqlserver") || buildFileContent.contains("mssql")) return "SQLServer";
        if (buildFileContent.contains("spring-boot-starter-data-jpa")) return "JPA_GENERIC";
        return "NONE";
    }

    private String detectDatabaseTypeFromStack(String repoUrl, String token, String stackType, String workingDirectory) {
        try {
            String buildFileContent = getBuildFileContentByType(repoUrl, token, stackType, workingDirectory);
            return detectDatabaseType(buildFileContent);
        } catch (Exception e) {
            return "NONE";
        }
    }

    private String extractDatabaseName(String repoUrl, String token, String workingDirectory) {
        try {
            String props = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/src/main/resources/application.properties");
            String dbName = parseDatabaseNameFromProperties(props);
            if (!"my_database".equals(dbName)) return dbName;
        } catch (Exception ignored) {}

        try {
            String yml = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/src/main/resources/application.yml");
            String dbName = parseDatabaseNameFromYml(yml);
            if (!"my_database".equals(dbName)) return dbName;
        } catch (Exception ignored) {}

        return "my_database";
    }

    private String parseDatabaseNameFromProperties(String content) {
        if (content == null) return "my_database";

        Matcher m = Pattern.compile("spring\\.datasource\\.url=.*[:/]([\\w-]+)(?:\\?|$)").matcher(content);
        if (m.find()) return m.group(1);

        m = Pattern.compile("spring\\.data\\.mongodb\\.database=([\\w-]+)").matcher(content);
        if (m.find()) return m.group(1);

        m = Pattern.compile("spring\\.datasource\\.url=.*/(\\w+)").matcher(content);
        if (m.find()) return m.group(1);

        return "my_database";
    }

    private String parseDatabaseNameFromYml(String content) {
        if (content == null) return "my_database";

        Matcher m = Pattern.compile("url:.*[:/]([\\w-]+)(?:\\?|$)").matcher(content);
        if (m.find()) return m.group(1);

        m = Pattern.compile("database:\\s*([\\w-]+)").matcher(content);
        if (m.find()) return m.group(1);

        return "my_database";
    }

    private String getBuildFileContent(String repoUrl, String token, DetectedStack service) throws Exception {
        if ("SPRING_BOOT_MAVEN".equals(service.stackType))
            return gitHubService.getFileContent(repoUrl, token, service.workingDirectory + "/pom.xml");
        else if ("SPRING_BOOT_GRADLE".equals(service.stackType))
            return gitHubService.getFileContent(repoUrl, token, service.workingDirectory + "/build.gradle");
        return "";
    }

    private String getBuildFileContentByType(String repoUrl, String token, String stackType, String workingDirectory) throws Exception {
        if ("SPRING_BOOT_MAVEN".equals(stackType))
            return gitHubService.getFileContent(repoUrl, token, workingDirectory + "/pom.xml");
        else if ("SPRING_BOOT_GRADLE".equals(stackType))
            return gitHubService.getFileContent(repoUrl, token, workingDirectory + "/build.gradle");
        return "";
    }

    private String extractDependencies(String buildFileContent, String stackType) {
        if ("SPRING_BOOT_MAVEN".equals(stackType)) return extractMavenDependencies(buildFileContent);
        if ("SPRING_BOOT_GRADLE".equals(stackType)) return extractGradleDependencies(buildFileContent);
        return "No dependencies detected";
    }

    private String extractSpringBootVersion(String buildFileContent, String stackType) {
        if ("SPRING_BOOT_MAVEN".equals(stackType)) return extractSpringBootVersionFromPom(buildFileContent);
        if ("SPRING_BOOT_GRADLE".equals(stackType)) return extractSpringBootVersionFromGradle(buildFileContent);
        return "Unknown";
    }

    private String extractNodeDependencies(String packageContent) {
        if (packageContent == null) return "No major dependencies detected";
        StringBuilder deps = new StringBuilder();
        if (packageContent.contains("\"react\"")) deps.append("react, ");
        if (packageContent.contains("\"vue\"")) deps.append("vue, ");
        if (packageContent.contains("\"angular\"")) deps.append("angular, ");
        if (packageContent.contains("\"express\"")) deps.append("express, ");
        if (packageContent.contains("\"next\"")) deps.append("next, ");
        if (packageContent.contains("\"nuxt\"")) deps.append("nuxt, ");
        if (packageContent.contains("\"typescript\"")) deps.append("typescript, ");
        if (packageContent.contains("\"webpack\"")) deps.append("webpack, ");
        if (packageContent.contains("\"vite\"")) deps.append("vite, ");
        return deps.length() == 0 ? "No major dependencies detected" : deps.substring(0, deps.length() - 2);
    }

    private boolean isNodeBackend(String packageContent) {
        return packageContent != null && (
                packageContent.contains("\"express\"") ||
                packageContent.contains("\"koa\"") ||
                packageContent.contains("\"fastify\"") ||
                packageContent.contains("\"nestjs\"") ||
                packageContent.contains("\"hapi\"") ||
                packageContent.contains("\"socket.io\"")
        );
    }

    private boolean isNodeFrontend(String packageContent) {
        return packageContent != null && (
                packageContent.contains("\"react\"") ||
                packageContent.contains("\"vue\"") ||
                packageContent.contains("\"angular\"") ||
                packageContent.contains("\"svelte\"") ||
                packageContent.contains("\"@angular/core\"") ||
                packageContent.contains("\"react-dom\"")
        );
    }

  

    private static class DetectedStack {
        String stackType;
        String workingDirectory;

        DetectedStack(String stackType, String workingDirectory) {
            this.stackType = stackType;
            this.workingDirectory = workingDirectory;
        }
    }

    private DetectedStack detectStackTypeRecursively(List<Map<String, Object>> files, String repoUrl, String token, String currentPath) {
        for (Map<String, Object> file : files) {
            String fileName = (String) file.get("name");
            String type = (String) file.get("type");
            String newPath = currentPath.isEmpty() ? fileName : currentPath + "/" + fileName;

            if ("pom.xml".equals(fileName))
                return new DetectedStack("SPRING_BOOT_MAVEN", currentPath.isEmpty() ? "." : "./" + currentPath);
            if ("build.gradle".equals(fileName) || "build.gradle.kts".equals(fileName))
                return new DetectedStack("SPRING_BOOT_GRADLE", currentPath.isEmpty() ? "." : "./" + currentPath);
            if ("package.json".equals(fileName))
                return new DetectedStack("NODE_JS", currentPath.isEmpty() ? "." : "./" + currentPath);

            if ("dir".equals(type)) {
                List<Map<String, Object>> subFiles = gitHubService.getRepositoryContents(repoUrl, token, newPath);
                DetectedStack subResult = detectStackTypeRecursively(subFiles, repoUrl, token, newPath);
                if (!"GENERIC".equals(subResult.stackType))
                    return subResult;
            }
        }
        return new DetectedStack("GENERIC", ".");
    }

    private String detectJavaVersion(String repoUrl, String token, String stackType, String workingDirectory) {
        try {
            if ("SPRING_BOOT_MAVEN".equals(stackType)) {
                String pom = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/pom.xml");
                return extractJavaVersionFromPom(pom);
            } else if ("SPRING_BOOT_GRADLE".equals(stackType)) {
                String gradle = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/build.gradle");
                return extractJavaVersionFromGradle(gradle);
            }
        } catch (Exception e) {
            return "17";
        }
        return "17";
    }

    private String detectBuildTool(String stackType) {
        return switch (stackType) {
            case "SPRING_BOOT_MAVEN" -> "Maven";
            case "SPRING_BOOT_GRADLE" -> "Gradle";
            case "NODE_JS" -> "npm";
            default -> "Generic";
        };
    }

    private String detectLanguage(String stackType) {
        return switch (stackType) {
            case "SPRING_BOOT_MAVEN", "SPRING_BOOT_GRADLE" -> "Java";
            case "NODE_JS" -> "JavaScript";
            default -> "Unknown";
        };
    }

    private Map<String, Object> analyzeProjectDetails(String repoUrl, String token, String stackType, String workingDirectory) {
        Map<String, Object> details = new HashMap<>();
        try {
            if ("SPRING_BOOT_MAVEN".equals(stackType)) {
                String pom = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/pom.xml");
                details.put("springBootVersion", extractSpringBootVersionFromPom(pom));
                details.put("dependencies", extractMavenDependencies(pom));
                details.put("packaging", extractPackaging(pom));
            } else if ("SPRING_BOOT_GRADLE".equals(stackType)) {
                String gradle = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/build.gradle");
                details.put("springBootVersion", extractSpringBootVersionFromGradle(gradle));
                details.put("dependencies", extractGradleDependencies(gradle));
            } else if ("NODE_JS".equals(stackType)) {
                String pkg = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/package.json");
                details.put("nodeVersion", extractNodeVersion(pkg));
                details.put("scripts", extractNpmScripts(pkg));
                details.put("framework", detectNodeFramework(pkg));
            }
        } catch (Exception e) {
            details.put("error", "Analysis failed: " + e.getMessage());
        }
        return details;
    }

    private String extractJavaVersionFromPom(String pomContent) {
        if (pomContent == null) return "17";
        Matcher m = Pattern.compile("<java\\.version>(\\d+)</java\\.version>").matcher(pomContent);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<maven\\.compiler\\.source>(\\d+)</maven\\.compiler\\.source>").matcher(pomContent);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<maven\\.compiler\\.target>(\\d+)</maven\\.compiler\\.target>").matcher(pomContent);
        if (m.find()) return m.group(1);
        return "17";
    }

    private String extractJavaVersionFromGradle(String gradleContent) {
        if (gradleContent == null) return "17";
        Matcher m = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(\\d+)['\"]?").matcher(gradleContent);
        if (m.find()) return m.group(1);
        m = Pattern.compile("targetCompatibility\\s*=\\s*['\"]?(\\d+)['\"]?").matcher(gradleContent);
        if (m.find()) return m.group(1);
        m = Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)").matcher(gradleContent);
        if (m.find()) return m.group(1);
        return "17";
    }

    private String extractSpringBootVersionFromPom(String pomContent) {
        if (pomContent == null) return "Unknown";
        Matcher m = Pattern.compile("<parent>.*?<groupId>org\\.springframework\\.boot</groupId>.*?<version>([0-9.]+)</version>.*?</parent>", Pattern.DOTALL).matcher(pomContent);
        if (m.find()) return m.group(1);
        m = Pattern.compile("<spring-boot\\.version>([0-9.]+)</spring-boot\\.version>").matcher(pomContent);
        if (m.find()) return m.group(1);
        return "Unknown";
    }

    private String extractSpringBootVersionFromGradle(String gradleContent) {
        if (gradleContent == null) return "Unknown";
        Matcher m = Pattern.compile("id\\s+['\"]org\\.springframework\\.boot['\"]\\s+version\\s+['\"]([0-9.]+)['\"]").matcher(gradleContent);
        if (m.find()) return m.group(1);
        return "Unknown";
    }

    private String extractMavenDependencies(String pomContent) {
        if (pomContent == null) return "No Spring Boot dependencies detected";
        StringBuilder deps = new StringBuilder();
        if (pomContent.contains("spring-boot-starter-web")) deps.append("spring-boot-starter-web, ");
        if (pomContent.contains("spring-boot-starter-data-jpa")) deps.append("spring-boot-starter-data-jpa, ");
        if (pomContent.contains("spring-boot-starter-security")) deps.append("spring-boot-starter-security, ");
        if (pomContent.contains("spring-boot-starter-test")) deps.append("spring-boot-starter-test, ");
        if (pomContent.contains("spring-boot-starter-data-mongodb")) deps.append("spring-boot-starter-data-mongodb, ");
        return deps.length() == 0 ? "No Spring Boot dependencies detected" : deps.substring(0, deps.length() - 2);
    }

    private String extractGradleDependencies(String gradleContent) {
        if (gradleContent == null) return "No Spring Boot dependencies detected";
        StringBuilder deps = new StringBuilder();
        if (gradleContent.contains("spring-boot-starter-web")) deps.append("spring-boot-starter-web, ");
        if (gradleContent.contains("spring-boot-starter-data-jpa")) deps.append("spring-boot-starter-data-jpa, ");
        if (gradleContent.contains("spring-boot-starter-security")) deps.append("spring-boot-starter-security, ");
        if (gradleContent.contains("spring-boot-starter-test")) deps.append("spring-boot-starter-test, ");
        if (gradleContent.contains("spring-boot-starter-data-mongodb")) deps.append("spring-boot-starter-data-mongodb, ");
        return deps.length() == 0 ? "No Spring Boot dependencies detected" : deps.substring(0, deps.length() - 2);
    }

    private String extractPackaging(String pomContent) {
        if (pomContent == null) return "jar";
        Matcher m = Pattern.compile("<packaging>([^<]+)</packaging>").matcher(pomContent);
        if (m.find()) return m.group(1);
        return "jar";
    }

    private String extractNodeVersion(String packageContent) {
        if (packageContent == null) return "Latest";
        Matcher m = Pattern.compile("\"engines\"\\s*:\\s*\\{[^}]*\"node\"\\s*:\\s*\"([^\"]+)\"").matcher(packageContent);
        if (m.find()) return m.group(1);
        return "Latest";
    }

    private String extractNpmScripts(String packageContent) {
        if (packageContent == null) return "No scripts detected";
        StringBuilder scripts = new StringBuilder();
        if (packageContent.contains("\"build\"")) scripts.append("build, ");
        if (packageContent.contains("\"test\"")) scripts.append("test, ");
        if (packageContent.contains("\"start\"")) scripts.append("start, ");
        if (packageContent.contains("\"dev\"")) scripts.append("dev, ");
        if (packageContent.contains("\"serve\"")) scripts.append("serve, ");
        if (packageContent.contains("\"lint\"")) scripts.append("lint, ");
        return scripts.length() == 0 ? "No scripts detected" : scripts.substring(0, scripts.length() - 2);
    }

    private String detectNodeFramework(String packageContent) {
        if (packageContent == null) return "Vanilla Node.js";
        if (packageContent.contains("\"react\"")) return "React";
        if (packageContent.contains("\"vue\"")) return "Vue.js";
        if (packageContent.contains("\"angular\"") || packageContent.contains("\"@angular/core\"")) return "Angular";
        if (packageContent.contains("\"express\"")) return "Express.js";
        if (packageContent.contains("\"next\"")) return "Next.js";
        if (packageContent.contains("\"nuxt\"")) return "Nuxt.js";
        if (packageContent.contains("\"svelte\"")) return "Svelte";
        if (packageContent.contains("\"nestjs\"") || packageContent.contains("\"@nestjs/core\"")) return "NestJS";
        return "Vanilla Node.js";
    }
}