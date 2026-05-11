package uaic.css.model.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uaic.css.model.process.Process;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorTest {

    private Processor processor;

    @BeforeEach
    void setUp() {
        processor = new Processor(0);
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    void isFree_initially_returnsTrue() {
        assertTrue(processor.isFree());
    }

    @Test
    void getCurrentProcess_initially_returnsNull() {
        assertNull(processor.getCurrentProcess());
    }

    @Test
    void isBusyWithSystemProcess_initially_returnsFalse() {
        assertFalse(processor.isBusyWithSystemProcess());
    }

    @Test
    void getId_returnsConstructorValue() {
        Processor p = new Processor(5);
        assertEquals(5, p.getId());
    }

    // ── Assign a process ───────────────────────────────────────────────────────

    @Test
    void setCurrentProcess_assignProcess_isFreeReturnsFalse() {
        Process process = new Process("P1", 0, 10, List.of(5));
        processor.setCurrentProcess(process);
        assertFalse(processor.isFree());
    }

    @Test
    void setCurrentProcess_assignProcess_getCurrentProcessReturnsIt() {
        Process process = new Process("P1", 0, 10, List.of(5));
        processor.setCurrentProcess(process);
        assertSame(process, processor.getCurrentProcess());
    }

    // ── Release a process ──────────────────────────────────────────────────────

    @Test
    void setCurrentProcess_releaseProcess_isFreeReturnsTrue() {
        Process process = new Process("P1", 0, 10, List.of(5));
        processor.setCurrentProcess(process);
        processor.setCurrentProcess(null);
        assertTrue(processor.isFree());
    }

    // ── System process occupancy ───────────────────────────────────────────────

    @Test
    void setBusyWithSystemProcess_true_isFreeReturnsFalse() {
        processor.setBusyWithSystemProcess(true);
        assertFalse(processor.isFree());
    }

    @Test
    void setBusyWithSystemProcess_false_isFreeReturnsTrue() {
        processor.setBusyWithSystemProcess(true);
        processor.setBusyWithSystemProcess(false);
        assertTrue(processor.isFree());
    }

    @Test
    void systemProcessFlag_independentOfUserProcess() {
        Process process = new Process("P1", 0, 10, List.of(5));
        processor.setCurrentProcess(process);
        processor.setBusyWithSystemProcess(true);

        // Even if system process flag clears, user process still occupies
        processor.setBusyWithSystemProcess(false);
        assertFalse(processor.isFree());

        // Even if user process clears, system process flag still occupies
        processor.setCurrentProcess(null);
        processor.setBusyWithSystemProcess(true);
        assertFalse(processor.isFree());
    }

    // ── Double-assign without release ──────────────────────────────────────────
    // Note: Currently the invariant "don't assign to an occupied processor" is
    // enforced at a higher level (EventDrivenSimulationEngine.dispatchProcessOnProcessor
    // checks isFree() before calling setCurrentProcess). This test asserts that
    // the Processor itself should also guard against misuse at the setter level.
    // We may remove this test as there's reason for debate whether to keep the setter dumb or not

    @Test
    void setCurrentProcess_doubleAssignWithoutRelease_throwsIllegalStateException() {
        Process p1 = new Process("P1", 0, 10, List.of(5));
        Process p2 = new Process("P2", 0, 10, List.of(5));
        processor.setCurrentProcess(p1);
        assertThrows(IllegalStateException.class, () -> processor.setCurrentProcess(p2));
    }

    // ── toString ───────────────────────────────────────────────────────────────

    @Test
    void toString_returnsReadableFormat() {
        Processor p = new Processor(3);
        assertEquals("Processor 3", p.toString());
    }
}
