package uaic.css.memory;

import uaic.css.model.simulation.EvictionResult;
import uaic.css.model.event.Event;
import uaic.css.model.event.EventType;
import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;
import uaic.css.model.simulation.EntryType;
import uaic.css.model.simulation.ExecutionLogEntry;

import uaic.css.util.MinHeapPriorityQueue;

import java.util.List;

/**
 * Manages disk I/O operations for virtual memory transfers.
 * The disk is a single shared resource — all operations are sequential.
 */
public class DiskController {
    private final MemoryManager memoryManager;
    private int diskBusyUntil;

    public DiskController(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.diskBusyUntil = 0;
    }

    /**
     * Initiates loading a process from disk into memory.
     * Handles eviction of LRU processes if needed.
     * Adds log entries for all disk operations and schedules the
     * DISK_TRANSFER_COMPLETE event.
     *
     * @param process     the process to load
     * @param currentTime the current simulation time
     * @param logEntries  the log to append disk operation entries to
     * @param eventQueue  the event queue to schedule the completion event
     */
    public void initiateMemoryLoad(Process process, int currentTime,
            List<ExecutionLogEntry> logEntries,
            MinHeapPriorityQueue<Event> eventQueue) {
        assert process != null : "Process must not be null";
        assert currentTime >= 0 : "Current time must be non-negative";
        assert logEntries != null : "Log entries list must not be null";
        assert eventQueue != null : "Event queue must not be null";
        assert !memoryManager.isLoaded(process) : "Process " + process.getName() + " is already loaded in memory";

        int diskBusyUntilBefore = diskBusyUntil;
        process.setState(ProcessState.LOADING);

        EvictionResult eviction = memoryManager.planEviction(process);

        int diskTime = Math.max(currentTime, diskBusyUntil);

        // Evict LRU processes (save to disk sequentially)
        for (Process toEvict : eviction.processesToEvict()) {
            int transferTime = memoryManager.calculateTransferTime(toEvict);

            logEntries.add(new ExecutionLogEntry(
                    "Save " + toEvict.getName(),
                    ExecutionLogEntry.DISK_PROCESSOR_ID,
                    diskTime,
                    diskTime + transferTime,
                    EntryType.DISK_SAVE));

            memoryManager.unloadProcess(toEvict);
            diskTime += transferTime;
        }

        // Load the new process from disk
        int loadTime = memoryManager.calculateTransferTime(process);

        logEntries.add(new ExecutionLogEntry(
                "Load " + process.getName(),
                ExecutionLogEntry.DISK_PROCESSOR_ID,
                diskTime,
                diskTime + loadTime,
                EntryType.DISK_LOAD));

        // Reserve memory space (commit happens when transfer completes)
        memoryManager.reserveSpace(process.getMemoryRequired());

        int loadEndTime = diskTime + loadTime;
        diskBusyUntil = loadEndTime;

        assert diskBusyUntil > diskBusyUntilBefore || diskBusyUntil > currentTime
                : "diskBusyUntil must advance after initiating a memory load";
        assert loadEndTime > currentTime : "Load end time must be after current time";

        eventQueue.add(new Event(loadEndTime, EventType.DISK_TRANSFER_COMPLETE, process));
    }
}
