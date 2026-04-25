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
        // Seed process release events
        for (Process process : processes) {
            eventQueue.add(new Event(process.getReleaseTime(), EventType.PROCESS_RELEASE, process));
        }

        // Seed system process release events
        // We generate enough to cover the simulation; we can always add more if needed
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
        int estimatedEndTime = maxReleaseTime + totalBurstTime * 3; // generous estimate

        for (int t = config.getSystemProcessPeriod(); t <= estimatedEndTime; t += config.getSystemProcessPeriod()) {
            eventQueue.add(new Event(t, EventType.SYSTEM_PROCESS_RELEASE));
        }
    }

    private void runEventLoop() {
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            currentTime = event.getTime();

            // Bug fix #4: Stop if all processes are terminated
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

        // Preempt the process
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

        // Free the processor
        processor.setCurrentProcess(null);
        updateLastMeaningfulTime();

        // Check if there's a syscall to execute after this burst
        if (process.hasSyscallAfterCurrentBurst()) {
            // Queue the syscall
            int syscallDuration = process.getCurrentSyscallDuration();
            syscallQueue.add(new SystemCallRequest(process, syscallDuration));
            process.setState(ProcessState.WAITING_SYSCALL);
            process.advanceToNextBurst();
        } else {
            // No more bursts — process is done
            process.setState(ProcessState.TERMINATED);
            // Free memory immediately — no need to save a terminated process to disk
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

        // The process that requested this syscall can now continue
        if (process.getState() == ProcessState.WAITING_SYSCALL && process.hasMoreBursts()) {
            process.setState(ProcessState.READY);
            scheduler.addToReadyQueue(process);
        }

        trySchedule();
    }

    private void handleSystemProcessRelease(Event event) {
        // Case 1: This is the "system process done" signal (processor is attached)
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
        Processor lockedProcessor = event.getProcessor();
        assert process != null : "DISK_TRANSFER_COMPLETE event must have an associated process";
        assert lockedProcessor != null : "DISK_TRANSFER_COMPLETE event must have an associated processor";

        // Commit the load — process is now actually in memory
        memoryManager.commitLoad(process, currentTime);

        // Free the processor that was waiting for the disk transfer
        lockedProcessor.setBusyWithDiskTransfer(false);

        // Process is now in memory, schedule it
        process.setState(ProcessState.READY);
        updateLastMeaningfulTime();

        // Re-evaluate processor affinity now that processor states may have changed
        Processor bestProcessor = scheduler.findBestProcessor(process, processors);
        if (bestProcessor == null) {
            bestProcessor = lockedProcessor; // fallback to the one that was waiting
        }

        scheduleProcessOnProcessor(process, bestProcessor);
    }

    private void trySchedule() {
        // First: if system process is waiting, try to give it a processor
        if (systemProcessWaiting && !syscallQueue.isEmpty()) {
            Processor freeProcessor = scheduler.findFreeProcessor(processors);
            if (freeProcessor != null) {
                executeSystemProcess(freeProcessor);
                return;
            }
        }

        // Then: try to schedule user processes
        boolean scheduled = true;
        while (scheduled) {
            scheduled = false;

            if (!scheduler.hasReadyProcesses()) {
                break;
            }

            Process nextProcess = scheduler.getReadyQueue().peek();
            if (nextProcess == null) {
                break;
            }

            Processor bestProcessor = scheduler.findBestProcessor(nextProcess, processors);
            if (bestProcessor == null) {
                break;
            }

            // Dequeue the process
            scheduler.getNextProcess();

            // Check if process is in memory
            if (!memoryManager.isLoaded(nextProcess)) {
                initiateMemoryLoad(nextProcess, bestProcessor);
            } else {
                scheduleProcessOnProcessor(nextProcess, bestProcessor);
            }

            scheduled = true;
        }
    }

    private void initiateMemoryLoad(Process process, Processor processor) {
        process.setState(ProcessState.LOADING);

        // Bug fix #3: Mark processor as busy waiting for disk transfer
        processor.setBusyWithDiskTransfer(true);

        // Plan eviction if needed
        MemoryManager.EvictionResult eviction = memoryManager.planEviction(process);

        // Bug fix #2: All disk operations go through the single disk queue
        // Disk operations start from max(currentTime, diskBusyUntil)
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

        // Load the new process from disk (also sequential)
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

        // Schedule the disk transfer completion
        eventQueue.add(new Event(loadEndTime, EventType.DISK_TRANSFER_COMPLETE, process, processor));
    }

    private void scheduleProcessOnProcessor(Process process, Processor processor) {
        // If the processor is no longer free, try another
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
