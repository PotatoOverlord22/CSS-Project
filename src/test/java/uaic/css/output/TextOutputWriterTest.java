package uaic.css.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uaic.css.model.simulation.EntryType;
import uaic.css.model.simulation.ExecutionLogEntry;
import uaic.css.model.simulation.SimulationResult;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextOutputWriterTest {

    private TextOutputWriter writer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        writer = new TextOutputWriter();
    }

    // ── Empty SimulationResult ─────────────────────────────────────────────────

    @Test
    void write_emptyResult_fileCreatedAndParseable() throws IOException {
        SimulationResult result = new SimulationResult(new ArrayList<>(), 0);
        Path outputFile = tempDir.resolve("output.txt");

        writer.write(result, outputFile.toString());

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("Total simulation time: 0"));
        assertTrue(content.contains("=== End of Simulation ==="));
    }

    // ── One entry per EntryType ────────────────────────────────────────────────

    @Test
    void write_oneEntryPerEntryType_eachLineMatchesExpectedFormat() {
        List<ExecutionLogEntry> entries = List.of(
                new ExecutionLogEntry("P1", 0, 0, 5, EntryType.CPU_BURST),
                new ExecutionLogEntry("SysCall(P1)", 1, 5, 8, EntryType.SYSCALL),
                new ExecutionLogEntry("Load P2", ExecutionLogEntry.DISK_PROCESSOR_ID, 0, 3, EntryType.DISK_LOAD),
                new ExecutionLogEntry("Save P3", ExecutionLogEntry.DISK_PROCESSOR_ID, 3, 6, EntryType.DISK_SAVE));

        SimulationResult result = new SimulationResult(new ArrayList<>(entries), 10);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        // Verify each entry type appears in the chronological log
        assertTrue(output.contains("CPU_BURST"));
        assertTrue(output.contains("SYSCALL"));
        assertTrue(output.contains("DISK_LOAD"));
        assertTrue(output.contains("DISK_SAVE"));

        // Verify time markers
        assertTrue(output.contains("[T=0 -> T=5]"));
        assertTrue(output.contains("[T=5 -> T=8]"));
        assertTrue(output.contains("[T=0 -> T=3]"));
        assertTrue(output.contains("[T=3 -> T=6]"));

        // Verify disk operations labeled correctly
        assertTrue(output.contains("Disk"));
        assertTrue(output.contains("Processor 0"));
        assertTrue(output.contains("Processor 1"));
    }

    // ── Total simulation time header ───────────────────────────────────────────

    @Test
    void write_nonZeroTotalTime_headerShowsCorrectTime() {
        SimulationResult result = new SimulationResult(new ArrayList<>(), 42);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        assertTrue(output.contains("Total simulation time: 42"));
    }

    // ── Null result throws ─────────────────────────────────────────────────────

    @Test
    void write_nullResultToFile_throwsIllegalArgumentException() {
        Path outputFile = tempDir.resolve("output.txt");
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(null, outputFile.toString()));
    }

    @Test
    void write_nullResultToWriter_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(null, new StringWriter()));
    }

    // ── Null/empty file path throws ────────────────────────────────────────────

    @Test
    void write_nullFilePath_throwsIllegalArgumentException() {
        SimulationResult result = new SimulationResult(new ArrayList<>(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(result, (String) null));
    }

    @Test
    void write_emptyFilePath_throwsIllegalArgumentException() {
        SimulationResult result = new SimulationResult(new ArrayList<>(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(result, ""));
    }

    // ── Null writer throws ─────────────────────────────────────────────────────

    @Test
    void write_nullWriter_throwsIllegalArgumentException() {
        SimulationResult result = new SimulationResult(new ArrayList<>(), 0);
        assertThrows(IllegalArgumentException.class,
                () -> writer.write(result, (java.io.Writer) null));
    }

    // ── Per-processor timeline section ─────────────────────────────────────────

    @Test
    void write_multipleProcessors_perProcessorTimelineContainsAllProcessors() {
        List<ExecutionLogEntry> entries = List.of(
                new ExecutionLogEntry("P1", 0, 0, 5, EntryType.CPU_BURST),
                new ExecutionLogEntry("P2", 1, 2, 7, EntryType.CPU_BURST));
        SimulationResult result = new SimulationResult(new ArrayList<>(entries), 10);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        assertTrue(output.contains("Processor 0:"));
        assertTrue(output.contains("Processor 1:"));
    }

    // ── Disk operations section ────────────────────────────────────────────────

    @Test
    void write_withDiskEntries_diskOperationsSectionPresent() {
        List<ExecutionLogEntry> entries = List.of(
                new ExecutionLogEntry("Load P1", ExecutionLogEntry.DISK_PROCESSOR_ID, 0, 3, EntryType.DISK_LOAD));
        SimulationResult result = new SimulationResult(new ArrayList<>(entries), 5);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        assertTrue(output.contains("Disk Operations:"));
        assertTrue(output.contains("Load P1"));
    }

    @Test
    void write_noDiskEntries_diskOperationsSectionAbsent() {
        List<ExecutionLogEntry> entries = List.of(
                new ExecutionLogEntry("P1", 0, 0, 5, EntryType.CPU_BURST));
        SimulationResult result = new SimulationResult(new ArrayList<>(entries), 5);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        assertFalse(output.contains("Disk Operations:"));
    }

    // ── File write creates file ────────────────────────────────────────────────

    @Test
    void write_toFilePath_createsFileWithContent() throws IOException {
        List<ExecutionLogEntry> entries = List.of(
                new ExecutionLogEntry("P1", 0, 0, 5, EntryType.CPU_BURST));
        SimulationResult result = new SimulationResult(new ArrayList<>(entries), 5);
        Path outputFile = tempDir.resolve("result.txt");

        writer.write(result, outputFile.toString());

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertFalse(content.isEmpty());
        assertTrue(content.contains("P1"));
    }

    // ── Chronological ordering ─────────────────────────────────────────────────

    @Test
    void write_entriesOutOfOrder_outputIsSortedChronologically() {
        List<ExecutionLogEntry> entries = new ArrayList<>(List.of(
                new ExecutionLogEntry("P2", 0, 5, 10, EntryType.CPU_BURST),
                new ExecutionLogEntry("P1", 0, 0, 5, EntryType.CPU_BURST)));
        SimulationResult result = new SimulationResult(entries, 10);
        StringWriter stringWriter = new StringWriter();

        writer.write(result, stringWriter);
        String output = stringWriter.toString();

        // P1 (starts at 0) should appear before P2 (starts at 5) in chronological log
        int p1Pos = output.indexOf("[T=0 -> T=5]");
        int p2Pos = output.indexOf("[T=5 -> T=10]");
        assertTrue(p1Pos < p2Pos, "P1 should appear before P2 in chronological output");
    }
}
