package daisa.agent;

import daisa.study.StudyTask;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AgentMessage {
    private final String id;
    private final StudyTask task;
    private final Instant createdAt;

    public AgentMessage(StudyTask task) {
        this(UUID.randomUUID().toString(), task, Instant.now());
    }

    public AgentMessage(String id, StudyTask task, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.task = Objects.requireNonNull(task, "task");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String id() {
        return id;
    }

    public StudyTask task() {
        return task;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

