# Project Notes for Agents

## Product Direction

- This project is intended to be a lightweight internal Online Judge system.
- It is not planned for public Internet exposure.
- Keep the feature set focused and practical. Avoid copying full platforms like Hydro when a small internal workflow is enough.
- Core workflows should cover users, problems, submissions, judging, result history, and basic ranking/admin tools.
- Self-test runs should execute through go-judge but must not create official submission records or affect rankings/history.
- User profile should cover avatar, nickname, student number, major, and SMTP-verified password changes.
- Public problem lists should show the current user's status: unattempted, attempted, or accepted.
- The lightweight leaderboard sorts by accepted problem count descending, then total submissions ascending, then last accepted time ascending.
- Admins can manage a preset tag dictionary. Problems still store selected tag names as a simple comma-separated string.
- LLM tag suggestion is configured from the admin settings page with an OpenAI-compatible Base URL, model, and optional API key.
- Avatar uploads should be converted/compressed to WebP on the client before upload where possible, while the backend enforces a 2MB maximum.
- User registration should be email-based with SMTP verification codes.
- SMTP connection settings are configured from the administrator backend, not hard-coded in environment files.
- Problem statements are authored as a single Markdown document. Do not force separate input/output description fields in the UI.
- Test cases should be imported by uploading paired files with the same basename, such as `1.in` and `1.out` or `1.ans`. Avoid manual input/output text entry in the admin UI.
- Prefer the standard problem package ZIP format for agent-generated or batch-created problems: a Markdown statement file, a YAML metadata config file, and paired same-basename `.in` plus `.out`/`.ans` test files. Keep `PROBLEM_PACKAGE_SPEC.md` current when this format changes.

## Sandbox / Judge Decision

- Use go-judge as the initial sandboxed execution engine.
- Prefer integrating go-judge through its HTTP API instead of implementing a sandbox from scratch.
- go-judge should be treated as a separate judge service, not as business logic inside the main backend.
- The backend or judge worker should submit code, stdin, time limits, memory limits, and file inputs to go-judge, then persist normalized results.
- Keep the integration replaceable so isolate/nsjail could be adopted later if needed.

Useful references:

- go-judge repo: https://github.com/criyle/go-judge
- go-judge docs: https://docs.goj.ac/

## Security Posture

- Even on an internal network, submitted code is untrusted code.
- Run the judge service isolated from the main application where practical.
- Do not expose go-judge directly to normal users.
- Do not mount the Docker socket into judge-related containers.
- Do not let submitted programs access raw problem data directories directly.
- Disable network access for actual submitted-program execution wherever the chosen judge configuration supports it.
- Enforce CPU time, wall time, memory, process count, and output-size limits for every run.
- Keep database credentials and application secrets away from judge execution environments.

## Development / Deployment Assumptions

- Development machine may be Windows.
- Use Docker Desktop with WSL2 for local development when running Linux-dependent services.
- Production/internal deployment should target Linux, because go-judge relies on Linux isolation features such as namespaces and cgroups.
- Design for easy migration from Windows development to Linux deployment.

Avoid hard-coding Windows-specific paths such as:

```text
D:\some\local\path
```

Prefer container/Linux paths such as:

```text
/app
/data/problems
/data/submissions
```

## Configuration

- Keep environment-specific settings in `.env` files.
- Provide `.env.example` when adding configurable services.
- In Docker Compose, use service names for internal networking, for example:
  - `redis:6379`
  - `mysql:3306` or `postgres:5432`
  - `go-judge:5050`
- Do not assume services are available on `localhost` from inside containers.

## Suggested Architecture

Use this shape unless the project later chooses otherwise:

```text
frontend/
backend/
judge-worker/
data/
docker-compose.yml
.env.example
```

Recommended runtime components:

- Frontend: Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Monaco Editor.
- Backend API: Spring Boot 3 + Java 21 + MyBatis-Plus + Spring Security/JWT.
- Database: MySQL 8.
- Queue: Redis.
- Judge execution: go-judge.
- Deployment: Docker Compose first.

Current pinned versions to preserve unless there is a reason to upgrade:

- Java: 21
- Spring Boot: 3.5.x line
- MyBatis-Plus: 3.5.x line
- go-judge Docker image: `criyle/go-judge:v1.12.0`

## Judge Flow

Preferred flow:

```text
User submits code
  -> Backend stores submission as pending
  -> Backend pushes job to Redis queue
  -> Judge worker consumes job
  -> Judge worker calls go-judge
  -> Judge worker compares outputs and aggregates test results
  -> Backend/database stores final verdict
```

Normalize verdicts to a small set:

- AC: Accepted
- WA: Wrong Answer
- TLE: Time Limit Exceeded
- MLE: Memory Limit Exceeded
- OLE: Output Limit Exceeded
- RE: Runtime Error
- CE: Compilation Error
- IE: Internal Error

## Implementation Preferences

- Keep the first version small and shippable.
- Prefer explicit schemas and clear service boundaries over broad abstractions.
- Store problem test data outside source code, ideally in a mounted data volume.
- Store uploaded test data under `/data/problems/{problemId}/cases/`; database rows should keep case names, file names, sizes, score, and sample flags instead of full `.in/.out` contents.
- Admin test import should pair uploaded files by the same base name, using `.in` as input and `.out` or `.ans` as expected output.
- Admin problem package import should read `config.yml`/`config.yaml`, `problem.md`/`statement.md`, and paired test files from a ZIP, then create the problem and materialize cases through the same file-storage path as normal admin uploads.
- Keep judge worker code deterministic and easy to inspect.
- Add tests around verdict normalization and output comparison logic.
- Treat sandbox configuration as a critical part of the system, not an incidental deployment detail.
