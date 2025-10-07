
package com.example.demo.dto;

import lombok.Data;

@Data
public class ComposePlan {
  public boolean shouldGenerateCompose;
  public String composePath;   // "docker-compose.dev.yml"
  public String content;
}