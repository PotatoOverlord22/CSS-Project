package uaic.css;

import uaic.css.config.InputParser;
import uaic.css.config.SimulationConfig;
import uaic.css.engine.EventDrivenSimulationEngine;
import uaic.css.engine.SimulationEngine;
import uaic.css.engine.SimulationResult;
import uaic.css.model.process.Process;
import uaic.css.output.TextOutputWriter;
import uaic.css.ui.GanttChartPanel;

import java.util.ArrayList;
import java.util.List;

public class ApplicationOrchestrator {

    public void run(String inputFilePath) {
        // 1. Parse input
        System.out.println("Parsing configuration from: " + inputFilePath);
        SimulationConfig config = InputParser.parse(inputFilePath);

        // 2. Build process list from config
        List<Process> processes = new ArrayList<>();
        for (var pc : config.getProcesses()) {
            processes.add(new Process(pc));
        }

        System.out.println("Configuration loaded:");
        System.out.println("  Processors: " + config.getProcessors());
        System.out.println("  Memory: " + config.getMemorySize());
        System.out.println("  Time Slice: " + config.getTimeSlice());
        System.out.println("  System Process Period: " + config.getSystemProcessPeriod());
        System.out.println("  Disk Transfer Rate: " + config.getDiskTransferRate());
        System.out.println("  Processes: " + processes.size());

        // 3. Run simulation
        System.out.println("\nRunning simulation...");
        SimulationEngine engine = new EventDrivenSimulationEngine();
        SimulationResult result = engine.run(config, processes);
        System.out.println("Simulation complete. Total time: " + result.getTotalTime());

        // 4. Write text output
        String outputPath = "output.txt";
        System.out.println("Writing text output to: " + outputPath);
        TextOutputWriter.write(result, outputPath);

        // 5. Show Gantt chart
        System.out.println("Displaying Gantt chart...");
        GanttChartPanel.display(result, config.getProcessors());
    }
}
