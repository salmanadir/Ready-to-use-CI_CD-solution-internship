// src/main/java/com/example/demo/model/DockerComposeHistory.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "docker_compose_history", indexes = {
    @Index(name="idx_dch_repo", columnList = "repo_id"),
    @Index(name="idx_dch_path", columnList = "composePath")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DockerComposeHistory {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long compose_id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "repo_id")
  private Repo repo;

  /** Path du fichier compose dans le repo (ex: "docker-compose.yml" ou "deploy/compose.yml") */
  @Column(length = 400, nullable = false)
  private String composePath;

  /** Contenu complet du compose (Yaml) */
 
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  /** Source : EXISTING (déjà présent), GENERATED (créé par nous), UPDATED (modifié par nous) */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Source source;

  /** Mode : SINGLE ou MULTI (utile en multi-services) */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Mode mode;

  /**
   * Optionnel : pour tracer rapidement (CSV type "frontend,backend,database"),
   * ou laisser null si non pertinent.
   */
  @Column(length = 500)
  private String serviceNames;

  /** SHA du commit pushé par l'action d’écriture (si applicable) */
  @Column(length = 80)
  private String commitHash;

  @CreationTimestamp
  private OffsetDateTime createdAt;

  public enum Source { GENERATED, EXISTING, UPDATED }
  public enum Mode { SINGLE, MULTI }
}
