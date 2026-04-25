package uaic.css.model.event;

/**
 * Types of events in the simulation, with explicit priority for tie-breaking.
 * Lower priority value = processed first when events occur at the same time.
 */
public enum EventType {
    PROCESS_RELEASE(1),
    SYSTEM_PROCESS_COMPLETED(2),
    BURST_COMPLETED(3),
    TIME_SLICE_EXPIRED(4),
    SYSCALL_COMPLETED(5),
    SYSTEM_PROCESS_RELEASE(6),
    DISK_TRANSFER_COMPLETE(7);

    private final int priority;

    EventType(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }
}
