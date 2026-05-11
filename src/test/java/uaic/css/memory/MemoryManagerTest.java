package uaic.css.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;
import uaic.css.model.simulation.EvictionResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryManagerTest {

    private MemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        memoryManager = new MemoryManager(100, 10);
    }

    // ── Constructor validation ─────────────────────────────────────────────────

    @Test
    void constructor_zeroTotalMemory_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(0, 10));
    }

    @Test
    void constructor_negativeTotalMemory_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(-1, 10));
    }

    @Test
    void constructor_zeroDiskTransferRate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(100, 0));
    }

    @Test
    void constructor_negativeDiskTransferRate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(100, -1));
    }

    @Test
    void constructor_validParams_initialFreeMemoryEqualsTotalMemory() {
        MemoryManager mm = new MemoryManager(50, 5);
        assertEquals(50, mm.getFreeMemory());
        assertEquals(50, mm.getTotalMemory());
        assertEquals(0, mm.getUsedMemory());
    }

    // ── reserveSpace ───────────────────────────────────────────────────────────

    @Test
    void reserveSpace_amountGreaterThanFree_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> memoryManager.reserveSpace(101));
    }

    @Test
    void reserveSpace_validAmount_reducesFreeMemory() {
        memoryManager.reserveSpace(30);
        assertEquals(70, memoryManager.getFreeMemory());
    }

    @Test
    void reserveSpace_exactlyFreeMemory_succeeds() {
        memoryManager.reserveSpace(100);
        assertEquals(0, memoryManager.getFreeMemory());
    }

    // ── commitLoad ─────────────────────────────────────────────────────────────

    @Test
    void commitLoad_normalFlow_movesReservedToUsed() {
        Process process = mockProcess("P1", 30);

        memoryManager.reserveSpace(30);
        assertEquals(70, memoryManager.getFreeMemory());

        memoryManager.commitLoad(process, 10);
        // reserved decreases by 30, used increases by 30 -> free stays same
        assertEquals(70, memoryManager.getFreeMemory());
        assertEquals(30, memoryManager.getUsedMemory());
        assertTrue(memoryManager.isLoaded(process));
    }

    @Test
    void commitLoad_alreadyLoaded_throwsIllegalStateException() {
        Process process = mockProcess("P1", 30);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(process, 10);

        assertThrows(IllegalStateException.class, () -> memoryManager.commitLoad(process, 20));
    }

    // ── unloadProcess ──────────────────────────────────────────────────────────

    @Test
    void unloadProcess_notLoaded_throwsIllegalStateException() {
        Process process = mockProcess("P1", 30);
        assertThrows(IllegalStateException.class, () -> memoryManager.unloadProcess(process));
    }

    @Test
    void unloadProcess_loaded_freesMemory() {
        Process process = mockProcess("P1", 30);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(process, 10);

        memoryManager.unloadProcess(process);
        assertEquals(100, memoryManager.getFreeMemory());
        assertFalse(memoryManager.isLoaded(process));
    }

    // ── updateLastUsedTime ─────────────────────────────────────────────────────

    @Test
    void updateLastUsedTime_notLoaded_silentNoOp() {
        Process process = mockProcess("P1", 30);
        // Should not throw
        assertDoesNotThrow(() -> memoryManager.updateLastUsedTime(process, 100));
    }

    @Test
    void updateLastUsedTime_loaded_updatesInternally() {
        Process process = mockProcess("P1", 30);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(process, 10);

        // No exception, and process remains loaded
        assertDoesNotThrow(() -> memoryManager.updateLastUsedTime(process, 50));
        assertTrue(memoryManager.isLoaded(process));
    }

    // ── calculateTransferTime ──────────────────────────────────────────────────

    @Test
    void calculateTransferTime_memory1Rate1_returns1() {
        MemoryManager mm = new MemoryManager(100, 1);
        Process p = mockProcess("P", 1);
        assertEquals(1, mm.calculateTransferTime(p));
    }

    @Test
    void calculateTransferTime_memory1Rate10_returns1Ceil() {
        MemoryManager mm = new MemoryManager(100, 10);
        Process p = mockProcess("P", 1);
        assertEquals(1, mm.calculateTransferTime(p));
    }

    @Test
    void calculateTransferTime_memory11Rate10_returns2() {
        MemoryManager mm = new MemoryManager(100, 10);
        Process p = mockProcess("P", 11);
        assertEquals(2, mm.calculateTransferTime(p));
    }

    @Test
    void calculateTransferTime_memory20Rate10_returns2() {
        MemoryManager mm = new MemoryManager(100, 10);
        Process p = mockProcess("P", 20);
        assertEquals(2, mm.calculateTransferTime(p));
    }

    @Test
    void calculateTransferTime_memory21Rate10_returns3() {
        MemoryManager mm = new MemoryManager(100, 10);
        Process p = mockProcess("P", 21);
        assertEquals(3, mm.calculateTransferTime(p));
    }

    // ── planEviction: free memory already sufficient ───────────────────────────

    @Test
    void planEviction_freeMemorySufficient_returnsEmptyListAndZeroSaveTime() {
        // 100 free, process needs 30
        Process processToLoad = mockProcess("PLoad", 30);
        EvictionResult result = memoryManager.planEviction(processToLoad);
        assertTrue(result.processesToEvict().isEmpty());
        assertEquals(0, result.totalSaveTime());
    }

    // ── planEviction: one candidate evictable ──────────────────────────────────

    @Test
    void planEviction_oneCandidateEvictable_returnsIt() {
        // Fill memory: load a 80-mem process, then try to load 30-mem process (need 10 more)
        Process loaded = mockProcess("PLoaded", 80);
        when(loaded.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(80);
        memoryManager.commitLoad(loaded, 0);
        // Free memory = 20

        Process toLoad = mockProcess("PNew", 30);
        EvictionResult result = memoryManager.planEviction(toLoad);

        assertEquals(1, result.processesToEvict().size());
        assertSame(loaded, result.processesToEvict().get(0));
        assertTrue(result.totalSaveTime() > 0);
    }

    // ── planEviction: all candidates RUNNING or LOADING ────────────────────────

    @Test
    void planEviction_allCandidatesRunningOrLoading_throwsIllegalStateException() {
        Process running = mockProcess("PRunning", 80);
        when(running.getState()).thenReturn(ProcessState.RUNNING);
        memoryManager.reserveSpace(80);
        memoryManager.commitLoad(running, 0);
        // Free = 20

        Process toLoad = mockProcess("PNew", 30);
        assertThrows(IllegalStateException.class, () -> memoryManager.planEviction(toLoad));
    }

    // ── planEviction: mixed — evict only eligible, skip RUNNING/LOADING ────────

    @Test
    void planEviction_mixed_skipsRunningAndLoading() {
        // Load 3 processes: one RUNNING (40), one READY (30), one READY (20)
        Process pRunning = mockProcess("PRunning", 40);
        lenient().when(pRunning.getState()).thenReturn(ProcessState.RUNNING);
        memoryManager.reserveSpace(40);
        memoryManager.commitLoad(pRunning, 0);

        Process pReady1 = mockProcess("PReady1", 30);
        lenient().when(pReady1.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(pReady1, 1);

        Process pReady2 = mockProcess("PReady2", 20);
        lenient().when(pReady2.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(20);
        memoryManager.commitLoad(pReady2, 2);
        // Free = 100 - 90 = 10

        // Need to load 20-mem process -> need 10 more
        Process toLoad = mockProcess("PNew", 20);
        EvictionResult result = memoryManager.planEviction(toLoad);

        // Should evict pReady1 (LRU, lastUsed=1) and not pRunning
        assertFalse(result.processesToEvict().contains(pRunning));
        assertTrue(result.processesToEvict().contains(pReady1));
    }

    // ── planEviction: LRU ordering ─────────────────────────────────────────────

    @Test
    void planEviction_evictsInLRUOrder() {
        // Load two processes with different last-used times
        Process pOld = mockProcess("POld", 30);
        lenient().when(pOld.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(pOld, 5); // last used at t=5

        Process pNew = mockProcess("PNew", 30);
        lenient().when(pNew.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(pNew, 10); // last used at t=10
        // Free = 40

        // Need to load 50 -> need 10 more => evict one process (the LRU one)
        Process toLoad = mockProcess("PLoad", 50);
        EvictionResult result = memoryManager.planEviction(toLoad);

        assertEquals(1, result.processesToEvict().size());
        assertSame(pOld, result.processesToEvict().get(0)); // LRU (t=5) evicted first
    }

    @Test
    void planEviction_updatedLastUsedTimesFlipOrder() {
        Process pA = mockProcess("PA", 30);
        lenient().when(pA.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(pA, 5); // initially older

        Process pB = mockProcess("PB", 30);
        lenient().when(pB.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(30);
        memoryManager.commitLoad(pB, 10);
        // Free = 40

        // Update pA to be more recent
        memoryManager.updateLastUsedTime(pA, 20);
        // Now pB (t=10) is LRU, pA (t=20) is MRU

        Process toLoad = mockProcess("PLoad", 50);
        EvictionResult result = memoryManager.planEviction(toLoad);

        assertEquals(1, result.processesToEvict().size());
        assertSame(pB, result.processesToEvict().get(0)); // pB is now LRU
    }

    // ── canFreeEnoughMemory ────────────────────────────────────────────────────

    @Test
    void canFreeEnoughMemory_alreadyEnoughFree_returnsTrue() {
        Process p = mockProcess("P", 50);
        assertTrue(memoryManager.canFreeEnoughMemory(p));
    }

    @Test
    void canFreeEnoughMemory_allLoadedRunning_returnsFalse() {
        Process pRunning = mockProcess("PRunning", 80);
        when(pRunning.getState()).thenReturn(ProcessState.RUNNING);
        memoryManager.reserveSpace(80);
        memoryManager.commitLoad(pRunning, 0);
        // Free = 20

        Process toLoad = mockProcess("PLoad", 30);
        assertFalse(memoryManager.canFreeEnoughMemory(toLoad));
    }

    @Test
    void canFreeEnoughMemory_enoughEvictable_returnsTrue() {
        Process pReady = mockProcess("PReady", 80);
        when(pReady.getState()).thenReturn(ProcessState.READY);
        memoryManager.reserveSpace(80);
        memoryManager.commitLoad(pReady, 0);
        // Free = 20

        Process toLoad = mockProcess("PLoad", 30);
        assertTrue(memoryManager.canFreeEnoughMemory(toLoad));
    }

    // ── isLoaded ───────────────────────────────────────────────────────────────

    @Test
    void isLoaded_notLoaded_returnsFalse() {
        Process p = mockProcess("P", 10);
        assertFalse(memoryManager.isLoaded(p));
    }

    @Test
    void isLoaded_afterCommitLoad_returnsTrue() {
        Process p = mockProcess("P", 10);
        memoryManager.reserveSpace(10);
        memoryManager.commitLoad(p, 0);
        assertTrue(memoryManager.isLoaded(p));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private Process mockProcess(String name, int memoryRequired) {
        Process p = mock(Process.class);
        lenient().when(p.getName()).thenReturn(name);
        lenient().when(p.getMemoryRequired()).thenReturn(memoryRequired);
        return p;
    }
}
