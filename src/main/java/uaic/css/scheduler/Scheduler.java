package uaic.css.scheduler;

import uaic.css.memory.MemoryManager;
import uaic.css.model.simulation.ReadyQueue;
import uaic.css.model.simulation.SchedulingDecision;
import uaic.css.model.process.Process;
import uaic.css.model.simulation.Processor;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-Robin scheduler with processor affinity support.
 * Manages the ready queue and makes scheduling decisions.
 */
public class Scheduler {
    private final ReadyQueue readyQueue;

    public Scheduler() {
        this.readyQueue = new ReadyQueue();
    }

    public void addToReadyQueue(Process process) {
        readyQueue.enqueue(process);
    }

    public boolean hasReadyProcesses() {
        return !readyQueue.isEmpty();
    }

    /**
     * Determines which in-memory ready processes should be scheduled onto which
     * free processors.
     * Returns a list of (Process, Processor) assignments.
     * Does NOT modify process/processor state — the caller is responsible for that.
     */
    public List<SchedulingDecision> scheduleReadyProcesses(List<Processor> processors,
            MemoryManager memoryManager) {
        List<SchedulingDecision> decisions = new ArrayList<>();

        boolean found = true;
        while (found) {
            found = false;

            // Find the first in-memory process in the ready queue
            Process inMemoryProcess = readyQueue.findFirst(memoryManager::isLoaded);
            if (inMemoryProcess == null) {
                break;
            }

            // Find a free processor (with affinity preference)
            Processor bestProcessor = findBestProcessor(inMemoryProcess, processors);
            if (bestProcessor == null) {
                break;
            }

            readyQueue.remove(inMemoryProcess);
            decisions.add(new SchedulingDecision(inMemoryProcess, bestProcessor));
            found = true;
        }

        return decisions;
    }

    /**
     * Finds the next process that needs to be loaded from disk (not currently in
     * memory).
     * Returns the process, or null if none found. Removes it from the ready queue.
     */
    public Process dequeueNextProcessNeedingLoad(MemoryManager memoryManager) {
        Process toLoad = readyQueue.findFirst(p -> !memoryManager.isLoaded(p));
        if (toLoad != null && memoryManager.canFreeEnoughMemory(toLoad)) {
            readyQueue.remove(toLoad);
            return toLoad;
        }
        return null;
    }

    /**
     * Finds the best processor for the given process using processor affinity.
     * If multiple processors are free, prefers the one the process last ran on.
     * Returns null if no processor is free.
     */
    public Processor findBestProcessor(Process process, List<Processor> processors) {
        Processor affinityProcessor = null;
        Processor anyFreeProcessor = null;

        for (Processor processor : processors) {
            if (processor.isFree()) {
                if (process.getLastProcessorId() == processor.getId()) {
                    affinityProcessor = processor;
                    break;
                }
                if (anyFreeProcessor == null) {
                    anyFreeProcessor = processor;
                }
            }
        }

        return affinityProcessor != null ? affinityProcessor : anyFreeProcessor;
    }

    /**
     * Finds any free processor (for the system process, which has no affinity).
     */
    public Processor findFreeProcessor(List<Processor> processors) {
        for (Processor processor : processors) {
            if (processor.isFree()) {
                return processor;
            }
        }
        return null;
    }

}
