package uaic.css.model.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Process {
    private final String name;
    private final int releaseTime;
    private final int memoryRequired;
    private final List<Integer> cpuBursts;
    private final List<Integer> syscallDurations;

    private ProcessState state;
    private int currentBurstIndex;
    private int remainingBurstTime;
    private int lastProcessorId;

    public Process(String name, int releaseTime, int memoryRequired, List<Integer> executionSequence) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Process name must not be null or empty");
        }
        if (releaseTime < 0) {
            throw new IllegalArgumentException("Release time must be non-negative, got: " + releaseTime);
        }
        if (memoryRequired <= 0) {
            throw new IllegalArgumentException("Memory required must be positive, got: " + memoryRequired);
        }

        this.name = name;
        this.releaseTime = releaseTime;
        this.memoryRequired = memoryRequired;
        this.cpuBursts = new ArrayList<>();
        this.syscallDurations = new ArrayList<>();

        parseExecutionSequence(executionSequence);

        this.state = ProcessState.NEW;
        this.currentBurstIndex = 0;
        this.remainingBurstTime = cpuBursts.isEmpty() ? 0 : cpuBursts.get(0);
        this.lastProcessorId = -1;
    }

    private void parseExecutionSequence(List<Integer> sequence) {
        if (sequence == null) {
            throw new IllegalArgumentException("Execution sequence must not be null");
        }
        if (sequence.size() % 2 != 1) {
            throw new IllegalArgumentException(
                    "Execution sequence must have an odd number of elements (alternating CPU bursts and syscalls)");
        }

        for (int i = 0; i < sequence.size(); i++) {
            if (sequence.get(i) <= 0) {
                throw new IllegalArgumentException(
                        "All execution sequence values must be positive, found: " + sequence.get(i) + " at index " + i);
            }

            if (i % 2 == 0) {
                cpuBursts.add(sequence.get(i));
            } else {
                syscallDurations.add(sequence.get(i));
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getReleaseTime() {
        return releaseTime;
    }

    public int getMemoryRequired() {
        return memoryRequired;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public int getCurrentBurstIndex() {
        return currentBurstIndex;
    }

    public int getRemainingBurstTime() {
        return remainingBurstTime;
    }

    public void setRemainingBurstTime(int remainingBurstTime) {
        this.remainingBurstTime = remainingBurstTime;
    }

    public int getLastProcessorId() {
        return lastProcessorId;
    }

    public void setLastProcessorId(int lastProcessorId) {
        this.lastProcessorId = lastProcessorId;
    }

    public List<Integer> getCpuBursts() {
        return cpuBursts;
    }

    public List<Integer> getSyscallDurations() {
        return syscallDurations;
    }

    public boolean hasMoreBursts() {
        return currentBurstIndex < cpuBursts.size();
    }

    public boolean hasSyscallAfterCurrentBurst() {
        return currentBurstIndex < syscallDurations.size();
    }

    public int getCurrentSyscallDuration() {
        if (!hasSyscallAfterCurrentBurst()) {
            throw new IllegalStateException("No syscall after current burst index " + currentBurstIndex);
        }
        return syscallDurations.get(currentBurstIndex);
    }

    public void advanceToNextBurst() {
        currentBurstIndex++;
        if (hasMoreBursts()) {
            remainingBurstTime = cpuBursts.get(currentBurstIndex);
        }
    }

    public boolean isTerminated() {
        return state == ProcessState.TERMINATED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Process process = (Process) o;
        return Objects.equals(name, process.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
