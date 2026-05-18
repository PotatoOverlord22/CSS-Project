package uaic.css.model.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTest {

    // ── Constructor rejection cases ────────────────────────────────────────────

    @Test
    void constructor_nullName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process(null, 0, 10, List.of(5)));
    }

    @Test
    void constructor_emptyName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("", 0, 10, List.of(5)));
    }

    @Test
    void constructor_negativeReleaseTime_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", -1, 10, List.of(5)));
    }

    @Test
    void constructor_zeroMemory_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, 0, List.of(5)));
    }

    @Test
    void constructor_negativeMemory_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, -1, List.of(5)));
    }

    @Test
    void constructor_nullSequence_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, 10, null));
    }

    @Test
    void constructor_evenSizeSequence_throwsIllegalArgumentException() {
        // [5, 2] has 2 elements (even) -> must be odd
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, 10, List.of(5, 2)));
    }

    @Test
    void constructor_sequenceContainingZero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, 10, List.of(5, 0, 3)));
    }

    @Test
    void constructor_sequenceContainingNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Process("P1", 0, 10, List.of(5, -1, 3)));
    }

    // ── Valid path: single burst, no syscall ───────────────────────────────────

    @Test
    void constructor_singleBurstNoSyscall_hasSyscallAfterCurrentBurstReturnsFalse() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertFalse(p.hasSyscallAfterCurrentBurst());
        assertEquals(List.of(5), p.getCpuBursts());
        assertTrue(p.getSyscallDurations().isEmpty());
        assertEquals(5, p.getRemainingBurstTime());
        assertTrue(p.hasMoreBursts());
    }

    // ── Valid path: burst, syscall, burst ──────────────────────────────────────

    @Test
    void constructor_burstSyscallBurst_accessorsReturnCorrectValues() {
        Process p = new Process("P1", 0, 10, List.of(5, 2, 3));
        assertTrue(p.hasSyscallAfterCurrentBurst());
        assertEquals(List.of(5, 3), p.getCpuBursts());
        assertEquals(List.of(2), p.getSyscallDurations());
        assertEquals(5, p.getRemainingBurstTime());
        assertEquals(2, p.getCurrentSyscallDuration());
    }

    // ── getCurrentSyscallDuration when no syscall ──────────────────────────────

    @Test
    void getCurrentSyscallDuration_noSyscallAfterCurrentBurst_throwsAssertionError() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertThrows(AssertionError.class, p::getCurrentSyscallDuration);
    }

    // ── advanceToNextBurst ─────────────────────────────────────────────────────

    @Test
    void advanceToNextBurst_fromLastBurst_hasMoreBurstsReturnsFalse() {
        Process p = new Process("P1", 0, 10, List.of(5));
        // single burst: burstIndex=0, advance -> burstIndex=1
        p.advanceToNextBurst();
        assertFalse(p.hasMoreBursts());
    }

    @Test
    void advanceToNextBurst_moreBurstsExist_updatesRemainingBurstTime() {
        Process p = new Process("P1", 0, 10, List.of(5, 2, 3));
        assertEquals(5, p.getRemainingBurstTime());
        p.advanceToNextBurst();
        assertTrue(p.hasMoreBursts());
        assertEquals(3, p.getRemainingBurstTime());
    }

    @Test
    void advanceToNextBurst_fromLastBurst_remainingBurstTimeUnchanged() {
        Process p = new Process("P1", 0, 10, List.of(5));
        int beforeAdvance = p.getRemainingBurstTime();
        p.advanceToNextBurst();
        // remainingBurstTime stays as-is since hasMoreBursts is now false
        assertEquals(beforeAdvance, p.getRemainingBurstTime());
    }

    // ── setRemainingBurstTime with negative ────────────────────────────────────

    //failing test solved by assertion
    @Test
    void setRemainingBurstTime_negativeValue_throwsAssertionError() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertThrows(AssertionError.class, () -> p.setRemainingBurstTime(-1));
    }

    // ── equals and hashCode ────────────────────────────────────────────────────

    @Test
    void equals_sameNameDifferentFields_returnsTrue() {
        Process p1 = new Process("P1", 0, 10, List.of(5));
        Process p2 = new Process("P1", 5, 20, List.of(3, 2, 1));
        assertEquals(p1, p2);
    }

    @Test
    void equals_differentNamesSameFields_returnsFalse() {
        Process p1 = new Process("P1", 0, 10, List.of(5));
        Process p2 = new Process("P2", 0, 10, List.of(5));
        assertNotEquals(p1, p2);
    }

    @Test
    void equals_null_returnsFalse() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertNotEquals(null, p);
    }

    @Test
    void equals_otherClass_returnsFalse() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertNotEquals("P1", p);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Process p1 = new Process("P1", 0, 10, List.of(5));
        Process p2 = new Process("P1", 5, 20, List.of(3, 2, 1));
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // ── State management ───────────────────────────────────────────────────────

    @Test
    void getState_newProcess_returnsNEW() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertEquals(ProcessState.NEW, p.getState());
    }

    @Test
    void setState_changesState() {
        Process p = new Process("P1", 0, 10, List.of(5));
        p.setState(ProcessState.RUNNING);
        assertEquals(ProcessState.RUNNING, p.getState());
    }

    @Test
    void isTerminated_terminatedProcess_returnsTrue() {
        Process p = new Process("P1", 0, 10, List.of(5));
        p.setState(ProcessState.TERMINATED);
        assertTrue(p.isTerminated());
    }

    @Test
    void isTerminated_nonTerminatedProcess_returnsFalse() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertFalse(p.isTerminated());
    }

    // ── lastProcessorId ────────────────────────────────────────────────────────

    @Test
    void getLastProcessorId_initialValue_returnsMinusOne() {
        Process p = new Process("P1", 0, 10, List.of(5));
        assertEquals(-1, p.getLastProcessorId());
    }

    @Test
    void setLastProcessorId_setsCorrectly() {
        Process p = new Process("P1", 0, 10, List.of(5));
        p.setLastProcessorId(2);
        assertEquals(2, p.getLastProcessorId());
    }

    // ── Basic accessors ────────────────────────────────────────────────────────

    @Test
    void getReleaseTime_returnsConstructorValue() {
        Process p = new Process("P1", 7, 10, List.of(5));
        assertEquals(7, p.getReleaseTime());
    }

    @Test
    void getMemoryRequired_returnsConstructorValue() {
        Process p = new Process("P1", 0, 42, List.of(5));
        assertEquals(42, p.getMemoryRequired());
    }

    @Test
    void getName_returnsConstructorValue() {
        Process p = new Process("TestProc", 0, 10, List.of(5));
        assertEquals("TestProc", p.getName());
    }
}
