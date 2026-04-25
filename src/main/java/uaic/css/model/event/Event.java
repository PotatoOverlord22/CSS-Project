package uaic.css.model.event;

import uaic.css.model.process.Process;
import uaic.css.model.simulation.Processor;

public record Event(int time, EventType type, Process process, Processor processor) implements Comparable<Event> {
    public Event {
        assert time >= 0 : "Event time must be non-negative, got: " + time;
        assert type != null : "Event type must not be null";

    }

    public Event(int time, EventType type) {
        this(time, type, null, null);
    }

    public Event(int time, EventType type, Process process) {
        this(time, type, process, null);
    }

    @Override
    public int compareTo(Event other) {
        int timeCompare = Integer.compare(this.time, other.time);
        if (timeCompare != 0) {
            return timeCompare;
        }
        return Integer.compare(this.type.getPriority(), other.type.getPriority());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Event{T=").append(time).append(", ").append(type);
        if (process != null) {
            sb.append(", process=").append(process.getName());
        }
        if (processor != null) {
            sb.append(", processor=").append(processor.getId());
        }
        sb.append("}");
        return sb.toString();
    }
}
