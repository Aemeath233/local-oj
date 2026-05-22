# Local OJ

A lightweight internal Online Judge system built around Spring Boot, Vue, Redis, MySQL, and go-judge.

## Stack

- Frontend: Vue 3, Vite, TypeScript, Element Plus, Monaco Editor
- Backend: Spring Boot 3, Java 21, MyBatis-Plus, Spring Security
- Database: MySQL 8
- Queue: Redis
- Judge: judge-worker + go-judge
- Deployment: Docker Compose

## Local Notes

This repository is designed for Windows development and Linux deployment. Keep runtime paths Linux/container-style and place environment-specific settings in `.env`.

Copy the example environment file before using Docker Compose:

```powershell
Copy-Item .env.example .env
```

The Compose MySQL service maps to host port `3307` by default so it does not collide with an existing local MySQL on `3306`.
Uploaded problem test files are kept in the Compose `oj-data` volume under `/data/problems/{problemId}/cases`.

Then start the stack:

```powershell
docker compose up -d --build
```

Default accounts are seeded by the backend on first startup:

```text
admin / admin123
```

Change `ADMIN_PASSWORD` and `JWT_SECRET` before using the system for real internal training.

## Development

For a quick orientation before changing code, read `docx/PROJECT_OVERVIEW.md`. ZIP problem-package details live in `docx/PROBLEM_PACKAGE_SPEC.md`. Keep the `docx/` documents updated when architecture, feature surface, import format, or run/deployment assumptions change.

Backend and worker require Java 21:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean test
```

If Maven Central is unstable from the current network, use the checked-in mirror settings:

```powershell
mvn -s .mvn/settings-cn.xml clean test
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```
