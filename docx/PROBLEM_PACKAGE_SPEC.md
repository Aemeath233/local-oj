# Problem Package Specification (ZIP Format)

CodeRush OJ supports batch problem creation and package imports via standard ZIP files. This document details the expected structure and schema of the import packages.

## Directory Structure

A valid problem ZIP package must conform to the following directory structure:

```text
problem-zip-root/
│
├── config.yml (or config.yaml)      # Metadata and configuration
├── problem.md (or statement.md)     # Markdown problem statement
├── README.md                        # Optional human-facing package notes
├── AGENTS.md                        # Optional LLM/agent authoring instructions
│
└── cases/ (or root level)           # Test cases
    ├── 1.in
    ├── 1.out
    ├── 2.in
    ├── 2.ans
    └── ...
```

`README.md`, `AGENTS.md`, and other helper documents are ignored by the importer. They are safe to include as authoring notes, especially when a package template is handed to an LLM or another agent to generate new problems.

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
title: "Integer List Sum Template"
slug: "list-sum-template"
difficulty: "Easy"
tags: [基础, 输入输出, 测试设计]
timeLimitMs: 1000
memoryLimitKb: 262144
visible: true
samples: [sample-1, sample-2]
scores:
  sample-1: 5
  sample-2: 5
  min-n: 9
  single-negative: 9
  all-zero: 9
  mixed-sign: 9
  max-value-pair: 9
  max-n-pattern: 9
  alternating: 9
  random-small-01: 9
  random-small-02: 9
  random-medium-01: 9
```

---

## 2. Problem Statement (`problem.md`)

The problem statement is authored as a single Markdown document. It should include the description, input format, output format, constraints, and any necessary notes.

Do not duplicate the problem title as an H1 heading at the start of the statement. The frontend renders the title from `config.yml`.

Sample input/output sections do not need to be written manually. The frontend can render public samples from the test cases named in the `samples` field.

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

## 4. Test Design Guidance

Problem packages should include enough cases to validate correctness, not just a few hand-written examples.

Recommended coverage:

- Public samples: 1-2 small, readable cases that explain the statement.
- Boundary cases: minimum size, maximum size, minimum values, maximum values, and branch-boundary values.
- Degenerate cases: single element, all equal values, all zero values, empty-equivalent legal structures, or one-sided structures where the problem allows them.
- Constructed cases: sorted, reversed, alternating, duplicated, multiple optimal answers, zero answer, very large answer, or other shapes likely to reveal common bugs.
- Adversarial cases: inputs that break naive greedy choices, incomplete dynamic programming initialization, off-by-one indexing, overflow-prone arithmetic, or other common wrong solutions.
- Random cases: several deterministic random tests across small, medium, and large sizes. Use a fixed seed or document the generation strategy in `README.md` so the data can be regenerated and audited.

All expected outputs should be generated or verified by a trusted reference solution before packaging.
