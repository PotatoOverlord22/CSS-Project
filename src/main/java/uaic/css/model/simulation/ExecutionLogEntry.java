package uaic.css.model.simulation;

public record ExecutionLogEntry(String label, int processorId, int startTime, int endTime, EntryType type) {

    /**
     * Processor ID used for disk operations (not associated with any CPU).
     */
    public static final int DISK_PROCESSOR_ID = -1;

    public ExecutionLogEntry {
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("Label must not be null or empty");
        }
        if (startTime < 0) {
            throw new IllegalArgumentException("Start time must be non-negative, got: " + startTime);
        }
        if (endTime <= startTime) {
            throw new IllegalArgumentException(
                    "End time (" + endTime + ") must be greater than start time (" + startTime + ")");
        }
    }

    public int getDuration() {
        return endTime - startTime;
    }

    @Override
    public String toString() {
        return "[T=" + startTime + "-" + endTime + "] " + label + " on Processor " + processorId + " (" + type + ")";
    }
}
