# DAISA — Distributed AI Study Assistant

[![CI](https://github.com/Xantico12/daisa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Xantico12/daisa/actions/workflows/ci.yml)

A Java backend that treats an Obsidian vault as a live knowledge base.
DAISA watches markdown files, parses them deterministically, routes
study work to local or cloud AI through a privacy-aware router, runs
the work under supervised agents with heartbeat state, and writes
per-course study artifacts back into the vault.

The project is built dependency-light on the Java standard library
(`java.nio`, `java.net.http`, `java.util.concurrent`) with a custom
test runner. Every component is independently testable and replaceable
behind a small interface.

> **Architecture & design decisions:** [`docs/architecture.md`](docs/architecture.md)

---

## What it demonstrates

| Concept | Where it lives |
|---|---|
| File watching with `WatchService` + recursive directory registration | [`VaultWatcher`](src/main/java/daisa/vault/VaultWatcher.java) |
| Per-path debounce (collapses Obsidian's burst of save events) | [`VaultWatcher.shouldHandle`](src/main/java/daisa/vault/VaultWatcher.java) |
| Deterministic markdown parsing (YAML frontmatter + body, no LLM) | [`MarkdownParser`](src/main/java/daisa/vault/MarkdownParser.java) |
| Path-based course resolution + scope filter | [`CourseResolver`](src/main/java/daisa/vault/CourseResolver.java) |
| Idempotent artifact writes keyed by source wikilink | [`StudyArtifactWriter`](src/main/java/daisa/vault/StudyArtifactWriter.java) |
| Supervised agent runtime with heartbeats and restart-unhealthy | [`AgentSupervisor`](src/main/java/daisa/agent/AgentSupervisor.java) |
| Privacy-aware engine routing (private tasks force local) | [`AiRouter`](src/main/java/daisa/orchestration/AiRouter.java) |
| Stable `AiClient` interface with real + mock implementations | [`AiClient`](src/main/java/daisa/ai/AiClient.java) |
| Graceful AI degradation (failures surface as artifact text, never crash) | [`OllamaClient`](src/main/java/daisa/ai/OllamaClient.java) |
| Hand-rolled JSON encode/decode scoped to Ollama's schema | [`OllamaClient`](src/main/java/daisa/ai/OllamaClient.java) |
| Loop guard against feedback loops on generated artifacts | [`CourseResolver.isGeneratedArtifact`](src/main/java/daisa/vault/CourseResolver.java) |

---

## Quick start

### Requirements

- **Java 17+** (works on 11+ for the core, CI uses Temurin 17)
- **[Ollama](https://ollama.com)** for local AI (optional — pipeline degrades gracefully without it)
- No Maven, no Gradle, no third-party JARs

### Build and test

```sh
./scripts/test.sh
```

Builds `src/main/java` + `src/test/java` with one `javac` invocation, then
runs the custom `daisa.TestRunner`. All tests use real collaborators and
hand-built test doubles — no JUnit, no mock framework.

### Run

```sh
ollama serve &                     # start the Ollama daemon if not running
ollama pull qwen3.5:9b             # any chat model works; this one is the recommended default
export DAISA_OLLAMA_MODEL=qwen3.5:9b
./scripts/run.sh /path/to/obsidian-vault
```

The orchestrator only acts on notes under `Study/AU/S<n>/<COURSE>/` (the
default scope). Per-course artifacts are written as
`<COURSE> — Tasks.md` and `<COURSE> — Summaries.md` next to the course's
notes. To override the scope, set `DAISA_SCOPE_ROOT=Path/Relative/To/Vault`.

### Docker

```sh
docker build -t daisa .
docker run --rm -v "$HOME/Obsidian:/vault" \
  -e DAISA_OLLAMA_URL=http://host.docker.internal:11434 \
  -e DAISA_OLLAMA_MODEL=qwen3.5:9b \
  daisa
```

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DAISA_OLLAMA_URL` | `http://localhost:11434` | Ollama base URL |
| `DAISA_OLLAMA_MODEL` | `llama3.2` | Model tag to request |
| `DAISA_OLLAMA_TIMEOUT_SECONDS` | `180` | Per-request HTTP timeout |
| `DAISA_SCOPE_ROOT` | `Study/AU` | Vault-relative scope; events outside are ignored |

---

## Trigger conventions

The orchestrator emits two task types per note based on tags. Tags can be
inline `#exam` or YAML frontmatter `tags: [exam]`:

| Tag(s) | Task | Notes |
|---|---|---|
| Any unchecked `- [ ] todo` line | `EXTRACT_TODOS` | Deterministic — no AI call |
| `#exam` or `#summarize` | `SUMMARIZE_NOTE` | Routed through `AiRouter` to LOCAL or CLOUD |
| `#private` (with one of the above) | Same task type, **forced LOCAL** | Privacy override beats length policy |

---

## Repository layout

```
src/main/java/daisa/
  App.java                  ← composition root
  agent/                    ← AgentSupervisor, Agent, AgentMessage, AgentHealth
  ai/                       ← AiClient interface, OllamaClient, mocks, OllamaConfig
  orchestration/            ← AiRouter, TaskOrchestrator, RoutingDecision
  study/                    ← Plain domain types (MarkdownNote, StudyTask, …)
  vault/                    ← VaultWatcher, MarkdownParser, CourseResolver, StudyArtifactWriter

src/test/java/daisa/        ← Custom TestRunner + test doubles, one *Test.java per unit
scripts/                    ← test.sh, run.sh (javac-driven, no build system)
docs/architecture.md        ← Component diagram, design decisions, failure modes
Dockerfile                  ← Multi-stage JDK→JRE build
.github/workflows/ci.yml    ← Test on every push to main/dev
```

---

## Roadmap

| Milestone | Status |
|---|---|
| M1 — Deterministic vault layer (parser, watcher) | done |
| M2 — Agent runtime + AI router | done |
| M3 — Idempotent artifact writes | done |
| M4 — Real `OllamaClient` (local AI) | done |
| M5 — Orchestrator + watcher tests | done |
| M6 — Per-course scoping | done |
| M7 — Architecture doc + Dockerfile + CI | done |
| M8 — Text-based PDF ingestion | deferred (requires Maven + PDFBox) |
| M9 — `OpenRouterClient` + secrets | deferred (requires Maven + Jackson) |

See [the vault project plan](../the-vault/Projects/DAISA/Project%20Plan.md) for the
current source-of-truth status and session log.
