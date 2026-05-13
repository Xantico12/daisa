package daisa.agent;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AgentRuntime implements Runnable {
    private final Agent agent;
    private final BlockingQueue<AgentMessage> mailbox = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile AgentHealth health = AgentHealth.STOPPED;
    private volatile Instant lastHeartbeat = Instant.EPOCH;
    private Thread thread;

    AgentRuntime(Agent agent) {
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    String name() {
        return agent.name();
    }

    AgentHealth health() {
        return health;
    }

    Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    void submit(AgentMessage message) {
        mailbox.offer(Objects.requireNonNull(message, "message"));
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            health = AgentHealth.STARTING;
            thread = new Thread(this, "daisa-agent-" + agent.name());
            thread.start();
        }
    }

    void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
        health = AgentHealth.STOPPED;
    }

    void restart() {
        stop();
        start();
    }

    @Override
    public void run() {
        health = AgentHealth.RUNNING;
        while (running.get()) {
            lastHeartbeat = Instant.now();
            try {
                AgentMessage message = mailbox.poll(500, TimeUnit.MILLISECONDS);
                if (message != null) {
                    agent.handle(message);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (RuntimeException failure) {
                health = AgentHealth.FAILED;
                System.err.println("Agent failed: " + agent.name() + " - " + failure.getMessage());
            }
        }
        if (health != AgentHealth.FAILED) {
            health = AgentHealth.STOPPED;
        }
    }
}

