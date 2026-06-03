# Linux Production Deployment Guide

This document describes the production deployment architecture and steps for **CodeRush OJ**.

## Deployment Architecture

CodeRush OJ is deployed as a multi-container stack using Docker Compose:

```text
                  [ User Browser / MiaoDa Client ]
                                |
                                v
                           [ Frontend ] (Nginx/SPA)
                                |
                                v
                           [ Backend ] (Spring Boot API on Port 8080)
                           /    |    \
                          /     |     \
                         /      v      \
            [ MySQL 8 ]  [ Redis Queue ]  [ judge-worker ]
                                                 |
                                                 v
                                          [ go-judge ] (Port 5050)
```

## Security Hardening (Production Requirements)

### 1. Mandatory Secure Configurations
Before launching in production, you must set these environment variables in your `.env` file. The backend will refuse to boot up in `prod` profile if default values are detected:
- **`JWT_SECRET`**: Set to a cryptographically secure random string of at least 32 bytes.
- **`ADMIN_PASSWORD`**: Set to a strong administrator password instead of the default `admin123`.

### 2. go-judge Sandbox Isolation
- `go-judge` relies on Linux kernel namespace and cgroups isolation features to safely execute untrusted submitted code.
- Ensure the host kernel supports user namespaces and cgroups (cgroup v1 or v2).
- **DO NOT** run `go-judge` with `--privileged` unless absolutely necessary, and never expose the Docker socket to submission runners.
- The judge service execution environment disables network access for all program runs.

## Production Setup Steps

1. **Clone & Prepare Environment**:
   ```bash
   cp .env.example .env
   ```

2. **Configure Environment Variables**:
   Edit `.env` to configure production database credentials, SMTP configuration, and local data bind mounts:
   ```properties
   SPRING_PROFILES_ACTIVE=prod
   JWT_SECRET=your-secure-cryptographic-secret-at-least-32-chars
   ADMIN_PASSWORD=your-secure-admin-password
   DB_URL=jdbc:mysql://mysql:3306/local_oj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
   DB_USERNAME=localoj
   DB_PASSWORD=your-secure-db-password
   REDIS_HOST=redis
   APP_DATA_ROOT=/data
   APP_WORKER_ID=judge-worker-1
   GO_JUDGE_READ_TIMEOUT_MS=300000
   ```

   - **`GO_JUDGE_READ_TIMEOUT_MS`**: The HTTP read timeout for communicating with the judge service. This timeout must be larger than go-judge's maximum possible clockLimit (the compile clockLimit is calculated as `compileTimeoutMs * 2 + 1000ms` which is ~241,000ms when `compileTimeoutMs` is at its maximum of 120,000ms). Setting this value to `300000` (5 minutes) provides an adequate buffer.
   - **`APP_WORKER_ID`**: Keep `APP_WORKER_ID` stable across restarts. For multiple judge-worker instances, assign a unique stable value to each instance so every worker owns and recovers its own Redis processing queue.

3. **Deploy stack**:
   ```bash
   docker compose -f docker-compose.yml up -d --build
   ```

4. **Verify Deployment**:
   Check container logs and ensure migrations are applied successfully:
   ```bash
   docker compose logs -f backend
   ```
