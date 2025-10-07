package com.example.demo.service;  
  
import com.example.demo.dto.StackAnalysis;  
import org.junit.jupiter.api.BeforeEach;  
import org.junit.jupiter.api.Test;  
  
import static org.assertj.core.api.Assertions.assertThat;  
  
class DockerfileGenerationServiceTest {  
  
    private DockerfileGenerationService service;  
  
    @BeforeEach  
    void setUp() {  
        service = new DockerfileGenerationService();  
    }  
  
    // ========== Tests  pour single-service ==========  
      
    @Test  
    void testGenerateDockerfileForSpringBootMaven() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("SPRING_BOOT_MAVEN");  
        info.setBuildTool("Maven");  
        info.setJavaVersion("17");  
        info.setWorkingDirectory(".");  
  
        String dockerfile = service.generate(info);  
  
        assertThat(dockerfile).isNotNull();  
        // Vérifier le stage de build  
        assertThat(dockerfile).contains("FROM maven:");  
        assertThat(dockerfile).contains("eclipse-temurin-17");  
        // Vérifier le stage runtime  
        assertThat(dockerfile).contains("FROM eclipse-temurin:17-jre");  
        assertThat(dockerfile).contains("mvn -B -DskipTests clean package");  
        assertThat(dockerfile).contains("EXPOSE 8080");  
        assertThat(dockerfile).contains("ENTRYPOINT");  
    }  
  
    @Test  
    void testGenerateDockerfileForSpringBootGradle() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("SPRING_BOOT_GRADLE");  
        info.setBuildTool("Gradle");  
        info.setJavaVersion("11");  
        info.setWorkingDirectory("./backend");  
  
        String dockerfile = service.generate(info);  
  
        assertThat(dockerfile).isNotNull();  
        // Vérifier le stage de build  
        assertThat(dockerfile).contains("FROM gradle:");  
        assertThat(dockerfile).contains("jdk11");  
        // Vérifier le stage runtime  
        assertThat(dockerfile).contains("FROM eclipse-temurin:11-jre");  
        assertThat(dockerfile).contains("gradle build -x test --no-daemon");  
        assertThat(dockerfile).contains("EXPOSE 8080");  
        assertThat(dockerfile).contains("ENTRYPOINT");  
    }  
  
    @Test  
    void testGenerateDockerfileForNodeJS() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("NODE_JS");  
        info.setBuildTool("npm");  
        info.setWorkingDirectory("./frontend");  
  
        String dockerfile = service.generate(info);  
  
        assertThat(dockerfile).isNotNull();  
        assertThat(dockerfile).contains("FROM node:");  
        assertThat(dockerfile).contains("npm");  
        assertThat(dockerfile).contains("EXPOSE");  
    }  
  
    // ==========  test multi-service ==========  
      
    @Test  
    void testGenerateDockerfileForMultiServiceRepo() {  
        // Test 1: Backend Maven dans ./backend  
        StackAnalysis backendInfo = new StackAnalysis();  
        backendInfo.setStackType("SPRING_BOOT_MAVEN");  
        backendInfo.setBuildTool("Maven");  
        backendInfo.setJavaVersion("17");  
        backendInfo.setWorkingDirectory("./backend");  
  
        String backendDockerfile = service.generate(backendInfo);  
  
        assertThat(backendDockerfile).isNotNull();  
        assertThat(backendDockerfile).contains("FROM maven:");  
        assertThat(backendDockerfile).contains("eclipse-temurin-17");  
        assertThat(backendDockerfile).contains("FROM eclipse-temurin:17-jre");  
        assertThat(backendDockerfile).contains("mvn -B -DskipTests clean package");  
        assertThat(backendDockerfile).contains("WORKDIR /src/backend");  
        assertThat(backendDockerfile).contains("EXPOSE 8080");  
  
        // Test 2: Frontend Node.js dans ./frontend  
        StackAnalysis frontendInfo = new StackAnalysis();  
        frontendInfo.setStackType("NODE_JS");  
        frontendInfo.setBuildTool("npm");  
        frontendInfo.setWorkingDirectory("./frontend");  
  
        String frontendDockerfile = service.generate(frontendInfo);  
  
        assertThat(frontendDockerfile).isNotNull();  
        assertThat(frontendDockerfile).contains("FROM node:");  
        assertThat(frontendDockerfile).contains("npm");  
        assertThat(frontendDockerfile).contains("WORKDIR");  
        assertThat(frontendDockerfile).contains("EXPOSE");  
          
        assertThat(backendDockerfile).isNotEqualTo(frontendDockerfile);  
    }  
  
    @Test  
    void testGenerateDockerfileWithDifferentWorkingDirectories() {  
        StackAnalysis infoRoot = new StackAnalysis();  
        infoRoot.setStackType("SPRING_BOOT_MAVEN");  
        infoRoot.setBuildTool("Maven");  
        infoRoot.setJavaVersion("17");  
        infoRoot.setWorkingDirectory(".");  
  
        StackAnalysis infoBackend = new StackAnalysis();  
        infoBackend.setStackType("SPRING_BOOT_MAVEN");  
        infoBackend.setBuildTool("Maven");  
        infoBackend.setJavaVersion("17");  
        infoBackend.setWorkingDirectory("./backend");  
  
        String dockerfileRoot = service.generate(infoRoot);  
        String dockerfileBackend = service.generate(infoBackend);  
  
        assertThat(dockerfileRoot).contains("FROM maven:");  
        assertThat(dockerfileBackend).contains("FROM maven:");  
          
        assertThat(dockerfileRoot).contains("WORKDIR /src");  
        assertThat(dockerfileBackend).contains("WORKDIR /src/backend");  
    }  
}
