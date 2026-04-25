package uaic.css.model.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SimulationResult(List<ExecutionLogEntry> logEntries, int totalTime) {
    public SimulationResult(List<ExecutionLogEntry> logEntries, int totalTime) {
        this.logEntries = logEntries != null ? logEntries : new ArrayList<>();
        this.totalTime = totalTime;
    }

    @Override
    public List<ExecutionLogEntry> logEntries() {
        return Collections.unmodifiableList(logEntries);
    }

    public List<ExecutionLogEntry> getEntriesForProcessor(int processorId) {
        List<ExecutionLogEntry> result = new ArrayList<>();
        for (ExecutionLogEntry entry : logEntries) {
            if (entry.processorId() == processorId) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<ExecutionLogEntry> getDiskEntries() {
        List<ExecutionLogEntry> result = new ArrayList<>();
        for (ExecutionLogEntry entry : logEntries) {
            if (entry.type() == EntryType.DISK_LOAD
                    || entry.type() == EntryType.DISK_SAVE) {
                result.add(entry);
            }
        }
        return result;
    }
}
