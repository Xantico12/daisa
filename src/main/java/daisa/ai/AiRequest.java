package daisa.ai;

import daisa.study.StudyTaskType;

import java.util.Objects;

public final class AiRequest {
    private final StudyTaskType taskType;
    private final String prompt;
    private final boolean privacySensitive;

    public AiRequest(StudyTaskType taskType, String prompt, boolean privacySensitive) {
        this.taskType = Objects.requireNonNull(taskType, "taskType");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.privacySensitive = privacySensitive;
    }

    public StudyTaskType taskType() {
        return taskType;
    }

    public String prompt() {
        return prompt;
    }

    public boolean privacySensitive() {
        return privacySensitive;
    }
}

