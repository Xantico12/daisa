package daisa.agent;

public interface Agent {
    String name();

    void handle(AgentMessage message);
}

