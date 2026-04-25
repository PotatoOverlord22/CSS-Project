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
                if (name == null || name.isEmpty()) {
                        throw new IllegalArgumentException("Process name must not be null or empty");
                }
                if (releaseTime < 0) {
                        throw new IllegalArgumentException("Release time must be non-negative, got: " + releaseTime);
                }
                if (memoryRequired <= 0) {
                        throw new IllegalArgumentException("Memory required must be positive, got: " + memoryRequired);
                }
                if (executionSequence == null || executionSequence.isEmpty()) {
                        throw new IllegalArgumentException("Execution sequence must not be null or empty");
                }
                if (executionSequence.size() % 2 != 1) {
                        throw new IllegalArgumentException(
                                        "Execution sequence must have an odd number of elements (alternating CPU bursts and syscalls), got: "
                                                        + executionSequence.size());
                }

                this.name = name;
                this.releaseTime = releaseTime;
                this.memoryRequired = memoryRequired;
                this.executionSequence = executionSequence;
        }
}
