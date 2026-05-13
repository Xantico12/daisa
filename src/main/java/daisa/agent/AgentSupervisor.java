package daisa.agent;

import daisa.study.StudyTaskType;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AgentSupervisor {
    private final Map<String, AgentRuntime> agentsByName;
    private final Map<StudyTaskType, String> routingTable;
    private final Duration heartbeatTimeout;

    public AgentSupervisor(Map<String, Agent> agentsByName, Map<StudyTaskType, String> routingTable, Duration heartbeatTimeout) {
        Objects.requireNonNull(agentsByName, "agentsByName");
        this.agentsByName = new LinkedHashMap<>();
        for (Map.Entry<String, Agent> entry : agentsByName.entrySet()) {
            this.agentsByName.put(entry.getKey(), new AgentRuntime(entry.getValue()));
        }
        this.routingTable = new EnumMap<>(Objects.requireNonNull(routingTable, "routingTable"));
        this.heartbeatTimeout = Objects.requireNonNull(heartbeatTimeout, "heartbeatTimeout");
    }

    public static AgentSupervisor withDefaultAgents() {
        Map<String, Agent> agents = new LinkedHashMap<>();
        agents.put("markdown", new LoggingAgent("markdown"));
        agents.put("study", new LoggingAgent("study"));

        Map<StudyTaskType, String> routes = new EnumMap<>(StudyTaskType.class);
        routes.put(StudyTaskType.EXTRACT_TODOS, "markdown");
        routes.put(StudyTaskType.SUMMARIZE_NOTE, "study");
        routes.put(StudyTaskType.EXPLAIN_TOPIC, "study");

        return new AgentSupervisor(agents, routes, Duration.ofSeconds(5));
    }

    public void start() {
        for (AgentRuntime runtime : agentsByName.values()) {
            runtime.start();
        }
    }

    public void stop() {
        for (AgentRuntime runtime : agentsByName.values()) {
            runtime.stop();
        }
    }

    public void dispatch(AgentMessage message) {
        String agentName = routingTable.get(message.task().type());
        if (agentName == null) {
            throw new IllegalArgumentException("No agent route for task type: " + message.task().type());
        }
        AgentRuntime runtime = agentsByName.get(agentName);
        if (runtime == null) {
            throw new IllegalStateException("Configured route points to missing agent: " + agentName);
        }
        runtime.submit(message);
    }

    public void restartUnhealthyAgents() {
        Instant now = Instant.now();
        for (AgentRuntime runtime : agentsByName.values()) {
            boolean staleHeartbeat = runtime.health() == AgentHealth.RUNNING
                    && Duration.between(runtime.lastHeartbeat(), now).compareTo(heartbeatTimeout) > 0;
            if (runtime.health() == AgentHealth.FAILED || staleHeartbeat) {
                System.out.println("Restarting agent: " + runtime.name());
                runtime.restart();
            }
        }
    }

    public Map<String, AgentHealth> healthSnapshot() {
        Map<String, AgentHealth> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, AgentRuntime> entry : agentsByName.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().health());
        }
        return Collections.unmodifiableMap(snapshot);
    }
}

