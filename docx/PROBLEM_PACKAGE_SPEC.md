# Problem Package Specification (ZIP Format)

CodeRush OJ supports batch problem creation and package imports via standard ZIP files. This document details the expected structure and schema of the import packages.

## Directory Structure

A valid problem ZIP package must conform to the following directory structure:

```text
problem-zip-root/
│
├── config.yml (or config.yaml)      # Metadata and configuration
├── problem.md (or statement.md)     # Markdown problem statement
│
└── cases/ (or root level)           # Test cases
    ├── 1.in
    ├── 1.out
    ├── 2.in
    ├── 2.ans
    └── ...
```

## 1. Metadata Configuration (`config.yml`)

The configuration file defines the problem's metadata, limits, tags, samples, and test case scoring. It must be written in standard YAML.

### Supported Fields

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `title` | String | Yes | - | The title of the problem |
| `slug` | String | No | Auto | Unique slug for problem URL. If omitted, generated from zip name |
| `difficulty` | String | No | `Easy` | Difficulty label (`Easy`, `Medium`, `Hard`) |
| `tags` | String/List | No | - | Problem tags (e.g. `[dp, greedy]` or `"dp, greedy"`) |
| `timeLimitMs` | Integer | No | `1000` | CPU execution time limit per test case (minimum 100ms) |
| `memoryLimitKb` | Integer | No | `262144` | Memory execution limit per test case (minimum 16384Kb) |
| `visible` | Boolean | No | `true` | Whether the problem is immediately visible to public |
| `samples` | String/List | No | - | Base names of test cases to use as public samples (e.g. `[1]`) |
| `scores` | Map/String | No | Equal split | Custom score distribution mapping (e.g. `1: 30, 2: 70`) |

### Example `config.yml`

```yaml
title: "A + B Problem"
slug: "aplusb"
difficulty: "Easy"
tags: [基础, 模拟]
timeLimitMs: 1000
memoryLimitKb: 262144
visible: true
samples: [1]
scores:
  1: 40
  2: 60
```

---

## 2. Problem Statement (`problem.md`)

The problem statement is authored as a single Markdown document. It should include the description, input format, output format, sample input, and sample output sections in standard Markdown structure.

---

## 3. Test Cases

Test cases are paired inputs and expected outputs with the same base names:
- **Inputs**: Ends with `.in` (e.g. `1.in`, `2.in`).
- **Outputs**: Ends with `.out` or `.ans` (e.g. `1.out`, `2.ans`).

File base names are compared to pair them up automatically. Any unpaired file will generate a warning during preview and will be ignored during package import.

Scoring rules:

- If `scores` is omitted, the importer distributes 100 points across all paired cases as evenly as possible. Any remainder is assigned to earlier cases in sorted order.
- If `scores` is partially specified, explicit scores are preserved and the remaining points are evenly distributed across unspecified cases.
- Explicit scores must reference existing case base names, must be non-negative integers, and their total cannot exceed 100.
- Manual admin uploads use the same total-score rule before saving: every stored problem's test cases must sum to exactly 100.
