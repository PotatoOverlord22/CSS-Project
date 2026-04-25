package uaic.css.model.system;

import uaic.css.model.process.Process;

import java.util.List;

/**
 * Represents the result of planning a memory eviction.
 * Contains the list of processes to evict and the total time needed to save them to disk.
 */
public record EvictionResult(List<Process> processesToEvict, int totalSaveTime) {
}
