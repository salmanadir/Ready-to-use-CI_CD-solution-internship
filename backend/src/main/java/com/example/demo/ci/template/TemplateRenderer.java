package com.example.demo.ci.template;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.IOException;

@Component
public class TemplateRenderer {

    public String renderTemplate(String templatePath, Map<String, String> replacements) throws IOException {
        InputStream inputStream = new ClassPathResource(templatePath).getInputStream();
        String template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        return template;
    }
}
