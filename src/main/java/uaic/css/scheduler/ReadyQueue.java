package uaic.css.scheduler;

import uaic.css.model.process.Process;

import java.util.LinkedList;
import java.util.Queue;

public class ReadyQueue {
    private final Queue<Process> queue;

    public ReadyQueue() {
        this.queue = new LinkedList<>();
    }

    public void enqueue(Process process) {
        assert process != null : "Cannot enqueue a null process";
        queue.add(process);
    }

    public Process dequeue() {
        assert !isEmpty() : "Cannot dequeue from an empty ready queue";
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
}
