package uaic.css.engine;

import uaic.css.model.system.ExecutionLogEntry;

import java.util.ArrayList;
import java.util.List;

public class SimulationResult {
    private final List<ExecutionLogEntry> logEntries;
    private final int totalTime;

    public SimulationResult(List<ExecutionLogEntry> logEntries, int totalTime) {
        this.logEntries = logEntries != null ? logEntries : new ArrayList<>();
        this.totalTime = totalTime;
    }

    public List<ExecutionLogEntry> getLogEntries() {
        return logEntries;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public List<ExecutionLogEntry> getEntriesForProcessor(int processorId) {
        List<ExecutionLogEntry> result = new ArrayList<>();
        for (ExecutionLogEntry entry : logEntries) {
            if (entry.getProcessorId() == processorId) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<ExecutionLogEntry> getDiskEntries() {
        List<ExecutionLogEntry> result = new ArrayList<>();
        for (ExecutionLogEntry entry : logEntries) {
            if (entry.getType() == ExecutionLogEntry.EntryType.DISK_LOAD
                    || entry.getType() == ExecutionLogEntry.EntryType.DISK_SAVE) {
                result.add(entry);
            }
        }
        return result;
    }
}
