package uaic.css.model.system;

public class ExecutionLogEntry {

    public enum EntryType {
        CPU_BURST,
        SYSCALL,
        DISK_LOAD,
        DISK_SAVE,
        IDLE
    }

    private final String label;
    private final int processorId;
    private final int startTime;
    private final int endTime;
    private final EntryType type;

    public ExecutionLogEntry(String label, int processorId, int startTime, int endTime, EntryType type) {
        assert label != null && !label.isEmpty() : "Label must not be null or empty";
        assert startTime >= 0 : "Start time must be non-negative, got: " + startTime;
        assert endTime > startTime : "End time (" + endTime + ") must be greater than start time (" + startTime + ")";

        this.label = label;
        this.processorId = processorId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public int getProcessorId() {
        return processorId;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public EntryType getType() {
        return type;
    }

    public int getDuration() {
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return "[T=" + startTime + "-" + endTime + "] " + label + " on Processor " + processorId + " (" + type + ")";
    }
}
