package uaic.css.model.system;

import uaic.css.model.process.Process;

public class Processor {
    private final int id;
    private Process currentProcess;
    private boolean busyWithSystemProcess;
    private boolean busyWithDiskTransfer;

    public Processor(int id) {
        this.id = id;
        this.currentProcess = null;
        this.busyWithSystemProcess = false;
        this.busyWithDiskTransfer = false;
    }

    public int getId() {
        return id;
    }

    public Process getCurrentProcess() {
        return currentProcess;
    }

    public void setCurrentProcess(Process process) {
        this.currentProcess = process;
    }

    public boolean isFree() {
        return currentProcess == null && !busyWithSystemProcess && !busyWithDiskTransfer;
    }

    public boolean isBusyWithSystemProcess() {
        return busyWithSystemProcess;
    }

    public void setBusyWithSystemProcess(boolean busy) {
        this.busyWithSystemProcess = busy;
    }

    public boolean isBusyWithDiskTransfer() {
        return busyWithDiskTransfer;
    }

    public void setBusyWithDiskTransfer(boolean busy) {
        this.busyWithDiskTransfer = busy;
    }

    @Override
    public String toString() {
        return "Processor " + id;
    }
}
