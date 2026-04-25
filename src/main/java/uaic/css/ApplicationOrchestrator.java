package uaic.css;

import uaic.css.config.InputParser;
import uaic.css.config.SimulationConfig;
import uaic.css.engine.EventDrivenSimulationEngine;
import uaic.css.engine.SimulationEngine;
import uaic.css.model.simulation.SimulationResult;
import uaic.css.model.process.Process;
import uaic.css.output.TextOutputWriter;
import uaic.css.ui.GanttChartPanel;

import java.util.ArrayList;
import java.util.List;

public class ApplicationOrchestrator {

    private final InputParser inputParser;
    private final TextOutputWriter outputWriter;

    public ApplicationOrchestrator() {
        this.inputParser = new InputParser();
        this.outputWriter = new TextOutputWriter();
    }

    public void run(String inputFilePath) {
        // 1. Parse input
        System.out.println("Parsing configuration from: " + inputFilePath);
        SimulationConfig config = inputParser.parse(inputFilePath);

        // 2. Build process list from config
        List<Process> processes = new ArrayList<>();
        for (var pc : config.processes()) {
            processes.add(new Process(
                    pc.name(),
                    pc.releaseTime(),
                    pc.memoryRequired(),
                    pc.executionSequence()
            ));
        }

        System.out.println("Configuration loaded:");
        System.out.println("  Processors: " + config.processors());
        System.out.println("  Memory: " + config.memorySize());
        System.out.println("  Time Slice: " + config.timeSlice());
        System.out.println("  System Process Period: " + config.systemProcessPeriod());
        System.out.println("  Disk Transfer Rate: " + config.diskTransferRate());
        System.out.println("  Processes: " + processes.size());

        // 3. Run simulation
        System.out.println("\nRunning simulation...");
        SimulationEngine engine = new EventDrivenSimulationEngine();
        SimulationResult result = engine.run(config, processes);
        System.out.println("Simulation complete. Total time: " + result.totalTime());

        // 4. Write text output
        String outputPath = "output.txt";
        System.out.println("Writing text output to: " + outputPath);
        outputWriter.write(result, outputPath);

        // 5. Show Gantt chart
        System.out.println("Displaying Gantt chart...");
        GanttChartPanel ganttChart = new GanttChartPanel(result, config.processors());
        ganttChart.display();
    }
}
