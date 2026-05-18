# DAISA — Architecture

DAISA (Distributed AI Study Assistant) is a Java backend that treats an
Obsidian vault as a live knowledge base. The runtime watches markdown files,
parses them deterministically, routes study work to local or cloud AI
through a privacy-aware router, runs the work under supervised agents,
and writes per-course study artifacts back into the vault.

The goals that drove the design — in this order — are **correctness**,
**readability**, **testability**, **clear component boundaries**, and
**operational visibility**. Performance is only addressed where a concrete
bottleneck exists.

---

## Component diagram

```
                   ┌────────────────────────────┐
                   │   Obsidian vault (.md)     │
                   └─────────────┬──────────────┘
                                 │ filesystem events
                                 ▼
                   ┌────────────────────────────┐
                   │        VaultWatcher        │  WatchService, recursive register,
                   │      (debounced, async)    │  750 ms per-path debounce
                   └─────────────┬──────────────┘
                                 │ Path
                                 ▼
                   ┌────────────────────────────┐
                   │      TaskOrchestrator      │  loop guard, course scoping,
                   │                            │  dispatch + write
                   └────┬────────────┬──────────┘
                        │            │
              parse     │            │   resolve
                        ▼            ▼
            ┌────────────────┐  ┌────────────────────┐
            │ MarkdownParser │  │   CourseResolver   │  Study/AU/S<n>/<COURSE>/...
            │ (YAML + body)  │  │                    │  → Course(directory, name)
            └────────────────┘  └────────────────────┘
                        │
                        │ StudyTask
                        ▼
        ┌────────────────────────────┐        ┌─────────────────────┐
        │     AgentSupervisor        │        │      AiRouter       │
        │ heartbeat, restart-unhealthy│       │ privacy + length    │
        └────────────┬───────────────┘        └────────┬────────────┘
                     │                                  │
                     ▼                                  ▼
            ┌────────────────┐                ┌─────────────────────┐
            │  Agent queue   │                │     AiClient        │
            │ (in-process)   │                │  ┌────────────────┐ │
            └────────────────┘                │  │  OllamaClient  │ │ local
                                              │  ├────────────────┤ │
                                              │  │ MockCloudAi…   │ │ cloud
                                              │  └────────────────┘ │ (M9: OpenRouter)
                                              └─────────────────────┘
                                                          │
                                                          ▼
                                              ┌─────────────────────┐
                                              │ StudyArtifactWriter │  idempotent upsert,
                                              │  (per-course paths) │  keyed by source wikilink
                                              └─────────────────────┘
                                                          │
                                                          ▼
                                              <COURSE>/<COURSE> — Tasks.md
                                              <COURSE>/<COURSE> — Summaries.md
```

## Module boundaries

| Package | Responsibility | Knows about |
|---|---|---|
| `daisa.vault` | File watching, deterministic parsing, course resolution, artifact writing | filesystem |
| `daisa.orchestration` | Turn vault events into tasks; route tasks to engines; write outputs | vault + ai + agent |
| `daisa.ai` | Stable `AiClient` interface + local/cloud implementations + config | http only |
| `daisa.agent` | `Agent`, `AgentSupervisor`, `AgentMessage`, health/heartbeat | nothing else |
| `daisa.study` | Plain domain types: `MarkdownNote`, `TodoItem`, `StudyTask`, `StudyTaskType` | nothing |
| `daisa` | `App` entry point — wires everything from `main` | all of the above |

The arrows in the diagram only flow downward and outward. The `study` and
`agent` packages depend on no other package in the project, which keeps
the core domain easy to test in isolation.

---

## Design decisions

### Internal queues before sockets

The agent runtime uses an in-process queue (`AgentSupervisor` dispatches
`AgentMessage` to agent mailboxes) instead of cross-process IPC. Reasons:

1. **Failure isolation is the goal, not multi-host scaling.** The
   distributed-systems concepts worth learning here are supervision,
   heartbeat, restart-unhealthy, queue-based decoupling, and backpressure
   — all of which apply just as well in-process.
2. **No serialization friction.** Messages are plain Java objects; refactoring
   the message envelope costs nothing.
3. **Tests don't need a network.** A real `AgentSupervisor` is built in
   `TaskOrchestratorTest` without ever starting its consumer thread; the
   dispatch side is exercised, the loop is not, and the test stays fast.
4. **Sockets remain a future option.** The `AgentMessage` boundary already
   makes it tractable to swap the in-process queue for a socket-based
   transport when there's a concrete reason — none today.

### Interfaces around AI

`AiClient` is the only thing the orchestrator knows about AI. There are
three implementations behind it: `OllamaClient` (real, local),
`MockLocalAiClient` (deterministic test double), and `MockCloudAiClient`
(placeholder until M9 lands `OpenRouterClient`). Reasons:

1. **Deterministic work must not depend on an LLM.** Markdown parsing, todo
   extraction, routing decisions, and artifact naming are all pure code.
   That work continues to behave the same whether or not Ollama is running.
2. **Graceful failure is observable.** When `OllamaClient` can't reach the
   daemon, it returns a diagnostic `AiResponse` carrying
   `"[Ollama unavailable: <reason>]"` instead of throwing. The artifact
   pipeline still writes a summary section so the operator sees what
   happened in-vault, not just in logs.
3. **The router is the only place privacy/length policy lives.** A new
   engine can be added without touching policy.

### Privacy-aware routing

`AiRouter` makes one decision: which engine handles a given `StudyTask`?
The rules are deliberately small:

- A task marked `privacySensitive` (driven by `#private` tag) **always**
  goes to LOCAL, regardless of length.
- A task above the cloud-reasoning threshold goes to CLOUD.
- Everything else stays LOCAL.

The privacy override is the load-bearing rule — it's why the local engine
exists at all. M9 will add a defensive `IllegalStateException` inside the
cloud client to enforce the invariant at the boundary, not just in the
router.

### Per-course scoping (M6)

The orchestrator only acts on notes that resolve to a course under
`Study/AU/S<n>/<COURSE>/`. Artifacts are written next to the course's notes
as `<COURSE> — Tasks.md` and `<COURSE> — Summaries.md`. Notes outside the
scope root or outside any recognized course are silently ignored — this is
preferable to a fallback artifact at vault root, because surprise files in
unexpected places are worse than a silent no-op the operator can verify in
the log. The loop guard recognizes generated files by filename suffix, not
fixed names.

### Idempotent artifact writes

`StudyArtifactWriter.upsertSection` rewrites a per-source section in place,
keyed by the `- Source: [[wikilink]]` marker. Re-saving a note replaces
its section instead of appending a new one. This is the property that
makes the system safe to re-process the same note an arbitrary number of
times (debounced re-saves, future replay tooling, etc.).

### Dependency-light by default

The current scaffold ships with no Maven/Gradle, no third-party libraries,
and a hand-rolled JSON encoder/decoder scoped to Ollama's known schema.
The trade-off is intentional:

- **Pro**: every line is readable end-to-end without checking a framework's
  behavior, the build is `javac @sources.txt`, and CI takes seconds.
- **Con**: real cloud (M9, `OpenRouterClient`) needs to parse nested JSON
  (`choices[0].message.content`) — that's the trigger to adopt Maven and
  swap in Jackson. PDF ingestion (M8) is the second trigger, since
  `PDFBox` has no stdlib substitute.

Both M8 and M9 are deferred in the current backlog precisely because they
force this trade-off; they will land together with the build system change.

---

## Failure modes & operational notes

| Failure | What happens | Where to look |
|---|---|---|
| Ollama daemon down | `[Ollama unavailable: connection refused]` written to Summaries; pipeline continues | summary artifact + stderr |
| Ollama model not pulled | `[Ollama unavailable: HTTP 404]` | summary artifact |
| Long inference (thinking models) | `[Ollama unavailable: timeout]` after `DAISA_OLLAMA_TIMEOUT_SECONDS` | summary artifact |
| Note outside `Study/AU/` | Silently ignored; no artifact, no AI call | no log line emitted |
| Note in scope, no course owner | Silently ignored | no log line emitted |
| Generated artifact re-edited | Skipped by suffix-based loop guard | no log line emitted |
| Rapid re-saves (Obsidian autosave) | Collapsed by 750 ms per-path debounce in `VaultWatcher` | debounce is unit-tested |

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DAISA_OLLAMA_URL` | `http://localhost:11434` | Ollama base URL |
| `DAISA_OLLAMA_MODEL` | `llama3.2` | Model tag to request |
| `DAISA_OLLAMA_TIMEOUT_SECONDS` | `180` | Per-request HTTP timeout |
| `DAISA_SCOPE_ROOT` | `Study/AU` | Vault-relative path; events outside are ignored |

## Running under Docker

The Dockerfile is a two-stage build: Temurin 17 JDK compiles the sources
with the same `javac @sources.txt` flow the host scripts use, then the
runtime stage copies only the compiled classes into a JRE image
(no compiler, smaller surface). The vault mounts at `/vault`.

Mac-specific gotcha: Ollama on macOS binds to `127.0.0.1` by default, which
the container can't reach via `host.docker.internal`. Start it with
`OLLAMA_HOST=0.0.0.0 ollama serve` (or `launchctl setenv OLLAMA_HOST 0.0.0.0`
before relaunching Ollama.app) so the container can connect. Verified
end-to-end on Apple Silicon: build → run → host filesystem event → per-course
artifact written back, with a real `qwen3.5:9b` summary roundtripping through
`host.docker.internal:11434`.

---

## What's intentionally not here

- No Maven/Gradle (until M8 or M9 forces it)
- No web UI (the orchestrator is the product; vault is the UI)
- No streaming Ollama responses (one-shot generate is enough for short summaries)
- No cross-process agents (queues are in-process; see "Internal queues" above)
- No vector search / embeddings (summarization is the current workload)
