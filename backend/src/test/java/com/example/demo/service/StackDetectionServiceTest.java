package com.example.demo.service;  

import com.example.demo.dto.ServiceAnalysis;  
import org.junit.jupiter.api.BeforeEach;  
import org.junit.jupiter.api.Test;  
import org.junit.jupiter.api.extension.ExtendWith;  
import org.mockito.InjectMocks;  
import org.mockito.Mock;  
import org.mockito.junit.jupiter.MockitoExtension;  

import java.util.*;  

import static org.junit.jupiter.api.Assertions.*;  
import static org.mockito.ArgumentMatchers.*;  
import static org.mockito.Mockito.*;  

@ExtendWith(MockitoExtension.class)  
class StackDetectionServiceTest {  
  
    @Mock  
    private GitHubService gitHubService;  
  
    @InjectMocks  
    private StackDetectionService stackDetectionService;  
  
    private static final String REPO_URL = "https://github.com/user/test-repo";  
    private static final String TOKEN = "test-token";  
  
    // =====================================================================================  
    // Tests pour detectAllServices() - Teste isSpringBootPom() indirectement  
    // =====================================================================================  
  
    @Test  
    void testDetectAllServices_SpringBootMaven_DetectsCorrectly() {  
        // Arrange - Simuler la structure du repository  
        Map<String, Object> pomFile = new HashMap<>();  
        pomFile.put("name", "pom.xml");  
        pomFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(pomFile);  
          
        String pomContent = """  
            <project>  
                <parent>  
                    <groupId>org.springframework.boot</groupId>  
                    <artifactId>spring-boot-starter-parent</artifactId>  
                </parent>  
                <dependencies>  
                    <dependency>  
                        <groupId>org.springframework.boot</groupId>  
                        <artifactId>spring-boot-starter-web</artifactId>  
                    </dependency>  
                </dependencies>  
            </project>  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("pom.xml")))  
            .thenReturn(pomContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertNotNull(services);  
        assertEquals(1, services.size());  
        assertEquals("SPRING_BOOT_MAVEN", services.get(0).getStackType());  
        assertEquals(".", services.get(0).getWorkingDirectory());  
          
        verify(gitHubService).getFileContent(eq(REPO_URL), eq(TOKEN), eq("pom.xml"));  
    }  
  
    @Test  
    void testDetectAllServices_NonSpringBootPom_NotDetected() {  
        // Arrange - pom.xml sans Spring Boot  
        Map<String, Object> pomFile = new HashMap<>();  
        pomFile.put("name", "pom.xml");  
        pomFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(pomFile);  
          
        String pomContent = """  
            <project>  
                <groupId>com.example</groupId>  
                <artifactId>regular-maven-project</artifactId>  
                <dependencies>  
                    <dependency>  
                        <groupId>junit</groupId>  
                        <artifactId>junit</artifactId>  
                    </dependency>  
                </dependencies>  
            </project>  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("pom.xml")))  
            .thenReturn(pomContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertTrue(services.isEmpty(), "Non-Spring Boot pom.xml should not be detected");  
    }  
  
    // =====================================================================================  
    // Tests pour detectAllServices() - Teste isSpringBootGradle() indirectement  
    // =====================================================================================  
  
    @Test  
    void testDetectAllServices_SpringBootGradle_DetectsCorrectly() {  
        // Arrange  
        Map<String, Object> gradleFile = new HashMap<>();  
        gradleFile.put("name", "build.gradle");  
        gradleFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(gradleFile);  
          
        String gradleContent = """  
            plugins {  
                id 'org.springframework.boot' version '3.0.0'  
                id 'java'  
            }  
              
            dependencies {  
                implementation 'org.springframework.boot:spring-boot-starter-web'  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("build.gradle")))  
            .thenReturn(gradleContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertNotNull(services);  
        assertEquals(1, services.size());  
        assertEquals("SPRING_BOOT_GRADLE", services.get(0).getStackType());  
    }  
  
    @Test  
    void testDetectAllServices_AndroidGradle_NotDetected() {  
        // Arrange - Gradle Android (doit être filtré)  
        Map<String, Object> gradleFile = new HashMap<>();  
        gradleFile.put("name", "build.gradle");  
        gradleFile.put("type", "file");  
          
        Map<String, Object> manifestFile = new HashMap<>();  
        manifestFile.put("name", "app");  
        manifestFile.put("type", "dir");  
          
        List<Map<String, Object>> files = Arrays.asList(gradleFile, manifestFile);  
          
        String gradleContent = """  
            plugins {  
                id 'com.android.application'  
                id 'org.springframework.boot' version '3.0.0'  
            }  
              
            android {  
                compileSdk 33  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("build.gradle")))  
            .thenReturn(gradleContent);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("app/src/main/AndroidManifest.xml")))  
            .thenReturn("<manifest></manifest>");  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertTrue(services.isEmpty(), "Android Gradle projects should be filtered out");  
    }  
  
    // =====================================================================================  
    // Tests pour detectAllServices() - Teste isMobileNodePackage() indirectement  
    // =====================================================================================  
  
    @Test  
    void testDetectAllServices_NodeJs_DetectsCorrectly() {  
        // Arrange  
        Map<String, Object> packageFile = new HashMap<>();  
        packageFile.put("name", "package.json");  
        packageFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(packageFile);  
          
        String packageContent = """  
            {  
                "name": "my-node-app",  
                "dependencies": {  
                    "express": "^4.18.0"  
                }  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("package.json")))  
            .thenReturn(packageContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertNotNull(services);  
        assertEquals(1, services.size());  
        assertEquals("NODE_JS", services.get(0).getStackType());  
    }  
  
    @Test  
    void testDetectAllServices_ReactNative_NotDetected() {  
        // Arrange - React Native (mobile, doit être filtré)  
        Map<String, Object> packageFile = new HashMap<>();  
        packageFile.put("name", "package.json");  
        packageFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(packageFile);  
          
        String packageContent = """  
            {  
                "name": "my-mobile-app",  
                "dependencies": {  
                    "react-native": "^0.70.0",  
                    "react": "^18.0.0"  
                }  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("package.json")))  
            .thenReturn(packageContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertTrue(services.isEmpty(), "React Native projects should be filtered out");  
    }  
  
    @Test  
    void testDetectAllServices_Expo_NotDetected() {  
        // Arrange - Expo (mobile, doit être filtré)  
        Map<String, Object> packageFile = new HashMap<>();  
        packageFile.put("name", "package.json");  
        packageFile.put("type", "file");  
          
        List<Map<String, Object>> files = Arrays.asList(packageFile);  
          
        String packageContent = """  
            {  
                "name": "my-expo-app",  
                "dependencies": {  
                    "expo": "^47.0.0"  
                }  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(files);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("package.json")))  
            .thenReturn(packageContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertTrue(services.isEmpty(), "Expo projects should be filtered out");  
    }  
  
    // =====================================================================================  
    // Tests pour multi-services  
    // =====================================================================================  
  
    @Test  
    void testDetectAllServices_MultiService_DetectsAll() {  
        // Arrange - Repository avec backend Spring Boot et frontend Node.js  
        Map<String, Object> backendDir = new HashMap<>();  
        backendDir.put("name", "backend");  
        backendDir.put("type", "dir");  
          
        Map<String, Object> frontendDir = new HashMap<>();  
        frontendDir.put("name", "frontend");  
        frontendDir.put("type", "dir");  
          
        List<Map<String, Object>> rootFiles = Arrays.asList(backendDir, frontendDir);  
          
        // Backend files  
        Map<String, Object> pomFile = new HashMap<>();  
        pomFile.put("name", "pom.xml");  
        pomFile.put("type", "file");  
        List<Map<String, Object>> backendFiles = Arrays.asList(pomFile);  
          
        // Frontend files  
        Map<String, Object> packageFile = new HashMap<>();  
        packageFile.put("name", "package.json");  
        packageFile.put("type", "file");  
        List<Map<String, Object>> frontendFiles = Arrays.asList(packageFile);  
          
        String pomContent = """  
            <project>  
                <groupId>org.springframework.boot</groupId>  
                <artifactId>spring-boot-starter-parent</artifactId>  
            </project>  
            """;  
          
        String packageContent = """  
            {  
                "name": "frontend",  
                "dependencies": {  
                    "react": "^18.0.0"  
                }  
            }  
            """;  
          
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), isNull()))  
            .thenReturn(rootFiles);  
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), eq("backend")))  
            .thenReturn(backendFiles);  
        when(gitHubService.getRepositoryContents(eq(REPO_URL), eq(TOKEN), eq("frontend")))  
            .thenReturn(frontendFiles);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("backend/pom.xml")))  
            .thenReturn(pomContent);  
        when(gitHubService.getFileContent(eq(REPO_URL), eq(TOKEN), eq("frontend/package.json")))  
            .thenReturn(packageContent);  
          
        // Act  
        List<ServiceAnalysis> services = stackDetectionService.analyzeAllServices(REPO_URL, TOKEN);  
          
        // Assert  
        assertNotNull(services);  
        assertEquals(2, services.size());  
          
        // Vérifier que les deux services sont détectés  
        boolean hasSpringBoot = services.stream()  
            .anyMatch(s -> "SPRING_BOOT_MAVEN".equals(s.getStackType()));  
        boolean hasNodeJs = services.stream()  
            .anyMatch(s -> "NODE_JS".equals(s.getStackType()));  
          
        assertTrue(hasSpringBoot, "Should detect Spring Boot service");  
        assertTrue(hasNodeJs, "Should detect Node.js service");  
    }  
}