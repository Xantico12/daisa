package daisa.study;

import java.nio.file.Path;
import java.util.Objects;

public final class StudyTask {
    private final StudyTaskType type;
    private final Path sourcePath;
    private final String prompt;
    private final boolean privacySensitive;

    public StudyTask(StudyTaskType type, Path sourcePath, String prompt, boolean privacySensitive) {
        this.type = Objects.requireNonNull(type, "type");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.privacySensitive = privacySensitive;
    }

    public StudyTaskType type() {
        return type;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public String prompt() {
        return prompt;
    }

    public boolean privacySensitive() {
        return privacySensitive;
    }
}

