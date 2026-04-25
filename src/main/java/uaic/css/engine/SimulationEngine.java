package uaic.css.engine;

import uaic.css.config.SimulationConfig;
import uaic.css.model.process.Process;
import uaic.css.model.system.SimulationResult;

import java.util.List;

public interface SimulationEngine {

    /**
     * Runs the simulation with the given configuration and processes.
     *
     * @param config    the simulation parameters
     * @param processes the list of user processes to simulate
     * @return the simulation result containing all execution log entries
     */
    SimulationResult run(SimulationConfig config, List<Process> processes);
}
