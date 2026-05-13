package daisa.study;

import java.util.Objects;

public final class TodoItem {
    private final String text;
    private final int lineNumber;
    private final boolean complete;

    public TodoItem(String text, int lineNumber, boolean complete) {
        this.text = Objects.requireNonNull(text, "text");
        this.lineNumber = lineNumber;
        this.complete = complete;
    }

    public String text() {
        return text;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public boolean complete() {
        return complete;
    }
}

