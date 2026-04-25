package uaic.css.scheduler;

import uaic.css.model.process.Process;
import uaic.css.model.system.Processor;

import java.util.List;

public class Scheduler {
    private final ReadyQueue readyQueue;

    public Scheduler() {
        this.readyQueue = new ReadyQueue();
    }

    public ReadyQueue getReadyQueue() {
        return readyQueue;
    }

    public void addToReadyQueue(Process process) {
        readyQueue.enqueue(process);
    }

    public Process getNextProcess() {
        if (readyQueue.isEmpty()) {
            return null;
        }
        return readyQueue.dequeue();
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

    public boolean hasReadyProcesses() {
        return !readyQueue.isEmpty();
    }
}
