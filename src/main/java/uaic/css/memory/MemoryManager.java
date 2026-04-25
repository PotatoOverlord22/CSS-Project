package uaic.css.memory;

import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryManager {
    private final int totalMemory;
    private final int diskTransferRate;
    private int usedMemory;
    private int reservedMemory; // memory reserved for in-flight disk loads
    private final Map<String, Process> loadedProcesses;
    private final Map<String, Integer> lastUsedTime;

    public MemoryManager(int totalMemory, int diskTransferRate) {
        assert totalMemory > 0 : "Total memory must be positive, got: " + totalMemory;
        assert diskTransferRate > 0 : "Disk transfer rate must be positive, got: " + diskTransferRate;

        this.totalMemory = totalMemory;
        this.diskTransferRate = diskTransferRate;
        this.usedMemory = 0;
        this.reservedMemory = 0;
        this.loadedProcesses = new HashMap<>();
        this.lastUsedTime = new HashMap<>();
    }

    public boolean isLoaded(Process process) {
        return loadedProcesses.containsKey(process.getName());
    }

    /**
     * Reserves memory for a process that is being loaded from disk.
     * The memory is not yet committed — call commitLoad() when the transfer completes.
     */
    public void reserveSpace(int amount) {
        assert getFreeMemory() >= amount
                : "Not enough free memory to reserve. Free: " + getFreeMemory() + ", Requested: " + amount;
        reservedMemory += amount;
    }

    /**
     * Commits a previously reserved load — the process is now actually in memory.
     */
    public void commitLoad(Process process, int currentTime) {
        assert !isLoaded(process) : "Process " + process.getName() + " is already loaded in memory";

        reservedMemory -= process.getMemoryRequired();
        loadedProcesses.put(process.getName(), process);
        usedMemory += process.getMemoryRequired();
        lastUsedTime.put(process.getName(), currentTime);
    }

    public void unloadProcess(Process process) {
        assert isLoaded(process) : "Process " + process.getName() + " is not loaded in memory";

        loadedProcesses.remove(process.getName());
        usedMemory -= process.getMemoryRequired();
        lastUsedTime.remove(process.getName());
    }

    public void updateLastUsedTime(Process process, int time) {
        if (!isLoaded(process)) {
            return; // Process may have been evicted; silently skip
        }
        lastUsedTime.put(process.getName(), time);
    }

    public int getFreeMemory() {
        return totalMemory - usedMemory - reservedMemory;
    }

    public int calculateTransferTime(Process process) {
        return (int) Math.ceil((double) process.getMemoryRequired() / diskTransferRate);
    }

    /**
     * Determines which processes need to be evicted to free enough memory for the given process.
     * Skips processes that are currently RUNNING or LOADING (cannot be evicted).
     * Returns the list of processes to evict (in LRU order) and the total time needed to save them to disk.
     */
    public EvictionResult planEviction(Process processToLoad) {
        int memoryNeeded = processToLoad.getMemoryRequired() - getFreeMemory();

        if (memoryNeeded <= 0) {
            return new EvictionResult(new ArrayList<>(), 0);
        }

        List<Map.Entry<String, Integer>> sortedByLRU = new ArrayList<>(lastUsedTime.entrySet());
        sortedByLRU.sort(Map.Entry.comparingByValue());

        List<Process> toEvict = new ArrayList<>();
        int freedMemory = 0;
        int totalSaveTime = 0;

        for (Map.Entry<String, Integer> entry : sortedByLRU) {
            if (freedMemory >= memoryNeeded) {
                break;
            }

            Process candidate = loadedProcesses.get(entry.getKey());
            if (candidate == null) {
                continue; // Defensive: skip if not found
            }

            // Don't evict processes that are currently running or being loaded
            ProcessState state = candidate.getState();
            if (state == ProcessState.RUNNING || state == ProcessState.LOADING) {
                continue;
            }

            toEvict.add(candidate);
            freedMemory += candidate.getMemoryRequired();
            totalSaveTime += calculateTransferTime(candidate);
        }

        assert freedMemory >= memoryNeeded
                : "Cannot free enough memory even by evicting all eligible processes. Needed: "
                + memoryNeeded + ", Can free: " + freedMemory;

        return new EvictionResult(toEvict, totalSaveTime);
    }

    /**
     * Checks whether enough memory can be freed (by evicting eligible processes)
     * to load the given process. This considers currently used memory, reserved memory,
     * and only evictable processes (not RUNNING or LOADING).
     */
    public boolean canFreeEnoughMemory(Process processToLoad) {
        int memoryNeeded = processToLoad.getMemoryRequired() - getFreeMemory();
        if (memoryNeeded <= 0) {
            return true; // Already enough free memory
        }

        int evictableMemory = 0;
        for (Process candidate : loadedProcesses.values()) {
            ProcessState state = candidate.getState();
            if (state != ProcessState.RUNNING && state != ProcessState.LOADING) {
                evictableMemory += candidate.getMemoryRequired();
            }
        }

        return evictableMemory >= memoryNeeded;
    }

    public int getTotalMemory() {
        return totalMemory;
    }

    public int getUsedMemory() {
        return usedMemory;
    }

    public static class EvictionResult {
        private final List<Process> processesToEvict;
        private final int totalSaveTime;

        public EvictionResult(List<Process> processesToEvict, int totalSaveTime) {
            this.processesToEvict = processesToEvict;
            this.totalSaveTime = totalSaveTime;
        }

        public List<Process> getProcessesToEvict() {
            return processesToEvict;
        }

        public int getTotalSaveTime() {
            return totalSaveTime;
        }
    }
}
