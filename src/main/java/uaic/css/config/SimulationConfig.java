package uaic.css.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SimulationConfig {
    private final int processors;
    private final int memorySize;
    private final int timeSlice;
    private final int systemProcessPeriod;
    private final int diskTransferRate;
    private final List<ProcessConfig> processes;

    @JsonCreator
    public SimulationConfig(
            @JsonProperty("processors") int processors,
            @JsonProperty("memorySize") int memorySize,
            @JsonProperty("timeSlice") int timeSlice,
            @JsonProperty("systemProcessPeriod") int systemProcessPeriod,
            @JsonProperty("diskTransferRate") int diskTransferRate,
            @JsonProperty("processes") List<ProcessConfig> processes) {
        this.processors = processors;
        this.memorySize = memorySize;
        this.timeSlice = timeSlice;
        this.systemProcessPeriod = systemProcessPeriod;
        this.diskTransferRate = diskTransferRate;
        this.processes = processes;
    }

    public int getProcessors() {
        return processors;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    public int getSystemProcessPeriod() {
        return systemProcessPeriod;
    }

    public int getDiskTransferRate() {
        return diskTransferRate;
    }

    public List<ProcessConfig> getProcesses() {
        return processes;
    }
}
