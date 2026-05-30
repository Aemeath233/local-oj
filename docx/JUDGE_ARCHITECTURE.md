# CodeRush OJ - Judge & Sandbox Architecture

This document outlines the design, execution flow, security posture, and verdict mapping rules of the sandboxed execution subsystem.

---

## 🔄 Judge Execution Flow

CodeRush OJ separates the judging service from the core API backend to ensure asynchronous reliability, high throughput, and robust fault-isolation.

```text
  User Submits Code
    ├─► Backend API: Validates submission & saves state as "PENDING"
    ├─► Redis Queue: Backend pushes submission ID to `judge:queue`
    │
  Judge Worker Consumer (asynchronous pool)
    ├─► Redis Processing: Atomically transfers ID to `judge:processing` via Redis RPOPLPUSH
    ├─► Compilation Check:
    │     ├─► If native (C/C++), compiles inside sandbox first.
    │     └─► If bytecode/source (Java/Python), runs direct compiler or syntax check.
    ├─► Sandbox Execution (Multi-testcases):
    │     ├─► For each case, sends source code/binary, stdin, memory, and CPU limits to go-judge HTTP API.
    │     ├─► go-judge executes untrusted program in sandboxed cgroups/namespaces.
    │     └─► Worker retrieves program `stdout`, `stderr`, and runtime stats.
    ├─► Output Comparison:
    │     └─► Worker strips trailing whitespace and compares output to expected `.out` case data.
    ├─► Persistence:
    │     ├─► Saves individual case outcomes in `submission_case_result` table.
    │     └─► Commits final aggregate outcome & stats to `submission` table, status set to "FINISHED".
    └─► Redis Ack: Removes job from `judge:processing` queue.
```

---

## 🔒 Security Posture & Isolation

Submitting user code is inherently running untrusted instructions on a shared system. CodeRush OJ establishes deep defense bounds:

1. **go-judge Sandboxing**:
   - Treatment of sandboxed run as a separated HTTP service. Main API and workers do not run code natively.
   - Network isolation: Actual submitted program execution processes are **blocked from network access** inside the sandbox to prevent data exfiltration.
   - Restrictive limits enforced per run:
     - CPU Time & Wall Clock Time (ns)
     - Memory consumption limits (Bytes)
     - Process count and system threads count
     - Maximum output buffer limit (`stdout` / `stderr` size)
2. **Environment Shielding**:
   - Docker socket is **never mounted** into any judge-related containers to prevent container breakout.
   - Main database credentials and application secrets are excluded from the `go-judge` execution container space.
   - Untrusted programs are completely blocked from mounting or accessing raw problem data directories directly.

---

## 📋 Verdict Mappings & Normalization

The judge worker translates execution states returned by `go-judge` into a concise, standard set of Online Judge verdicts:

| `go-judge` Execution Status | Exit Status | CodeRush OJ Verdict | Verdict Description |
| :--- | :--- | :--- | :--- |
| **Accepted** | `0` | **AC** (Accepted) | Code ran successfully and outputs match the test case perfectly. |
| **Accepted** | Non-zero | **RE** (Runtime Error) | Program terminated with a non-zero exit code or crashed. |
| **Time Limit Exceeded** | Any | **TLE** (Time Limit Exceeded) | Program exceeded the CPU time or wall-clock limits. |
| **Memory Limit Exceeded** | Any | **MLE** (Memory Limit Exceeded) | Program exceeded the sandboxed memory limits. |
| **Output Limit Exceeded** | Any | **OLE** (Output Limit Exceeded) | Program wrote more bytes to stdout than the maximum limit allowed. |
| **Nonzero Exit Status** / **Signalled** | Any | **RE** (Runtime Error) | Program crashed due to segmentation fault, division by zero, etc. |
| **Internal Error** / Other | Any | **IE** (Internal Error) | Sandbox engine failed or configuration parsing failed. |
| *(Compilation Fails)* | Any | **CE** (Compilation Error) | Source code failed to compile. The compiler output is persisted as an error message. |
