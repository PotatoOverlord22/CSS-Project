package uaic.css.model.simulation;

import uaic.css.model.process.Process;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

public class ReadyQueue {
    private final Queue<Process> queue;

    public ReadyQueue() {
        this.queue = new LinkedList<>();
    }

    public void enqueue(Process process) {
        if (process == null) {
            throw new IllegalArgumentException("Cannot enqueue a null process");
        }
        queue.add(process);
    }

    public Process dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot dequeue from an empty ready queue");
        }
        return queue.poll();
    }

    public Process peek() {
        return queue.peek();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public boolean contains(Process process) {
        return queue.contains(process);
    }

    public boolean remove(Process process) {
        return queue.remove(process);
    }

    /**
     * Finds and removes the first process in the queue matching the given
     * predicate.
     * Maintains FIFO order for remaining elements.
     * Returns null if no matching process is found.
     */
    public Process removeFirstMatching(Predicate<Process> predicate) {
        Iterator<Process> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Process process = iterator.next();
            if (predicate.test(process)) {
                iterator.remove();
                return process;
            }
        }
        return null;
    }

    /**
     * Finds the first process in the queue matching the given predicate without
     * removing it.
     * Returns null if no matching process is found.
     */
    public Process findFirst(Predicate<Process> predicate) {
        for (Process process : queue) {
            if (predicate.test(process)) {
                return process;
            }
        }
        return null;
    }
}
