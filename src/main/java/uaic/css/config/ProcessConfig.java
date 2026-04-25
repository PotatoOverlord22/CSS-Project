package uaic.css.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ProcessConfig(String name, int releaseTime, int memoryRequired, List<Integer> executionSequence) {
    @JsonCreator
    public ProcessConfig(
            @JsonProperty("name") String name,
            @JsonProperty("releaseTime") int releaseTime,
            @JsonProperty("memoryRequired") int memoryRequired,
            @JsonProperty("executionSequence") List<Integer> executionSequence) {
        assert name != null && !name.isEmpty() : "Process name must not be null or empty";
        assert releaseTime >= 0 : "Release time must be non-negative, got: " + releaseTime;
        assert memoryRequired > 0 : "Memory required must be positive, got: " + memoryRequired;
        assert executionSequence != null && !executionSequence.isEmpty()
                : "Execution sequence must not be null or empty";
        assert executionSequence.size() % 2 == 1
                : "Execution sequence must have an odd number of elements (alternating CPU bursts and syscalls), got: "
                + executionSequence.size();

        this.name = name;
        this.releaseTime = releaseTime;
        this.memoryRequired = memoryRequired;
        this.executionSequence = executionSequence;
    }
}
