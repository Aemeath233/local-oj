# CodeRush OJ - Development & Deployment Environment

This document describes the environment specifications, environment variables, configuration parameters, and run commands required for developing and deploying CodeRush OJ.

---

## 📌 Pinned System Versions

To maintain absolute architectural stability, the following software and library versions are pinned across all environments:

| Dependency | Pinned Version | Scope / Notes |
| :--- | :--- | :--- |
| **Java Platform** | OpenJDK 21 | Backend API, Judge Worker, Compile Environment |
| **Spring Boot** | 3.5.x line | Framework backend dependency |
| **MyBatis-Plus** | 3.5.x line | Database ORM layer |
| **Monaco Editor** | 0.55.x | Frontend code editor |
| **MySQL Database** | MySQL 8.4 LTS | Main relational database |
| **Redis Cache/Queue** | Redis 7.4 (Alpine) | Job dispatch queue |
| **C++ Standard** | **C++20** (`-std=c++20`) | Sandbox compilation standard (using GCC 14.2) |
| **Python Standard** | **Python 3.12** | Sandbox execution interpreter |
| **Sandbox Environment** | `go-judge` v1.12.0 | Execution sandboxing |

---

## ⚙️ Environment Configuration (`.env`)

CodeRush OJ reads environment-specific values from the shared `.env` file in the project root. Before startup, configure these values appropriately.

```ini
# Database configuration
MYSQL_PORT=3307                     # Mapped host port to prevent collisions
MYSQL_DATABASE=local_oj
MYSQL_USER=localoj
MYSQL_PASSWORD=localoj_pass
MYSQL_ROOT_PASSWORD=localoj_root

# Redis configuration
REDIS_PORT=6379

# Sandbox engine configuration
GO_JUDGE_PORT=5050                  # Sandbox HTTP API port

# Backend API server configuration
BACKEND_PORT=8080                   # Backend API host port
JWT_SECRET=change-this-internal-secret-at-least-32-bytes   # Cryptographic secret
ADMIN_USERNAME=admin                # Default administrator seed username
ADMIN_PASSWORD=admin123             # Default administrator seed password

# Frontend deployment configuration
FRONTEND_PORT=5173                  # Frontend port exposed on the host
```

---

## 🏃 Run & Development Commands

### 🐳 1. Docker Compose Run (Whole Stack)
To run the entire system in one command, copy `.env.example` to `.env` and run Compose:

```powershell
# Copy environment configuration
Copy-Item .env.example .env

# Build and start all services in detached mode
docker compose up -d --build
```

### ☕ 2. Backend Development (Java 21)
Ensure `JAVA_HOME` points to JDK 21. Run standard Maven commands for compilation and tests:

```powershell
# Configure JDK 21 environment
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Compile and package Backend & Judge Worker modules
mvn clean package -Dmaven.test.skip=true
```

### ⚡ 3. Frontend Development (Vue 3 / Vite)
Start the local development server with hot-reloading:

```powershell
cd frontend
npm install
npm run dev
```

---

## 💾 Database Seeding

On the initial backend server boot, the system automatically runs schema migrations and seeds default configurations:
- Creates the administrative account using credentials specified by `ADMIN_USERNAME` and `ADMIN_PASSWORD` in the `.env` file (defaults to `admin / admin123`).
- Seeds standard database tables and initializes preset tags dictionaries.
