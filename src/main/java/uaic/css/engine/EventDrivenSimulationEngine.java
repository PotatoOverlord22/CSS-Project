package uaic.css.engine;

import uaic.css.config.SimulationConfig;
import uaic.css.memory.MemoryManager;
import uaic.css.model.event.Event;
import uaic.css.model.event.EventType;
import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;
import uaic.css.model.system.ExecutionLogEntry;
import uaic.css.model.system.Processor;
import uaic.css.model.system.SystemCallRequest;
import uaic.css.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class EventDrivenSimulationEngine implements SimulationEngine {

    private PriorityQueue<Event> eventQueue;
    private List<Processor> processors;
    private Scheduler scheduler;
    private MemoryManager memoryManager;
    private Queue<SystemCallRequest> syscallQueue;
    private List<ExecutionLogEntry> logEntries;
    private SimulationConfig config;
    private int currentTime;
    private int lastMeaningfulTime;

    // Tracks whether the system process is currently waiting for a free processor
    private boolean systemProcessWaiting;

    // Disk is a single shared resource — tracks when it becomes free
    private int diskBusyUntil;

    // All processes for termination checking
    private List<Process> allProcesses;

    @Override
    public SimulationResult run(SimulationConfig config, List<Process> processes) {
        initialize(config, processes);
        seedInitialEvents(processes, config);
        runEventLoop();
        return new SimulationResult(logEntries, lastMeaningfulTime);
    }

    private void initialize(SimulationConfig config, List<Process> processes) {
        this.config = config;
        this.eventQueue = new PriorityQueue<>();
        this.processors = new ArrayList<>();
        this.scheduler = new Scheduler();
        this.memoryManager = new MemoryManager(config.getMemorySize(), config.getDiskTransferRate());
        this.syscallQueue = new LinkedList<>();
        this.logEntries = new ArrayList<>();
        this.currentTime = 0;
        this.lastMeaningfulTime = 0;
        this.systemProcessWaiting = false;
        this.diskBusyUntil = 0;
        this.allProcesses = processes;

        for (int i = 0; i < config.getProcessors(); i++) {
            processors.add(new Processor(i));
        }
    }

    private void seedInitialEvents(List<Process> processes, SimulationConfig config) {
        for (Process process : processes) {
            eventQueue.add(new Event(process.getReleaseTime(), EventType.PROCESS_RELEASE, process));
        }

        // Seed system process release events — generous estimate
        int maxReleaseTime = 0;
        int totalBurstTime = 0;
        for (Process process : processes) {
            maxReleaseTime = Math.max(maxReleaseTime, process.getReleaseTime());
            for (int burst : process.getCpuBursts()) {
                totalBurstTime += burst;
            }
            for (int syscall : process.getSyscallDurations()) {
                totalBurstTime += syscall;
            }
        }
        int estimatedEndTime = maxReleaseTime + totalBurstTime * 10;

        for (int t = config.getSystemProcessPeriod(); t <= estimatedEndTime; t += config.getSystemProcessPeriod()) {
            eventQueue.add(new Event(t, EventType.SYSTEM_PROCESS_RELEASE));
        }
    }

    private void runEventLoop() {
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            currentTime = event.getTime();

            // Stop if all processes are terminated
            if (allProcessesTerminated()) {
                break;
            }

            switch (event.getType()) {
                case PROCESS_RELEASE -> handleProcessRelease(event);
                case TIME_SLICE_EXPIRED -> handleTimeSliceExpired(event);
                case BURST_COMPLETED -> handleBurstCompleted(event);
                case SYSCALL_COMPLETED -> handleSyscallCompleted(event);
                case SYSTEM_PROCESS_RELEASE -> handleSystemProcessRelease(event);
                case DISK_TRANSFER_COMPLETE -> handleDiskTransferComplete(event);
            }
        }
    }

    private boolean allProcessesTerminated() {
        for (Process p : allProcesses) {
            if (!p.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    private void updateLastMeaningfulTime() {
        if (currentTime > lastMeaningfulTime) {
            lastMeaningfulTime = currentTime;
        }
    }

    // ========== Event Handlers ==========

    private void handleProcessRelease(Event event) {
        Process process = event.getProcess();
        assert process != null : "PROCESS_RELEASE event must have an associated process";

        process.setState(ProcessState.READY);
        scheduler.addToReadyQueue(process);
        trySchedule();
    }

    private void handleTimeSliceExpired(Event event) {
        Process process = event.getProcess();
        Processor processor = event.getProcessor();
        assert process != null : "TIME_SLICE_EXPIRED event must have an associated process";
        assert processor != null : "TIME_SLICE_EXPIRED event must have an associated processor";

        // Only handle if the process is still running on this processor
        if (process.getState() != ProcessState.RUNNING || processor.getCurrentProcess() != process) {
            return;
        }

        process.setState(ProcessState.READY);
        processor.setCurrentProcess(null);
        scheduler.addToReadyQueue(process);
        updateLastMeaningfulTime();

        trySchedule();
    }

    private void handleBurstCompleted(Event event) {
        Process process = event.getProcess();
        Processor processor = event.getProcessor();
        assert process != null : "BURST_COMPLETED event must have an associated process";
        assert processor != null : "BURST_COMPLETED event must have an associated processor";

        // Only handle if the process is still running on this processor
        if (process.getState() != ProcessState.RUNNING || processor.getCurrentProcess() != process) {
            return;
        }

        processor.setCurrentProcess(null);
        updateLastMeaningfulTime();

        if (process.hasSyscallAfterCurrentBurst()) {
            int syscallDuration = process.getCurrentSyscallDuration();
            syscallQueue.add(new SystemCallRequest(process, syscallDuration));
            process.setState(ProcessState.WAITING_SYSCALL);
            process.advanceToNextBurst();
        } else {
            process.setState(ProcessState.TERMINATED);
            // Free memory immediately — no need to save a terminated process
            if (memoryManager.isLoaded(process)) {
                memoryManager.unloadProcess(process);
            }
        }

        trySchedule();
    }

    private void handleSyscallCompleted(Event event) {
        Process process = event.getProcess();
        assert process != null : "SYSCALL_COMPLETED event must have an associated process";

        updateLastMeaningfulTime();

        if (process.getState() == ProcessState.WAITING_SYSCALL && process.hasMoreBursts()) {
            process.setState(ProcessState.READY);
            scheduler.addToReadyQueue(process);
        }

        trySchedule();
    }

    private void handleSystemProcessRelease(Event event) {
        // Case 1: "system process done" signal (processor is attached)
        if (event.getProcessor() != null) {
            event.getProcessor().setBusyWithSystemProcess(false);
            trySchedule();
            return;
        }

        // Case 2: Periodic release of a new system process instance
        if (syscallQueue.isEmpty()) {
            return;
        }

        Processor freeProcessor = scheduler.findFreeProcessor(processors);
        if (freeProcessor != null) {
            executeSystemProcess(freeProcessor);
        } else {
            systemProcessWaiting = true;
        }
    }

    private void executeSystemProcess(Processor processor) {
        systemProcessWaiting = false;
        processor.setBusyWithSystemProcess(true);

        int syscallStartTime = currentTime;

        while (!syscallQueue.isEmpty()) {
            SystemCallRequest request = syscallQueue.poll();
            int syscallEndTime = syscallStartTime + request.getDuration();

            logEntries.add(new ExecutionLogEntry(
                    "SysCall(" + request.getRequestingProcess().getName() + ")",
                    processor.getId(),
                    syscallStartTime,
                    syscallEndTime,
                    ExecutionLogEntry.EntryType.SYSCALL
            ));

            eventQueue.add(new Event(
                    syscallEndTime,
                    EventType.SYSCALL_COMPLETED,
                    request.getRequestingProcess(),
                    processor
            ));

            syscallStartTime = syscallEndTime;
        }

        int systemProcessEndTime = syscallStartTime;

        if (systemProcessEndTime > currentTime) {
            eventQueue.add(new Event(
                    systemProcessEndTime,
                    EventType.SYSTEM_PROCESS_RELEASE,
                    null,
                    processor
            ));
        } else {
            processor.setBusyWithSystemProcess(false);
        }
    }

    private void handleDiskTransferComplete(Event event) {
        Process process = event.getProcess();
        assert process != null : "DISK_TRANSFER_COMPLETE event must have an associated process";

        // Commit the load — process is now actually in memory
        memoryManager.commitLoad(process, currentTime);

        // Process is now in memory, add to ready queue for normal scheduling
        process.setState(ProcessState.READY);
        scheduler.addToReadyQueue(process);
        updateLastMeaningfulTime();

        // Let the scheduler handle processor assignment with affinity
        trySchedule();
    }

    // ========== Scheduling ==========

    private void trySchedule() {
        // First: if system process is waiting, try to give it a processor
        if (systemProcessWaiting && !syscallQueue.isEmpty()) {
            Processor freeProcessor = scheduler.findFreeProcessor(processors);
            if (freeProcessor != null) {
                executeSystemProcess(freeProcessor);
                return;
            }
        }

        // Pass 1: Schedule all in-memory processes onto free processors
        boolean scheduled = true;
        while (scheduled) {
            scheduled = false;

            // Find the first in-memory process in the ready queue
            Process inMemoryProcess = scheduler.getReadyQueue().findFirst(memoryManager::isLoaded);
            if (inMemoryProcess == null) {
                break; // No in-memory process in ready queue
            }

            // Find a free processor (with affinity preference)
            Processor bestProcessor = scheduler.findBestProcessor(inMemoryProcess, processors);
            if (bestProcessor == null) {
                break; // No free processor
            }

            // Remove from queue and schedule
            scheduler.getReadyQueue().remove(inMemoryProcess);
            scheduleProcessOnProcessor(inMemoryProcess, bestProcessor);
            scheduled = true;
        }

        // Pass 2: If there are still ready processes not in memory, initiate ONE disk load
        if (scheduler.hasReadyProcesses()) {
            // Find the first not-in-memory process
            Process toLoad = scheduler.getReadyQueue().findFirst(p -> !memoryManager.isLoaded(p));
            if (toLoad != null && memoryManager.canFreeEnoughMemory(toLoad)) {
                scheduler.getReadyQueue().remove(toLoad);
                initiateMemoryLoad(toLoad);
            }
        }
    }

    // ========== Memory & Disk ==========

    private void initiateMemoryLoad(Process process) {
        process.setState(ProcessState.LOADING);

        // Plan eviction if needed
        MemoryManager.EvictionResult eviction = memoryManager.planEviction(process);

        // All disk operations go through the single disk — sequential
        int diskTime = Math.max(currentTime, diskBusyUntil);

        // Evict LRU processes (save to disk sequentially)
        for (Process toEvict : eviction.getProcessesToEvict()) {
            int transferTime = memoryManager.calculateTransferTime(toEvict);

            logEntries.add(new ExecutionLogEntry(
                    "Save " + toEvict.getName(),
                    -1,
                    diskTime,
                    diskTime + transferTime,
                    ExecutionLogEntry.EntryType.DISK_SAVE
            ));

            memoryManager.unloadProcess(toEvict);
            diskTime += transferTime;
        }

        // Load the new process from disk
        int loadTime = memoryManager.calculateTransferTime(process);

        logEntries.add(new ExecutionLogEntry(
                "Load " + process.getName(),
                -1,
                diskTime,
                diskTime + loadTime,
                ExecutionLogEntry.EntryType.DISK_LOAD
        ));

        // Reserve memory space (commit happens when transfer completes)
        memoryManager.reserveSpace(process.getMemoryRequired());

        int loadEndTime = diskTime + loadTime;
        diskBusyUntil = loadEndTime;

        // Schedule disk transfer completion — no processor attached
        eventQueue.add(new Event(loadEndTime, EventType.DISK_TRANSFER_COMPLETE, process));
    }

    // ========== Process Execution ==========

    private void scheduleProcessOnProcessor(Process process, Processor processor) {
        // Double-check processor is still free
        if (!processor.isFree()) {
            Processor altProcessor = scheduler.findBestProcessor(process, processors);
            if (altProcessor == null) {
                process.setState(ProcessState.READY);
                scheduler.addToReadyQueue(process);
                return;
            }
            processor = altProcessor;
        }

        process.setState(ProcessState.RUNNING);
        processor.setCurrentProcess(process);
        process.setLastProcessorId(processor.getId());
        memoryManager.updateLastUsedTime(process, currentTime);

        int burstTime = process.getRemainingBurstTime();
        int timeSlice = config.getTimeSlice();

        if (burstTime <= timeSlice) {
            int endTime = currentTime + burstTime;
            logEntries.add(new ExecutionLogEntry(
                    process.getName(),
                    processor.getId(),
                    currentTime,
                    endTime,
                    ExecutionLogEntry.EntryType.CPU_BURST
            ));
            process.setRemainingBurstTime(0);
            eventQueue.add(new Event(endTime, EventType.BURST_COMPLETED, process, processor));
        } else {
            int endTime = currentTime + timeSlice;
            logEntries.add(new ExecutionLogEntry(
                    process.getName(),
                    processor.getId(),
                    currentTime,
                    endTime,
                    ExecutionLogEntry.EntryType.CPU_BURST
            ));
            process.setRemainingBurstTime(burstTime - timeSlice);
            eventQueue.add(new Event(endTime, EventType.TIME_SLICE_EXPIRED, process, processor));
        }
    }
}
