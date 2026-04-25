package uaic.css.output;

import uaic.css.model.system.SimulationResult;
import uaic.css.model.system.ExecutionLogEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class TextOutputWriter {

    public void write(SimulationResult result, String filePath) {
        assert result != null : "SimulationResult must not be null";
        assert filePath != null && !filePath.isEmpty() : "File path must not be null or empty";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("=== Process Scheduling Simulation Results ===");
            writer.println("Total simulation time: " + result.totalTime());
            writer.println();

            // Sort entries by start time for chronological output
            List<ExecutionLogEntry> sorted = new ArrayList<>(result.logEntries());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(a.startTime(), b.startTime());
                if (cmp != 0) return cmp;
                return Integer.compare(a.processorId(), b.processorId());
            });

            // Chronological event log
            writer.println("--- Chronological Log ---");
            for (ExecutionLogEntry entry : sorted) {
                String location;
                if (entry.processorId() == ExecutionLogEntry.DISK_PROCESSOR_ID) {
                    location = "Disk";
                } else {
                    location = "Processor " + entry.processorId();
                }

                writer.printf("[T=%d -> T=%d] %-25s on %-15s (%s)%n",
                        entry.startTime(),
                        entry.endTime(),
                        entry.label(),
                        location,
                        entry.type());
            }

            writer.println();

            // Per-processor timeline
            writer.println("--- Per-Processor Timeline ---");
            int maxProcessorId = -1;
            for (ExecutionLogEntry entry : result.logEntries()) {
                if (entry.processorId() > maxProcessorId) {
                    maxProcessorId = entry.processorId();
                }
            }

            for (int pid = 0; pid <= maxProcessorId; pid++) {
                writer.println("Processor " + pid + ":");
                List<ExecutionLogEntry> procEntries = result.getEntriesForProcessor(pid);
                procEntries.sort((a, b) -> Integer.compare(a.startTime(), b.startTime()));
                for (ExecutionLogEntry entry : procEntries) {
                    writer.printf("  [%d-%d] %s (%s)%n",
                            entry.startTime(),
                            entry.endTime(),
                            entry.label(),
                            entry.type());
                }
                writer.println();
            }

            // Disk operations
            List<ExecutionLogEntry> diskEntries = result.getDiskEntries();
            if (!diskEntries.isEmpty()) {
                writer.println("Disk Operations:");
                diskEntries.sort((a, b) -> Integer.compare(a.startTime(), b.startTime()));
                for (ExecutionLogEntry entry : diskEntries) {
                    writer.printf("  [%d-%d] %s (%s)%n",
                            entry.startTime(),
                            entry.endTime(),
                            entry.label(),
                            entry.type());
                }
            }

            writer.println();
            writer.println("=== End of Simulation ===");

        } catch (IOException e) {
            throw new RuntimeException("Failed to write output to: " + filePath, e);
        }
    }
}
