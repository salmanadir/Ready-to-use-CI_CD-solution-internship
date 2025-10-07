// src/main/java/com/example/demo/dto/ComposeProdPlan.java
package com.example.demo.dto;

import lombok.Data;

@Data
public class ComposeProdPlan {
  public boolean shouldGenerate = false;
  public String composePath = "docker-compose.prod.yml";
  public String content;    // YAML final
  public String envPath = ".env";
  public String envContent; // REGISTRY=..., IMAGE_TAG=...
}
