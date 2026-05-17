package daisa.vault;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public final class VaultWatcher implements Closeable {
    private static final Duration DEBOUNCE_WINDOW = Duration.ofMillis(750);

    private final Path vaultRoot;
    private final Consumer<Path> markdownChangeHandler;
    private final WatchService watchService;
    private final Map<Path, Instant> recentlyHandled = new HashMap<>();
    private final Map<WatchKey, Path> watchDirectories = new HashMap<>();

    public VaultWatcher(Path vaultRoot, Consumer<Path> markdownChangeHandler) throws IOException {
        this.vaultRoot = Objects.requireNonNull(vaultRoot, "vaultRoot");
        this.markdownChangeHandler = Objects.requireNonNull(markdownChangeHandler, "markdownChangeHandler");
        this.watchService = FileSystems.getDefault().newWatchService();
        if (!Files.isDirectory(vaultRoot)) {
            throw new IllegalArgumentException("Vault path must be a directory: " + vaultRoot);
        }
        registerExistingDirectories(vaultRoot);
    }

    public void watchForever() throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key = watchService.take();
            Path watchedDirectory = watchDirectories.get(key);
            if (watchedDirectory == null) {
                key.reset();
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = watchedDirectory.resolve((Path) event.context()).toAbsolutePath().normalize();
                if (event.kind() == ENTRY_CREATE && Files.isDirectory(changed)) {
                    registerExistingDirectories(changed);
                    continue;
                }
                if (changed.getFileName().toString().endsWith(".md") && shouldHandle(changed)) {
                    markdownChangeHandler.accept(changed);
                }
            }
            if (!key.reset()) {
                watchDirectories.remove(key);
                break;
            }
        }
    }

    private void registerExistingDirectories(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isDirectory).forEach(this::registerDirectory);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to register vault directories: " + directory, failure);
        }
    }

    private void registerDirectory(Path directory) {
        try {
            WatchKey key = directory.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            watchDirectories.put(key, directory);
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to watch directory: " + directory, failure);
        }
    }

    // Package-private so VaultWatcherTest can exercise the debounce window
    // without spinning up a real WatchService (which polls every 10s on macOS).
    boolean shouldHandle(Path path) {
        Instant now = Instant.now();
        Instant previous = recentlyHandled.get(path);
        if (previous != null && Duration.between(previous, now).compareTo(DEBOUNCE_WINDOW) < 0) {
            return false;
        }
        recentlyHandled.put(path, now);
        return true;
    }

    @Override
    public void close() throws IOException {
        watchService.close();
    }
}
