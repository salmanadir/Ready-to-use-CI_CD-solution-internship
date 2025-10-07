package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service dédié à la détection et la génération d'un docker-compose de PROD.
 */
@Service
public class DockerComposeService {

    private final GitHubService gitHubService;
    private final StackDetectionService stackDetectionService;

    public DockerComposeService(GitHubService gitHubService,
                                StackDetectionService stackDetectionService) {
        this.gitHubService = gitHubService;
        this.stackDetectionService = stackDetectionService;
    }

    /** Noms de fichiers compose possibles */
    private static final List<String> COMPOSE_FILES = List.of(
            "docker-compose.yml", "docker-compose.yaml", "compose.yaml"
    );

    /** Dossiers “habituels” à scanner en plus de la racine */
    private static final List<String> COMMON_DIRS = List.of(
            "deploy", "deployment", "deployments",
            "infra", "infrastructure",
            "ops",
            "docker", "compose",
            ".deploy", ".ops"
    );

    /** Cherche des compose files dans la racine + dossiers communs + (optionnel) dossiers de services. */
    public List<String> findComposeFiles(String repoUrl, String token, List<String> serviceDirs) {
        Set<String> found = new LinkedHashSet<>();

        // Racine
        found.addAll(listComposeInPath(repoUrl, token, null));

        // Dossiers “habituels”
        for (String dir : COMMON_DIRS) {
            found.addAll(listComposeInPath(repoUrl, token, dir));
        }

        // Dossiers de services (ex: ./backend, ./frontend)
        if (serviceDirs != null) {
            for (String wd : serviceDirs) {
                String clean = wd == null ? "" : wd.replaceFirst("^\\./", "");
                if (!clean.isBlank() && !clean.equals(".")) {
                    found.addAll(listComposeInPath(repoUrl, token, clean));
                }
            }
        }

        return new ArrayList<>(found);
    }

    private List<String> listComposeInPath(String repoUrl, String token, String path) {
        try {
            List<Map<String, Object>> files = gitHubService.getRepositoryContents(repoUrl, token, path);
            if (files == null) return List.of();
            String prefix = (path == null || path.isBlank()) ? "" : (path + "/");
            return files.stream()
                    .map(m -> String.valueOf(m.get("name")))
                    .filter(COMPOSE_FILES::contains)
                    .map(n -> prefix + n)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Construit un docker-compose.yml de PROD.
     * - images applicatives: ghcr.io/<owner>/<repo>-<serviceId>:latest
     * - DB: image officielle + env par défaut
     * - depends_on/ports/env repris depuis les métadonnées
     */
    public String generateProdComposeYaml(List<Map<String,Object>> services,
                                          List<Map<String,Object>> relationships,
                                          String imageBase /* owner/repo */) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Production docker-compose.yml — generated\n");
        sb.append("version: '3.8'\n");
        sb.append("services:\n");

        Map<String,String> idToName = new HashMap<>();
        for (Map<String,Object> svc : services) {
            idToName.put(String.valueOf(svc.get("id")), String.valueOf(svc.get("name")));
        }
        Map<String,List<String>> depends = new HashMap<>();
        if (relationships != null) {
            for (Map<String,Object> rel : relationships) {
                String from = String.valueOf(rel.get("from"));
                String to = String.valueOf(rel.get("to"));
                String fromName = idToName.get(from);
                String toName = idToName.get(to);
                if (fromName != null && toName != null) {
                    depends.computeIfAbsent(fromName, k -> new ArrayList<>()).add(toName);
                }
            }
        }

        for (Map<String,Object> svc : services) {
            String id   = String.valueOf(svc.get("id"));
            String name = String.valueOf(svc.get("name"));
            String kind = svc.get("kind") == null ? "service" : String.valueOf(svc.get("kind")).toLowerCase();
            @SuppressWarnings("unchecked")
            Map<String,Object> runtime = (Map<String,Object>) svc.get("runtime");
            @SuppressWarnings("unchecked")
            Map<String,Object> env = (Map<String,Object>) svc.get("env");
            if (env == null) env = new HashMap<>();

            String port = "8080";
            if (runtime != null && runtime.get("port") != null) {
                port = String.valueOf(runtime.get("port"));
            } else if (kind.contains("frontend")) {
                port = "80";
            } else if (kind.contains("node")) {
                port = "3000";
            }

            sb.append("  ").append(name).append(":\n");

            boolean isDb = kind.contains("database") || kind.contains("postgres") || kind.contains("mysql")
                    || kind.contains("mongo") || kind.contains("redis") || "database".equals(id);
            if (isDb) {
                String fw = String.valueOf(svc.getOrDefault("framework","")).toLowerCase();
                if (fw.contains("postgres")) {
                    sb.append("    image: postgres:16\n");
                    env.putIfAbsent("POSTGRES_DB", String.valueOf(svc.getOrDefault("name","appdb")));
                    env.putIfAbsent("POSTGRES_USER", "postgres");
                    env.putIfAbsent("POSTGRES_PASSWORD", "postgres");
                    port = "5432";
                } else if (fw.contains("mysql")) {
                    sb.append("    image: mysql:8\n");
                    env.putIfAbsent("MYSQL_DATABASE", String.valueOf(svc.getOrDefault("name","appdb")));
                    env.putIfAbsent("MYSQL_USER", "mysql");
                    env.putIfAbsent("MYSQL_PASSWORD", "mysql");
                    env.putIfAbsent("MYSQL_ROOT_PASSWORD", "rootpassword");
                    port = "3306";
                } else if (fw.contains("mongo")) {
                    sb.append("    image: mongo:7\n");
                    env.putIfAbsent("MONGO_INITDB_DATABASE", String.valueOf(svc.getOrDefault("name","appdb")));
                    port = "27017";
                } else {
                    sb.append("    image: alpine:latest\n");
                }
            } else {
                String image = "ghcr.io/" + imageBase + "-" + id + ":latest";
                sb.append("    image: ").append(image).append("\n");
                sb.append("    restart: unless-stopped\n");
            }

            List<String> deps = depends.get(name);
            if (deps != null && !deps.isEmpty()) {
                sb.append("    depends_on:\n");
                for (String d : deps) sb.append("      - ").append(d).append("\n");
            }

            if (port != null) {
                sb.append("    ports:\n");
                sb.append("      - '").append(port).append(":").append(port).append("'\n");
            }

            if (!env.isEmpty()) {
                sb.append("    environment:\n");
                for (Map.Entry<String,Object> e : env.entrySet()) {
                    sb.append("      - ").append(e.getKey()).append("=").append(String.valueOf(e.getValue())).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Construit (si besoin) la structure “services/relationships” puis génère le compose de prod.
     */
    public String buildPreviewOrThrow(String repoUrl, String token, String defaultBranch, String imageBase) {
        var structured = stackDetectionService.generateStructuredServices(repoUrl, token, defaultBranch);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> services =
                (List<Map<String, Object>>) structured.getOrDefault("services", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> relationships =
                (List<Map<String, Object>>) structured.getOrDefault("relationships", List.of());
        if (services.isEmpty()) {
            throw new IllegalStateException("No services detected in repository");
        }
        return generateProdComposeYaml(services, relationships, imageBase);
    }
}