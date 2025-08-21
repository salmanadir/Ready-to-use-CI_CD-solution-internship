// src/main/java/com/example/demo/model/DockerfileHistory.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "dockerfile_history", indexes = {
    @Index(name="idx_dfh_repo", columnList = "repo_id"),
    @Index(name="idx_dfh_service", columnList = "serviceId"),
    @Index(name="idx_dfh_path", columnList = "dockerfilePath")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DockerfileHistory {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long dockerfile_id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "repo_id")
  private Repo repo;

  @Column(length = 100)
  private String serviceId;                 // null en single

  @Column(length = 300, nullable = false)
  private String workingDirectory;         

  @Column(length = 400, nullable = false)
  private String dockerfilePath;           

  @Lob
  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;                  

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Source source;                   

  @Column(length = 80)
  private String commitHash;             

  @CreationTimestamp
  private OffsetDateTime createdAt;

  public enum Source { GENERATED, EXISTING, UPDATED }
}
