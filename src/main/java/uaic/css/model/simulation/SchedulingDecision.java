package uaic.css.model.simulation;

import uaic.css.model.process.Process;

/**
 * Represents a scheduling decision: which process to run on which processor.
 */
public record SchedulingDecision(Process process, Processor processor) {
}
