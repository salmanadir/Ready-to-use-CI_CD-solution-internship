# 🚀 DeployMate

Génère automatiquement des workflows **GitHub Actions** pour vos projets **Spring Boot (Maven/Gradle)** et **Node.js (npm)**, puis pousse le YAML prêt à l’emploi dans votre dépôt GitHub.

---

## 📌 Description

**DeployMate** est un outil conçu pour simplifier la mise en place de pipelines **CI/CD** pour les développeurs, même sans expertise DevOps.

### Fonctionnalités principales :
- 🔍 **Détection automatique** de la stack du projet :
  - Spring Boot avec **Maven**
  - Spring Boot avec **Gradle**
  - **Node.js** avec **npm**
- 📝 **Génération automatique** de fichiers **YAML** CI/CD adaptés à la stack détectée.
- 📤 **Déploiement direct** des fichiers YAML dans le dépôt **GitHub** de l’utilisateur.

👉 L’outil utilise **GitHub Actions** comme moteur d’automatisation pour les workflows CI/CD.

---

## 🧱 Architecture & Technologies

- **Frontend** : React.js (avec Vite)  
- **Backend** : Spring Boot · Hibernate/JPA · JWT · GitHub OAuth  
- **Base de données** : PostgreSQL  
- **Conteneurisation** : Docker & Docker Compose  
- **CI/CD** : GitHub Actions (YAML)

---

## 🚀 Guide de démarrage rapide

### 1) Cloner le projet
```bash
git clone https://github.com/AsmaHmida99/Ready-to-use-CI_CD-solution.git
cd Ready-to-use-CI_CD-solution
```

### 2) Démarrer les conteneurs
```bash
docker-compose up --build
```

Une fois les conteneurs démarrés, accédez à l'application à l'adresse suivante :  
[http://localhost:3000](http://localhost:3000)

### 3) Arrêter les conteneurs
Pour arrêter les conteneurs, utilisez la commande suivante :
```bash
docker compose down
```
