package daisa.agent;

import java.util.Objects;

public final class LoggingAgent implements Agent {
    private final String name;

    public LoggingAgent(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void handle(AgentMessage message) {
        System.out.println("Agent " + name + " handled " + message.task().type() + " for " + message.task().sourcePath());
    }
}

