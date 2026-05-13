# DAISA

Distributed AI Study Assistant is a Java backend prototype that treats an Obsidian vault as a live knowledge base.

This first version focuses on a small, demoable core:

- Watches markdown files in a vault directory.
- Parses headings, tags, and markdown task checkboxes deterministically.
- Routes study tasks to local or cloud AI engines through a privacy-aware router.
- Runs work through supervised agents with heartbeat state.
- Writes generated study artifacts back to the vault.

## Requirements

- Java 11+
- No Maven or Gradle required for the current scaffold.

## Build and Test

```sh
./scripts/test.sh
```

## Run

```sh
./scripts/run.sh /path/to/obsidian-vault
```

The app starts a vault watcher and prints agent/routing activity to stdout.

