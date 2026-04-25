package uaic.css.engine;

import uaic.css.config.SimulationConfig;
import uaic.css.memory.DiskController;
import uaic.css.memory.MemoryManager;
import uaic.css.model.event.Event;
import uaic.css.model.event.EventType;
import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;
import uaic.css.model.simulation.*;
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
    private DiskController diskController;
    private Queue<SystemCallRequest> syscallQueue;
    private List<ExecutionLogEntry> logEntries;
    private SimulationConfig config;
    private int currentTime;
    private int lastMeaningfulTime;

    // Tracks whether the system process is currently waiting for a free processor
    private boolean systemProcessWaiting;

    // Tracks the next absolute time for a system process release (strictly periodic)
    private int nextSystemProcessReleaseTime;

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
        this.memoryManager = new MemoryManager(config.memorySize(), config.diskTransferRate());
        this.diskController = new DiskController(memoryManager);
        this.scheduler = new Scheduler();
        this.syscallQueue = new LinkedList<>();
        this.logEntries = new ArrayList<>();
        this.currentTime = 0;
        this.lastMeaningfulTime = 0;
        this.systemProcessWaiting = false;
        this.allProcesses = processes;

        for (int i = 0; i < config.processors(); i++) {
            processors.add(new Processor(i));
        }
    }

    private void seedInitialEvents(List<Process> processes, SimulationConfig config) {
        for (Process process : processes) {
            eventQueue.add(new Event(process.getReleaseTime(), EventType.PROCESS_RELEASE, process));
        }

        // Schedule only the first system process release; subsequent ones are scheduled lazily
        nextSystemProcessReleaseTime = config.systemProcessPeriod();
        eventQueue.add(new Event(nextSystemProcessReleaseTime, EventType.SYSTEM_PROCESS_RELEASE));
    }

    private void runEventLoop() {
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();
            currentTime = event.time();

            // Stop if all processes are terminated
            if (allProcessesTerminated()) {
                break;
            }

            switch (event.type()) {
                case PROCESS_RELEASE -> handleProcessRelease(event);
                case TIME_SLICE_EXPIRED -> handleTimeSliceExpired(event);
                case BURST_COMPLETED -> handleBurstCompleted(event);
                case SYSCALL_COMPLETED -> handleSyscallCompleted(event);
                case SYSTEM_PROCESS_RELEASE -> handleSystemProcessRelease(event);
                case SYSTEM_PROCESS_COMPLETED -> handleSystemProcessCompleted(event);
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
        Process process = event.process();
        assert process != null : "PROCESS_RELEASE event must have an associated process";

        process.setState(ProcessState.READY);
        scheduler.addToReadyQueue(process);
        trySchedule();
    }

    private void handleTimeSliceExpired(Event event) {
        Process process = event.process();
        Processor processor = event.processor();
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
        Process process = event.process();
        Processor processor = event.processor();
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
            if (memoryManager.isLoaded(process)) {
                memoryManager.unloadProcess(process);
            }
        }

        trySchedule();
    }

    private void handleSyscallCompleted(Event event) {
        Process process = event.process();
        assert process != null : "SYSCALL_COMPLETED event must have an associated process";

        updateLastMeaningfulTime();

        if (process.getState() == ProcessState.WAITING_SYSCALL && process.hasMoreBursts()) {
            process.setState(ProcessState.READY);
            scheduler.addToReadyQueue(process);
        }

        trySchedule();
    }

    private void handleSystemProcessRelease(Event event) {
        // Schedule the next periodic release at the exact next multiple (no drift)
        nextSystemProcessReleaseTime += config.systemProcessPeriod();
        eventQueue.add(new Event(nextSystemProcessReleaseTime, EventType.SYSTEM_PROCESS_RELEASE));

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

    private void handleSystemProcessCompleted(Event event) {
        Processor processor = event.processor();
        assert processor != null : "SYSTEM_PROCESS_COMPLETED event must have an associated processor";

        processor.setBusyWithSystemProcess(false);
        trySchedule();
    }

    private void handleDiskTransferComplete(Event event) {
        Process process = event.process();
        assert process != null : "DISK_TRANSFER_COMPLETE event must have an associated process";

        memoryManager.commitLoad(process, currentTime);
        process.setState(ProcessState.READY);
        scheduler.addToReadyQueue(process);
        updateLastMeaningfulTime();

        trySchedule();
    }

    // ========== System Process ==========

    private void executeSystemProcess(Processor processor) {
        systemProcessWaiting = false;
        processor.setBusyWithSystemProcess(true);

        int syscallStartTime = currentTime;

        while (!syscallQueue.isEmpty()) {
            SystemCallRequest request = syscallQueue.poll();
            int syscallEndTime = syscallStartTime + request.duration();

            logEntries.add(new ExecutionLogEntry(
                    "SysCall(" + request.requestingProcess().getName() + ")",
                    processor.getId(),
                    syscallStartTime,
                    syscallEndTime,
                    EntryType.SYSCALL
            ));

            eventQueue.add(new Event(
                    syscallEndTime,
                    EventType.SYSCALL_COMPLETED,
                    request.requestingProcess(),
                    processor
            ));

            syscallStartTime = syscallEndTime;
        }

        int systemProcessEndTime = syscallStartTime;

        if (systemProcessEndTime > currentTime) {
            eventQueue.add(new Event(
                    systemProcessEndTime,
                    EventType.SYSTEM_PROCESS_COMPLETED,
                    null,
                    processor
            ));
        } else {
            processor.setBusyWithSystemProcess(false);
        }
    }

    // ========== Scheduling ==========

    private void trySchedule() {
        // System process has higher priority
        if (systemProcessWaiting && !syscallQueue.isEmpty()) {
            Processor freeProcessor = scheduler.findFreeProcessor(processors);
            if (freeProcessor != null) {
                executeSystemProcess(freeProcessor);
            }
        }

        // Pass 1: Schedule in-memory ready processes onto free processors
        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);
        for (SchedulingDecision decision : decisions) {
            dispatchProcessOnProcessor(decision.process(), decision.processor());
        }

        // Pass 2: If there are still ready processes not in memory, initiate ONE disk load
        if (scheduler.hasReadyProcesses()) {
            Process toLoad = scheduler.dequeueNextProcessNeedingLoad(memoryManager);
            if (toLoad != null) {
                diskController.initiateMemoryLoad(toLoad, currentTime, logEntries, eventQueue);
            }
        }
    }

    // ========== Process Execution ==========

    private void dispatchProcessOnProcessor(Process process, Processor processor) {
        assert processor.isFree() : "Processor " + processor.getId()
                + " must be free when dispatching process " + process.getName();

        process.setState(ProcessState.RUNNING);
        processor.setCurrentProcess(process);
        process.setLastProcessorId(processor.getId());
        memoryManager.updateLastUsedTime(process, currentTime);

        int burstTime = process.getRemainingBurstTime();
        int timeSlice = config.timeSlice();

        if (burstTime <= timeSlice) {
            int endTime = currentTime + burstTime;
            logEntries.add(new ExecutionLogEntry(
                    process.getName(),
                    processor.getId(),
                    currentTime,
                    endTime,
                    EntryType.CPU_BURST
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
                    EntryType.CPU_BURST
            ));
            process.setRemainingBurstTime(burstTime - timeSlice);
            eventQueue.add(new Event(endTime, EventType.TIME_SLICE_EXPIRED, process, processor));
        }
    }
}
