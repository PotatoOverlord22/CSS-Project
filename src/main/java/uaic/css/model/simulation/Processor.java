package uaic.css.model.simulation;

import uaic.css.model.process.Process;

public class Processor {
    private final int id;
    private Process currentProcess;
    private boolean busyWithSystemProcess;

    public Processor(int id) {
        this.id = id;
        this.currentProcess = null;
        this.busyWithSystemProcess = false;
    }

    public int getId() {
        return id;
    }

    public Process getCurrentProcess() {
        return currentProcess;
    }

    public void setCurrentProcess(Process process) {
        assert process == null || this.currentProcess == null
                : "Processor " + id + " already has a process assigned; release it before assigning a new one";
        this.currentProcess = process;
        checkClassInvariant();
    }

    public boolean isFree() {
        return currentProcess == null && !busyWithSystemProcess;
    }

    public boolean isBusyWithSystemProcess() {
        return busyWithSystemProcess;
    }

    public void setBusyWithSystemProcess(boolean busy) {
        this.busyWithSystemProcess = busy;
        checkClassInvariant();
    }

    @Override
    public String toString() {
        return "Processor " + id;
    }

    private void checkClassInvariant() {
        assert !(currentProcess != null && busyWithSystemProcess) : "Processor " + id + " cannot run a user process and a system process simultaneously";
    }
}
