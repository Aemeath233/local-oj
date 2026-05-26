# Local OJ Project Overview

Last reviewed: 2026-05-26

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
- Queue: Redis lists, default keys `judge:queue`, `judge:processing`, and `judge:dlq`.
- Judge: judge-worker calls go-judge HTTP API.
- Local deployment: Docker Compose.
- Target internal deployment: Linux host, because go-judge relies on Linux isolation features.

## Core Flow

```text
User submits code
  -> backend validates problem/contest rules
  -> backend stores submission as PENDING
  -> backend pushes JudgeJob to Redis after DB commit
  -> judge-worker atomically moves the job to Redis `judge:processing`
  -> judge-worker compiles/runs through go-judge
  -> worker compares outputs and stores per-case results
  -> worker acknowledges the processing item or requeues/dead-letters it
  -> frontend polls and displays final verdict
```

Self-tests call go-judge too, but they do not create official submission records and do not affect rankings. Contest self-tests carry `contestId` and follow the same contest visibility, start-time, and registration rules as contest problem viewing.

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
- Admin ZIP import has a preview-before-create flow and creates a problem from metadata, statement Markdown, and paired test files.
- Normalized problem tags: problem tags are managed via a normalized database schema (`problem_tags` dictionary table and `problem_tag_relation` many-to-many junction table).
- Problem library list page supports multi-tag intersection filtering (SQL-based HAVING clause for maximum efficiency) and displays dynamic colored tag pills using custom colors defined in the tag dictionary.
- LLM problem generation and tag suggestion UI/API/code are intentionally removed from the active workflow. The historical `llm_settings` table is dropped by migration `V16__remove_llm_settings.sql`.

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
- Locks are released when the contest ends or is deleted; the backend also runs a scheduled release check, and problems are restored only when no other active contest lock remains.
- Users may register until the contest end time.
- Before a contest ends, only registered users can view contest problems, submit, and access contest submissions/standings. Admins bypass this for management.
- After a contest ends, problems, contest submissions, and standings are public.
- After a contest ends, `contestId` submissions are rejected, so late submissions do not affect that contest or its standings.
- Standings are based on registered participants only and only count submissions created inside the contest time window.

### Training Set Features (题单功能)

- **Public Training List**: A public space displaying all visible training sets ("题单") with dynamic card displays.
- **Progress Tracking**: Track and display individual student progress as a completion percentage (solved problems / total problems in the training set).
- **Solved Status**: Display the solved status of problems within a training set for the current user (unattempted, attempted, or accepted).
- **Admin Management**: Admins can create, edit metadata (title, description), toggle visibility (public/hidden), and delete training sets.
- **Problem Association & Ordering**: Admins can link existing problems from the system problem library to a training set, unlink associated problems, and reorder problems in sequence (up/down reordering) to customize the learning flow.

### Admin Features

- Admin dashboard with counts, recent submissions, and verdict distribution.
- Problem management: list, visibility, create/edit, delete, file import, ZIP import, example package download.
- Contest management: create/edit ACM/OI contests, toggle contest visibility without touching problem bindings, and view problem/registration/submission counts.
- Training set management: create, edit, delete training sets, associate problems, unlink problems, and sequence problem order.
- Submission management: inspect, rejudge, requeue unfinished.
- System logs management (Premium UI):
  - inspect real-time backend and judge-worker events, filter by level, service, module, submission ID, and keyword;
  - dynamically toggle the global system log collection in memory to optimize CPU and database IO performance;
  - download raw pure text `.log` files (`backend.log` and `judge-worker.log`) for offline multi-line grep and debugging;
  - one-click clear/truncate log files safely on disk with an interactive confirmation modal, keeping the Logback stream active.
- Super-admin user management.
- Super-admin settings:
  - SMTP settings;
  - sandbox settings.

## Dead-Letter Queue (DLQ) & Retry Policy

To prevent repeatedly failing or erroring judge jobs from blocking the judge-worker execution queue, a Dead-Letter Queue, processing queue, and linear retry backoff mechanism is implemented:

- **Processing Queue**: The worker uses Redis `rightPopAndLeftPush` to move jobs from `judge:queue` to `judge:processing` before execution. Successful, retried, or dead-lettered jobs are acknowledged by removing the payload from `judge:processing`.
- **Crash Recovery**: On startup, the worker moves any leftover `judge:processing` jobs back into `judge:queue`. `JudgeService` skips already finished submissions, so recovered duplicate jobs are safe.
- **System Failures**: Unexpected system/infrastructure errors (e.g., database connectivity issues, go-judge service temporarily offline) trigger the retry mechanism. Normal compilation/runtime failures (CE, WA, TLE, etc.) are handled as standard verdicts and do not trigger retries.
- **Retry Logic**: When a system failure is caught, the job attempts count is tracked in Redis via `judge:retry:{submissionId}`.
- **Linear Backoff**: The worker backs off dynamically before retrying: `attempts * 1000ms`.
- **Maximum Attempts**: A job is retried up to 3 times. On the 4th failure, it is dead-lettered:
  - The submission is removed from the active queue and moved to the dead-letter queue: Redis `judge:dlq`.
  - The submission's DB record is permanently marked as `IE` (Internal Error) with status updated in real-time.
  - A high-priority system warning is logged to the system audit.
- **Admin Controls**: Administrators can view active DLQ tasks directly on the glassmorphic Admin Logs dashboard, with controls to either clear/delete dead-lettered jobs or requeue them (which resets the retry state to `PENDING` cleanly in the DB).

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

## Deployment Documentation

Linux deployment and operations notes are maintained in [LINUX_DEPLOYMENT.md](./LINUX_DEPLOYMENT.md).

## Current Gaps

- The ZIP config parser intentionally supports simple key-value YAML-like metadata, not full nested YAML.
- Contest standings support ACM/OI, freeze, and CSV export, but penalty policy is still fixed in code.
- Frontend bundles are still large because Monaco and workers are loaded in the main build path; route-level lazy loading and Vite manual chunks can improve cold-load time.
- go-judge remains a privileged sandbox service in Compose and should stay isolated from normal users and application secrets.
