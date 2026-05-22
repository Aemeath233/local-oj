# Local OJ Project Overview

Last reviewed: 2026-05-22

This is the canonical engineering overview for this repository. Keep it current when product rules, service boundaries, database migrations, deployment assumptions, or major workflows change.

## Product Direction

Local OJ is a lightweight internal Online Judge system for classroom, training, or small team use. It is not designed for public Internet exposure. The intended experience is practical and compact: users solve problems, admins manage problems and contests, submissions are judged by go-judge, and core records remain easy to inspect.

## Repository Layout

```text
.
├── common/          Shared Java models, enums, MyBatis-Plus mappers, queue DTOs
├── backend/         Spring Boot API, auth, admin tools, Flyway migrations
├── judge-worker/    Redis queue consumer and go-judge execution pipeline
├── frontend/        Vue 3 + Vite + TypeScript + Element Plus + Monaco
├── docker/          Docker assets, including go-judge image context
├── docx/            Maintained project documentation
├── data/            Optional local data mount area
└── docker-compose.yml
```

## Runtime Stack

- Frontend: Vue 3, Vite, TypeScript, Element Plus, Pinia, Vue Router, Monaco Editor.
- Backend: Spring Boot 3.5.x, Java 21, MyBatis-Plus, Spring Security/JWT, Flyway.
- Database: MySQL 8.
- Queue: Redis list, default key `judge:queue`.
- Judge: judge-worker calls go-judge HTTP API.
- Local deployment: Docker Compose.
- Target internal deployment: Linux host, because go-judge relies on Linux isolation features.

## Core Flow

```text
User submits code
  -> backend validates problem/contest rules
  -> backend stores submission as PENDING
  -> backend pushes JudgeJob to Redis after DB commit
  -> judge-worker consumes the job
  -> judge-worker compiles/runs through go-judge
  -> worker compares outputs and stores per-case results
  -> frontend polls and displays final verdict
```

Self-tests call go-judge too, but they do not create official submission records and do not affect rankings.

## Current Feature Surface

### User Features

- Email-based registration with SMTP verification.
- JWT login and session expiry handling.
- Profile page: avatar, nickname, student number, major, password change by SMTP code.
- Avatar upload is compressed to WebP on the client when possible; backend still enforces a 2 MB limit.
- User preferences: default language, code font/size, reader font/size, per-language starter templates.

### Problem Features

- Public home page, problem list, fuzzy search, status filter.
- Problem detail page with Markdown statement, samples, Monaco editor, submit, self-test, and local submission history.
- Problem statements are a single Markdown document; input/output sections are not separate database fields in the UI.
- Test cases are file-backed and stored under `/data/problems/{problemId}/cases/`.
- Admin problem creation/editing supports paired `.in` with `.out`/`.ans` uploads.
- Admin ZIP import creates a problem from metadata, statement Markdown, and paired test files.
- Admin problem tags are managed as a preset dictionary; problems still store selected tag names as a comma-separated string.
- LLM problem generation and tag suggestion UI/API are intentionally removed from the active workflow.

### Submission And Ranking

- Verdicts: `AC`, `WA`, `TLE`, `MLE`, `OLE`, `RE`, `CE`, `IE`.
- Public leaderboard sorts by accepted problem count descending, total submissions ascending, then last accepted time ascending.
- Submission detail visibility:
  - users can always see their own submissions;
  - admins can see all;
  - public problem submissions by others require the current user to have AC on that problem;
  - contest submissions become public after the contest ends.

### Contest Features

- Admins can create ACM/OI contests and choose problems.
- Selected contest problems are hidden from the public problem list while the contest lock is active.
- Locks are released when the contest ends or is deleted; problems are restored only when no other active contest lock remains.
- Users may register until the contest end time.
- Before a contest ends, only registered users can view contest problems, submit, and access contest submissions/standings. Admins bypass this for management.
- After a contest ends, problems, contest submissions, and standings are public.
- After a contest ends, `contestId` submissions are rejected, so late submissions do not affect that contest or its standings.
- Standings are based on registered participants only.

### Admin Features

- Admin dashboard with counts, recent submissions, and verdict distribution.
- Problem management: list, visibility, create/edit, delete, file import, ZIP import, example package download.
- Contest management.
- Submission management: inspect, rejudge, requeue unfinished.
- Super-admin user management.
- Super-admin settings:
  - SMTP settings;
  - sandbox settings.

## Sandbox Settings

Sandbox settings live in `sandbox_settings` and are edited from the super-admin settings page.

Fields:

- `workerThreads`: how many worker jobs may be processed concurrently.
- `maxConcurrentRuns`: upper bound for simultaneous judge jobs. Effective submission concurrency is `min(workerThreads, maxConcurrentRuns)`.
- `compileTimeoutMs`: compile CPU timeout sent to go-judge; wall timeout is derived from it.
- `defaultOutputLimitKb`: stdout/stderr collector limit sent to go-judge.
- `maxProcessCount`: process count limit sent to compile and run commands.

The judge-worker reads these settings dynamically when polling and judging. Backend self-test uses the same compile/output/process limits, but self-test does not share the worker queue concurrency control because it is executed through the backend request path.

## ZIP Problem Package

The detailed import format is documented in [PROBLEM_PACKAGE_SPEC.md](./PROBLEM_PACKAGE_SPEC.md).

Quick shape:

```text
problem-package.zip
├── config.yml
├── statement.md
└── cases/
    ├── 1.in
    ├── 1.out
    ├── 2.in
    └── 2.out
```

The admin problem list exposes a downloadable example ZIP through `/api/admin/problems/example-package`.

## Local Commands

From repository root:

```powershell
mvn test
docker compose up -d --build
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
npm run build
```

Default local account:

```text
admin / admin123
```

Change `ADMIN_PASSWORD` and `JWT_SECRET` before any real internal use.

## Current Gaps

- Sandbox settings are now wired into the worker, but there is still no dead-letter queue or retry audit for failed judge jobs.
- Contest standings have a practical ACM/OI implementation, but no freeze, penalty customization, or export.
- The ZIP config parser intentionally supports simple key-value YAML-like metadata, not full nested YAML.
- Problem tags remain comma-separated names instead of a normalized many-to-many relation.
- Test coverage exists for package import, self-test, file pairing, and output comparison; contest registration/visibility lock behavior needs focused tests next.
- go-judge remains a privileged sandbox service in Compose and should stay isolated from normal users and application secrets.
