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
        assert processors > 0 : "Number of processors must be positive, got: " + processors;
        assert memorySize > 0 : "Memory size must be positive, got: " + memorySize;
        assert timeSlice > 0 : "Time slice must be positive, got: " + timeSlice;
        assert systemProcessPeriod > 0 : "System process period must be positive, got: " + systemProcessPeriod;
        assert diskTransferRate > 0 : "Disk transfer rate must be positive, got: " + diskTransferRate;
        assert processes != null && !processes.isEmpty() : "Process list must not be null or empty";

        this.processors = processors;
        this.memorySize = memorySize;
        this.timeSlice = timeSlice;
        this.systemProcessPeriod = systemProcessPeriod;
        this.diskTransferRate = diskTransferRate;
        this.processes = processes;
    }
}
