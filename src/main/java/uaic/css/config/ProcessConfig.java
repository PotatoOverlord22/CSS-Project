package uaic.css.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ProcessConfig {
    private final String name;
    private final int releaseTime;
    private final int memoryRequired;
    private final List<Integer> executionSequence;

    @JsonCreator
    public ProcessConfig(
            @JsonProperty("name") String name,
            @JsonProperty("releaseTime") int releaseTime,
            @JsonProperty("memoryRequired") int memoryRequired,
            @JsonProperty("executionSequence") List<Integer> executionSequence) {
        this.name = name;
        this.releaseTime = releaseTime;
        this.memoryRequired = memoryRequired;
        this.executionSequence = executionSequence;
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

    public List<Integer> getExecutionSequence() {
        return executionSequence;
    }
}
