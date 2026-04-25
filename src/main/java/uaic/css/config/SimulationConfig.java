package uaic.css.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SimulationConfig(int processors, int memorySize, int timeSlice, int systemProcessPeriod,
        int diskTransferRate, List<ProcessConfig> processes) {
    @JsonCreator
    public SimulationConfig(
            @JsonProperty("processors") int processors,
            @JsonProperty("memorySize") int memorySize,
            @JsonProperty("timeSlice") int timeSlice,
            @JsonProperty("systemProcessPeriod") int systemProcessPeriod,
            @JsonProperty("diskTransferRate") int diskTransferRate,
            @JsonProperty("processes") List<ProcessConfig> processes) {
        if (processors <= 0) {
            throw new IllegalArgumentException("Number of processors must be positive, got: " + processors);
        }
        if (memorySize <= 0) {
            throw new IllegalArgumentException("Memory size must be positive, got: " + memorySize);
        }
        if (timeSlice <= 0) {
            throw new IllegalArgumentException("Time slice must be positive, got: " + timeSlice);
        }
        if (systemProcessPeriod <= 0) {
            throw new IllegalArgumentException("System process period must be positive, got: " + systemProcessPeriod);
        }
        if (diskTransferRate <= 0) {
            throw new IllegalArgumentException("Disk transfer rate must be positive, got: " + diskTransferRate);
        }
        if (processes == null || processes.isEmpty()) {
            throw new IllegalArgumentException("Process list must not be null or empty");
        }
        for (ProcessConfig process : processes) {
            if (process.memoryRequired() > memorySize) {
                throw new IllegalArgumentException("Process '" + process.name()
                        + "' requires " + process.memoryRequired()
                        + " memory, but total system memory is only " + memorySize);
            }
        }

        this.processors = processors;
        this.memorySize = memorySize;
        this.timeSlice = timeSlice;
        this.systemProcessPeriod = systemProcessPeriod;
        this.diskTransferRate = diskTransferRate;
        this.processes = processes;
    }
}
