package uaic.css.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uaic.css.memory.MemoryManager;
import uaic.css.model.process.Process;
import uaic.css.model.simulation.Processor;
import uaic.css.model.simulation.ReadyQueue;
import uaic.css.model.simulation.SchedulingDecision;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerTest {

    @Mock
    private ReadyQueue readyQueue;

    @Mock
    private MemoryManager memoryManager;

    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new Scheduler(readyQueue);
    }

    // ── scheduleReadyProcesses: empty queue ────────────────────────────────────

    @Test
    void scheduleReadyProcesses_emptyQueue_returnsEmptyList() {
        when(readyQueue.findFirst(any())).thenReturn(null);

        List<Processor> processors = List.of(new Processor(0));
        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertTrue(decisions.isEmpty());
    }

    // ── scheduleReadyProcesses: one ready in-memory process + one free processor

    @Test
    void scheduleReadyProcesses_oneReadyProcessOneFreeProcessor_returnsOneDecision() {
        Process process = mock(Process.class);
        when(process.getLastProcessorId()).thenReturn(-1);

        when(readyQueue.findFirst(any())).thenReturn(process).thenReturn(null);
        when(readyQueue.remove(process)).thenReturn(true);

        Processor processor = new Processor(0);
        List<Processor> processors = List.of(processor);

        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertEquals(1, decisions.size());
        assertSame(process, decisions.get(0).process());
        assertSame(processor, decisions.get(0).processor());
    }

    // ── scheduleReadyProcesses: affinity matching ──────────────────────────────

    @Test
    void scheduleReadyProcesses_affinityMatch_picksAffinityProcessor() {
        Process process = mock(Process.class);
        when(process.getLastProcessorId()).thenReturn(1);

        when(readyQueue.findFirst(any())).thenReturn(process).thenReturn(null);
        when(readyQueue.remove(process)).thenReturn(true);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);
        List<Processor> processors = List.of(proc0, proc1);

        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertEquals(1, decisions.size());
        assertSame(proc1, decisions.get(0).processor()); // affinity picks id=1
    }

    @Test
    void scheduleReadyProcesses_affinityMatchBusyOtherFree_picksOther() {
        Process process = mock(Process.class);
        when(process.getLastProcessorId()).thenReturn(1);

        when(readyQueue.findFirst(any())).thenReturn(process).thenReturn(null);
        when(readyQueue.remove(process)).thenReturn(true);

        Processor proc0 = new Processor(0); // free
        Processor proc1 = new Processor(1);
        proc1.setCurrentProcess(mock(Process.class)); // busy
        List<Processor> processors = List.of(proc0, proc1);

        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertEquals(1, decisions.size());
        assertSame(proc0, decisions.get(0).processor()); // picks first free
    }

    // ── scheduleReadyProcesses: no free processors ─────────────────────────────

    @Test
    void scheduleReadyProcesses_noFreeProcessors_returnsEmptyList() {
        Process process = mock(Process.class);
        lenient().when(process.getLastProcessorId()).thenReturn(-1);
        when(readyQueue.findFirst(any())).thenReturn(process);

        Processor proc0 = new Processor(0);
        proc0.setCurrentProcess(mock(Process.class)); // busy
        Processor proc1 = new Processor(1);
        proc1.setCurrentProcess(mock(Process.class)); // busy
        List<Processor> processors = List.of(proc0, proc1);

        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertTrue(decisions.isEmpty());
    }

    // ── scheduleReadyProcesses: multiple processes and processors ───────────────

    @Test
    void scheduleReadyProcesses_multipleReadyMultipleFree_returnsMultipleDecisions() {
        Process p1 = mock(Process.class);
        when(p1.getLastProcessorId()).thenReturn(-1);
        Process p2 = mock(Process.class);
        when(p2.getLastProcessorId()).thenReturn(-1);

        when(readyQueue.findFirst(any()))
                .thenReturn(p1)
                .thenReturn(p2)
                .thenReturn(null);
        when(readyQueue.remove(any())).thenReturn(true);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);
        List<Processor> processors = new ArrayList<>(List.of(proc0, proc1));

        List<SchedulingDecision> decisions = scheduler.scheduleReadyProcesses(processors, memoryManager);

        assertEquals(2, decisions.size());
    }

    // ── dequeueNextProcessNeedingLoad ──────────────────────────────────────────

    @Test
    void dequeueNextProcessNeedingLoad_allInMemory_returnsNull() {
        when(readyQueue.findFirst(any())).thenReturn(null);

        Process result = scheduler.dequeueNextProcessNeedingLoad(memoryManager);
        assertNull(result);
    }

    @Test
    void dequeueNextProcessNeedingLoad_notLoadedCanFree_returnsProcess() {
        Process process = mock(Process.class);
        when(readyQueue.findFirst(any())).thenReturn(process);
        when(memoryManager.canFreeEnoughMemory(process)).thenReturn(true);
        when(readyQueue.remove(process)).thenReturn(true);

        Process result = scheduler.dequeueNextProcessNeedingLoad(memoryManager);
        assertSame(process, result);
        verify(readyQueue).remove(process);
    }

    @Test
    void dequeueNextProcessNeedingLoad_cannotFreeEnoughMemory_returnsNull() {
        Process process = mock(Process.class);
        when(readyQueue.findFirst(any())).thenReturn(process);
        when(memoryManager.canFreeEnoughMemory(process)).thenReturn(false);

        Process result = scheduler.dequeueNextProcessNeedingLoad(memoryManager);
        assertNull(result);
        verify(readyQueue, never()).remove(any());
    }

    // ── findFreeProcessor (system process, no affinity) ────────────────────────

    @Test
    void findFreeProcessor_allBusy_returnsNull() {
        Processor proc0 = new Processor(0);
        proc0.setCurrentProcess(mock(Process.class));
        Processor proc1 = new Processor(1);
        proc1.setBusyWithSystemProcess(true);

        assertNull(scheduler.findFreeProcessor(List.of(proc0, proc1)));
    }

    @Test
    void findFreeProcessor_oneFree_returnsFirstFree() {
        Processor proc0 = new Processor(0);
        proc0.setCurrentProcess(mock(Process.class)); // busy
        Processor proc1 = new Processor(1); // free

        Processor result = scheduler.findFreeProcessor(List.of(proc0, proc1));
        assertSame(proc1, result);
    }

    @Test
    void findFreeProcessor_multipleFree_returnsFirst() {
        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        Processor result = scheduler.findFreeProcessor(List.of(proc0, proc1));
        assertSame(proc0, result);
    }

    // ── findBestProcessor ──────────────────────────────────────────────────────

    @Test
    void findBestProcessor_noFreeProcessor_returnsNull() {
        Process process = mock(Process.class);
        lenient().when(process.getLastProcessorId()).thenReturn(0);

        Processor proc0 = new Processor(0);
        proc0.setCurrentProcess(mock(Process.class));

        assertNull(scheduler.findBestProcessor(process, List.of(proc0)));
    }

    @Test
    void findBestProcessor_affinityAvailable_returnsAffinityProcessor() {
        Process process = mock(Process.class);
        when(process.getLastProcessorId()).thenReturn(1);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        Processor result = scheduler.findBestProcessor(process, List.of(proc0, proc1));
        assertSame(proc1, result);
    }

    @Test
    void findBestProcessor_noAffinity_returnsFirstFree() {
        Process process = mock(Process.class);
        when(process.getLastProcessorId()).thenReturn(-1);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        Processor result = scheduler.findBestProcessor(process, List.of(proc0, proc1));
        assertSame(proc0, result);
    }

    // ── addToReadyQueue ────────────────────────────────────────────────────────

    @Test
    void addToReadyQueue_delegatesToReadyQueue() {
        Process process = mock(Process.class);
        scheduler.addToReadyQueue(process);
        verify(readyQueue).enqueue(process);
    }

    // ── hasReadyProcesses ──────────────────────────────────────────────────────

    @Test
    void hasReadyProcesses_emptyQueue_returnsFalse() {
        when(readyQueue.isEmpty()).thenReturn(true);
        assertFalse(scheduler.hasReadyProcesses());
    }

    @Test
    void hasReadyProcesses_nonEmptyQueue_returnsTrue() {
        when(readyQueue.isEmpty()).thenReturn(false);
        assertTrue(scheduler.hasReadyProcesses());
    }
}
