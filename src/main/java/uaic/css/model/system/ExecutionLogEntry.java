package uaic.css.model.system;

public record ExecutionLogEntry(String label, int processorId, int startTime, int endTime, EntryType type) {

    /**
     * Processor ID used for disk operations (not associated with any CPU).
     */
    public static final int DISK_PROCESSOR_ID = -1;

    public ExecutionLogEntry {
        assert label != null && !label.isEmpty() : "Label must not be null or empty";
        assert startTime >= 0 : "Start time must be non-negative, got: " + startTime;
        assert endTime > startTime : "End time (" + endTime + ") must be greater than start time (" + startTime + ")";

    }

    public int getDuration() {
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return "[T=" + startTime + "-" + endTime + "] " + label + " on Processor " + processorId + " (" + type + ")";
    }
}
