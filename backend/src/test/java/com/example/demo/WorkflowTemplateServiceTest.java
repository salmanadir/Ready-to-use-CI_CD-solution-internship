package com.example.demo.service;  
  
import com.example.demo.dto.StackAnalysis;  
import org.junit.jupiter.api.BeforeEach;  
import org.junit.jupiter.api.Test;  
  
import static org.assertj.core.api.Assertions.assertThat;  
  
class WorkflowTemplateServiceTest {  
  
    private WorkflowTemplateService service;  
  
    @BeforeEach  
    void setUp() {  
        service = new WorkflowTemplateService();  
    }  
  
    // ========== Tests pour single-service ==========  
      
    @Test  
    void testGetTemplatePathForSpringBootMaven() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("SPRING_BOOT_MAVEN");  
        info.setBuildTool("Maven");  
        info.setJavaVersion("17");  
          
        String templatePath = service.getTemplatePath(info);  
          
        assertThat(templatePath).isNotNull();  
        assertThat(templatePath).isNotEmpty();  
        // Le template doit être lié à Maven  
        assertThat(templatePath.toLowerCase()).contains("maven");  
    }  
  
    @Test  
    void testGetTemplatePathForSpringBootGradle() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("SPRING_BOOT_GRADLE");  
        info.setBuildTool("Gradle");  
        info.setJavaVersion("11");  
          
        String templatePath = service.getTemplatePath(info);  
          
        assertThat(templatePath).isNotNull();  
        assertThat(templatePath).isNotEmpty();  
        // Le template doit être lié à Gradle  
        assertThat(templatePath.toLowerCase()).contains("gradle");  
    }  
  
    @Test  
    void testGetTemplatePathForNodeJS() {  
        StackAnalysis info = new StackAnalysis();  
        info.setStackType("NODE_JS");  
        info.setBuildTool("npm");  
          
        String templatePath = service.getTemplatePath(info);  
          
        assertThat(templatePath).isNotNull();  
        assertThat(templatePath).isNotEmpty();  
        // Le template doit être lié à Node.js  
        assertThat(templatePath.toLowerCase()).containsAnyOf("node", "npm");  
    }  
  
    // ========== Test multi-service ==========  
      
    @Test  
    void testGetTemplatePathForMultiServiceRepo() {  
        // Backend Maven  
        StackAnalysis backendInfo = new StackAnalysis();  
        backendInfo.setStackType("SPRING_BOOT_MAVEN");  
        backendInfo.setBuildTool("Maven");  
        backendInfo.setJavaVersion("17");  
        backendInfo.setWorkingDirectory("./backend");  
          
        String backendTemplate = service.getTemplatePath(backendInfo);  
          
        assertThat(backendTemplate).isNotNull();  
        assertThat(backendTemplate.toLowerCase()).contains("maven");  
          
        // Frontend Node.js  
        StackAnalysis frontendInfo = new StackAnalysis();  
        frontendInfo.setStackType("NODE_JS");  
        frontendInfo.setBuildTool("npm");  
        frontendInfo.setWorkingDirectory("./frontend");  
          
        String frontendTemplate = service.getTemplatePath(frontendInfo);  
          
        assertThat(frontendTemplate).isNotNull();  
        assertThat(frontendTemplate.toLowerCase()).containsAnyOf("node", "npm");  
          
        // Les templates doivent être différents  
        assertThat(backendTemplate).isNotEqualTo(frontendTemplate);  
    }  
  
    @Test  
    void testGetTemplatePathConsistency() {  
        // Vérifier que le même input donne toujours le même template  
        StackAnalysis info1 = new StackAnalysis();  
        info1.setStackType("SPRING_BOOT_MAVEN");  
        info1.setBuildTool("Maven");  
        info1.setJavaVersion("17");  
          
        StackAnalysis info2 = new StackAnalysis();  
        info2.setStackType("SPRING_BOOT_MAVEN");  
        info2.setBuildTool("Maven");  
        info2.setJavaVersion("17");  
          
        String template1 = service.getTemplatePath(info1);  
        String template2 = service.getTemplatePath(info2);  
          
        assertThat(template1).isEqualTo(template2);  
    }  
}
