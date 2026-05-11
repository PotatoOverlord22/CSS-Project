package uaic.css.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InputParserTest {

    private InputParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new InputParser();
    }

    // ── Valid JSON file ─────────────────────────────────────────────────────────

    @Test
    void parse_validJsonFile_returnsCorrectSimulationConfig() throws IOException {
        String json = """
                {
                  "processors": 2,
                  "memorySize": 100,
                  "timeSlice": 4,
                  "systemProcessPeriod": 20,
                  "diskTransferRate": 10,
                  "processes": [
                    {
                      "name": "P1",
                      "releaseTime": 0,
                      "memoryRequired": 30,
                      "executionSequence": [5, 2, 3]
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("valid.json");
        Files.writeString(file, json);

        SimulationConfig config = parser.parse(file.toString());

        assertEquals(2, config.processors());
        assertEquals(100, config.memorySize());
        assertEquals(4, config.timeSlice());
        assertEquals(20, config.systemProcessPeriod());
        assertEquals(10, config.diskTransferRate());
        assertEquals(1, config.processes().size());

        ProcessConfig process = config.processes().get(0);
        assertEquals("P1", process.name());
        assertEquals(0, process.releaseTime());
        assertEquals(30, process.memoryRequired());
        assertEquals(java.util.List.of(5, 2, 3), process.executionSequence());
    }

    @Test
    void parse_validJsonMultipleProcesses_returnsAllProcessConfigs() throws IOException {
        String json = """
                {
                  "processors": 2,
                  "memorySize": 100,
                  "timeSlice": 4,
                  "systemProcessPeriod": 20,
                  "diskTransferRate": 10,
                  "processes": [
                    {
                      "name": "P1",
                      "releaseTime": 0,
                      "memoryRequired": 30,
                      "executionSequence": [5, 2, 3]
                    },
                    {
                      "name": "P2",
                      "releaseTime": 2,
                      "memoryRequired": 50,
                      "executionSequence": [8, 3, 4]
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("multi.json");
        Files.writeString(file, json);

        SimulationConfig config = parser.parse(file.toString());
        assertEquals(2, config.processes().size());
        assertEquals("P1", config.processes().get(0).name());
        assertEquals("P2", config.processes().get(1).name());
    }

    // ── Missing required field ─────────────────────────────────────────────────

    @Test
    void parse_missingProcessorsField_throwsRuntimeException() throws IOException {
        String json = """
                {
                  "memorySize": 100,
                  "timeSlice": 4,
                  "systemProcessPeriod": 20,
                  "diskTransferRate": 10,
                  "processes": [
                    {
                      "name": "P1",
                      "releaseTime": 0,
                      "memoryRequired": 30,
                      "executionSequence": [5]
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("missing.json");
        Files.writeString(file, json);

        // processors defaults to 0 -> SimulationConfig constructor throws
        assertThrows(RuntimeException.class, () -> parser.parse(file.toString()));
    }

    // ── File does not exist ────────────────────────────────────────────────────

    @Test
    void parse_nonExistentFile_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parser.parse("/non/existent/path.json"));
    }

    // ── Malformed JSON ─────────────────────────────────────────────────────────

    @Test
    void parse_malformedJson_throwsRuntimeException() throws IOException {
        String json = "{ this is not valid json }}}";
        Path file = tempDir.resolve("malformed.json");
        Files.writeString(file, json);

        assertThrows(RuntimeException.class, () -> parser.parse(file.toString()));
    }

    // ── Null/empty file path ───────────────────────────────────────────────────

    @Test
    void parse_nullFilePath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void parse_emptyFilePath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    // ── Constructor validation ─────────────────────────────────────────────────

    @Test
    void constructor_nullObjectMapper_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new InputParser(null));
    }

    // ── Invalid process config in JSON ─────────────────────────────────────────

    @Test
    void parse_processMemoryExceedsTotal_throwsRuntimeException() throws IOException {
        String json = """
                {
                  "processors": 2,
                  "memorySize": 50,
                  "timeSlice": 4,
                  "systemProcessPeriod": 20,
                  "diskTransferRate": 10,
                  "processes": [
                    {
                      "name": "P1",
                      "releaseTime": 0,
                      "memoryRequired": 100,
                      "executionSequence": [5]
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("exceed.json");
        Files.writeString(file, json);

        // SimulationConfig constructor rejects process memory > total
        assertThrows(RuntimeException.class, () -> parser.parse(file.toString()));
    }
}
