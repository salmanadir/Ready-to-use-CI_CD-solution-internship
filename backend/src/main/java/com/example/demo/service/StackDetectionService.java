package com.example.demo.service;

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

        return analysis;
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
                String pomContent = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/pom.xml");
                return extractJavaVersionFromPom(pomContent);
            } else if ("SPRING_BOOT_GRADLE".equals(stackType)) {
                String gradleContent = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/build.gradle");
                return extractJavaVersionFromGradle(gradleContent);
            }
        } catch (Exception e) {
            return "17"; 
        }
        return null;
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
                String pomContent = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/pom.xml");
                details.put("springBootVersion", extractSpringBootVersion(pomContent));
                details.put("dependencies", extractMavenDependencies(pomContent));
                details.put("packaging", extractPackaging(pomContent));
            } else if ("SPRING_BOOT_GRADLE".equals(stackType)) {
                String gradleContent = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/build.gradle");
                details.put("springBootVersion", extractSpringBootVersionFromGradle(gradleContent));
                details.put("dependencies", extractGradleDependencies(gradleContent));
            } else if ("NODE_JS".equals(stackType)) {
                String packageContent = gitHubService.getFileContent(repoUrl, token, workingDirectory + "/package.json");
                details.put("nodeVersion", extractNodeVersion(packageContent));
                details.put("scripts", extractNpmScripts(packageContent));
                details.put("framework", detectNodeFramework(packageContent));
            }
        } catch (Exception e) {
            details.put("error", "Analysis failed: " + e.getMessage());
        }
        return details;
    }

  
    private String extractJavaVersionFromPom(String pomContent) {
        Pattern pattern = Pattern.compile("<java\\.version>(\\d+)</java\\.version>");
        Matcher matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("<maven\\.compiler\\.source>(\\d+)</maven\\.compiler\\.source>");
        matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("<maven\\.compiler\\.target>(\\d+)</maven\\.compiler\\.target>");
        matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);

        return "17";
    }


    private String extractJavaVersionFromGradle(String gradleContent) {
        Pattern pattern = Pattern.compile("sourceCompatibility\\s*=\\s*['\"]?(\\d+)['\"]?");
        Matcher matcher = pattern.matcher(gradleContent);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("targetCompatibility\\s*=\\s*['\"]?(\\d+)['\"]?");
        matcher = pattern.matcher(gradleContent);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("JavaLanguageVersion\\.of\\((\\d+)\\)");
        matcher = pattern.matcher(gradleContent);
        if (matcher.find()) return matcher.group(1);

        return "17";
    }

    private String extractSpringBootVersion(String pomContent) {
        Pattern pattern = Pattern.compile("<parent>.*?<groupId>org\\.springframework\\.boot</groupId>.*?<version>([0-9.]+)</version>.*?</parent>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);

        pattern = Pattern.compile("<spring-boot\\.version>([0-9.]+)</spring-boot\\.version>");
        matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);

        return "Unknown";
    }

    private String extractSpringBootVersionFromGradle(String gradleContent) {
        Pattern pattern = Pattern.compile("id\\s+['\"]org\\.springframework\\.boot['\"]\\s+version\\s+['\"]([0-9.]+)['\"]");
        Matcher matcher = pattern.matcher(gradleContent);
        if (matcher.find()) return matcher.group(1);

        return "Unknown";
    }

    private String extractMavenDependencies(String pomContent) {
        StringBuilder deps = new StringBuilder();
        if (pomContent.contains("spring-boot-starter-web")) deps.append("spring-boot-starter-web, ");
        if (pomContent.contains("spring-boot-starter-data-jpa")) deps.append("spring-boot-starter-data-jpa, ");
        if (pomContent.contains("spring-boot-starter-security")) deps.append("spring-boot-starter-security, ");
        if (pomContent.contains("spring-boot-starter-test")) deps.append("spring-boot-starter-test, ");
        return deps.length() == 0 ? "No Spring Boot dependencies detected" : deps.substring(0, deps.length() - 2);
    }

    private String extractGradleDependencies(String gradleContent) {
        StringBuilder deps = new StringBuilder();
        if (gradleContent.contains("spring-boot-starter-web")) deps.append("spring-boot-starter-web, ");
        if (gradleContent.contains("spring-boot-starter-data-jpa")) deps.append("spring-boot-starter-data-jpa, ");
        if (gradleContent.contains("spring-boot-starter-security")) deps.append("spring-boot-starter-security, ");
        if (gradleContent.contains("spring-boot-starter-test")) deps.append("spring-boot-starter-test, ");
        return deps.length() == 0 ? "No Spring Boot dependencies detected" : deps.substring(0, deps.length() - 2);
    }

    private String extractPackaging(String pomContent) {
        Pattern pattern = Pattern.compile("<packaging>([^<]+)</packaging>");
        Matcher matcher = pattern.matcher(pomContent);
        if (matcher.find()) return matcher.group(1);
        return "jar";
    }

    private String extractNodeVersion(String packageContent) {
        Pattern pattern = Pattern.compile("\"engines\"\\s*:\\s*\\{[^}]*\"node\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(packageContent);
        if (matcher.find()) return matcher.group(1);
        return "Latest";
    }

    private String extractNpmScripts(String packageContent) {
        StringBuilder scripts = new StringBuilder();
        if (packageContent.contains("\"build\"")) scripts.append("build, ");
        if (packageContent.contains("\"test\"")) scripts.append("test, ");
        if (packageContent.contains("\"start\"")) scripts.append("start, ");
        if (packageContent.contains("\"dev\"")) scripts.append("dev, ");
        return scripts.length() == 0 ? "No scripts detected" : scripts.substring(0, scripts.length() - 2);
    }

    private String detectNodeFramework(String packageContent) {
        if (packageContent.contains("\"react\"")) return "React";
        if (packageContent.contains("\"vue\"")) return "Vue.js";
        if (packageContent.contains("\"angular\"")) return "Angular";
        if (packageContent.contains("\"express\"")) return "Express.js";
        if (packageContent.contains("\"next\"")) return "Next.js";
        if (packageContent.contains("\"nuxt\"")) return "Nuxt.js";
        return "Vanilla Node.js";
    }
}
