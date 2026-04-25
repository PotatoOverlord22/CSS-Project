package uaic.css.output;

import uaic.css.engine.SimulationResult;
import uaic.css.model.system.ExecutionLogEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class TextOutputWriter {

    public static void write(SimulationResult result, String filePath) {
        assert result != null : "SimulationResult must not be null";
        assert filePath != null && !filePath.isEmpty() : "File path must not be null or empty";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("=== Process Scheduling Simulation Results ===");
            writer.println("Total simulation time: " + result.getTotalTime());
            writer.println();

            // Sort entries by start time for chronological output
            List<ExecutionLogEntry> sorted = new ArrayList<>(result.getLogEntries());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(a.getStartTime(), b.getStartTime());
                if (cmp != 0) return cmp;
                return Integer.compare(a.getProcessorId(), b.getProcessorId());
            });

            // Chronological event log
            writer.println("--- Chronological Log ---");
            for (ExecutionLogEntry entry : sorted) {
                String location;
                if (entry.getProcessorId() == -1) {
                    location = "Disk";
                } else {
                    location = "Processor " + entry.getProcessorId();
                }

                writer.printf("[T=%d -> T=%d] %-25s on %-15s (%s)%n",
                        entry.getStartTime(),
                        entry.getEndTime(),
                        entry.getLabel(),
                        location,
                        entry.getType());
            }

            writer.println();

            // Per-processor timeline
            writer.println("--- Per-Processor Timeline ---");
            int maxProcessorId = -1;
            for (ExecutionLogEntry entry : result.getLogEntries()) {
                if (entry.getProcessorId() > maxProcessorId) {
                    maxProcessorId = entry.getProcessorId();
                }
            }

            for (int pid = 0; pid <= maxProcessorId; pid++) {
                writer.println("Processor " + pid + ":");
                List<ExecutionLogEntry> procEntries = result.getEntriesForProcessor(pid);
                procEntries.sort((a, b) -> Integer.compare(a.getStartTime(), b.getStartTime()));
                for (ExecutionLogEntry entry : procEntries) {
                    writer.printf("  [%d-%d] %s (%s)%n",
                            entry.getStartTime(),
                            entry.getEndTime(),
                            entry.getLabel(),
                            entry.getType());
                }
                writer.println();
            }

            // Disk operations
            List<ExecutionLogEntry> diskEntries = result.getDiskEntries();
            if (!diskEntries.isEmpty()) {
                writer.println("Disk Operations:");
                diskEntries.sort((a, b) -> Integer.compare(a.getStartTime(), b.getStartTime()));
                for (ExecutionLogEntry entry : diskEntries) {
                    writer.printf("  [%d-%d] %s (%s)%n",
                            entry.getStartTime(),
                            entry.getEndTime(),
                            entry.getLabel(),
                            entry.getType());
                }
            }

            writer.println();
            writer.println("=== End of Simulation ===");

        } catch (IOException e) {
            throw new RuntimeException("Failed to write output to: " + filePath, e);
        }
    }
}
