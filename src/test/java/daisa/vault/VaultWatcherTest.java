package daisa.vault;

import daisa.TestSupport;

import java.nio.file.Files;
import java.nio.file.Path;

public final class VaultWatcherTest {
    private VaultWatcherTest() {
    }

    public static void run() throws Exception {
        runDebounceCollapsesRapidEvents();
        runDebounceIsPerPath();
    }

    // The OS may emit several events for one logical save (e.g. Obsidian writes
    // a .tmp then renames). The watcher must collapse those into one handler
    // call within DEBOUNCE_WINDOW (750ms), so the orchestrator doesn't run twice.
    private static void runDebounceCollapsesRapidEvents() throws Exception {
        Path vault = Files.createTempDirectory("daisa-watcher-test");
        VaultWatcher watcher = new VaultWatcher(vault, p -> { });
        try {
            Path note = vault.resolve("note.md");
            TestSupport.assertTrue(watcher.shouldHandle(note),
                    "First event for a path should always be handled");
            TestSupport.assertTrue(!watcher.shouldHandle(note),
                    "Second event within debounce window must be suppressed");
            TestSupport.assertTrue(!watcher.shouldHandle(note),
                    "Third rapid event within window must also be suppressed");
        } finally {
            watcher.close();
        }
    }

    // Debounce state must be keyed by path: saving note A right after note B
    // should still fire for A. Otherwise concurrent edits to different files
    // would silently drop one of them.
    private static void runDebounceIsPerPath() throws Exception {
        Path vault = Files.createTempDirectory("daisa-watcher-test");
        VaultWatcher watcher = new VaultWatcher(vault, p -> { });
        try {
            Path a = vault.resolve("a.md");
            Path b = vault.resolve("b.md");
            TestSupport.assertTrue(watcher.shouldHandle(a), "First event for a.md");
            TestSupport.assertTrue(watcher.shouldHandle(b), "First event for b.md should not be debounced by a.md");
            TestSupport.assertTrue(!watcher.shouldHandle(a), "Second a.md within window suppressed");
            TestSupport.assertTrue(!watcher.shouldHandle(b), "Second b.md within window suppressed");
        } finally {
            watcher.close();
        }
    }
}
