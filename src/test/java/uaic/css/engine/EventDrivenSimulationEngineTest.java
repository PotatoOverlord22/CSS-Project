package uaic.css.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uaic.css.config.ProcessConfig;
import uaic.css.config.SimulationConfig;
import uaic.css.memory.DiskController;
import uaic.css.memory.MemoryManager;
import uaic.css.model.event.Event;
import uaic.css.model.event.EventType;
import uaic.css.model.process.Process;
import uaic.css.model.simulation.*;
import uaic.css.scheduler.Scheduler;
import uaic.css.util.MinHeapPriorityQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Timeout(value = 10, unit = TimeUnit.SECONDS) // Safety net: no test should run > 10s
class EventDrivenSimulationEngineTest {

    @Mock
    private Scheduler scheduler;

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private DiskController diskController;

    private EventDrivenSimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EventDrivenSimulationEngine(scheduler, memoryManager, diskController);
    }

    // ── Constructor validation ─────────────────────────────────────────────────

    @Test
    void constructor_nullScheduler_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventDrivenSimulationEngine(null, memoryManager, diskController));
    }

    @Test
    void constructor_nullMemoryManager_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventDrivenSimulationEngine(scheduler, null, diskController));
    }

    @Test
    void constructor_nullDiskController_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventDrivenSimulationEngine(scheduler, memoryManager, null));
    }

    // ── PROCESS_RELEASE ────────────────────────────────────────────────────────

    @Test
    void run_processRelease_processAddedToReadyQueueAndScheduleAttempted() {
        Process p = new Process("P1", 0, 10, List.of(3));

        // Use huge systemProcessPeriod so no system process events fire during the test
        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        // scheduleReadyProcesses returns a decision to run P1
        Processor proc = new Processor(0);
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc)))
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        engine.run(config, List.of(p));

        verify(scheduler).addToReadyQueue(p);
        assertTrue(p.isTerminated());
    }

    // ── BURST_COMPLETED + more bursts + syscall follows ────────────────────────

    @Test
    void run_burstCompletedWithSyscall_processEntersWaitingSyscallThenTerminates() {
        // Process with [3, 2, 4] -> burst=3, syscall=2, burst=4
        Process p = new Process("P1", 0, 10, List.of(3, 2, 4));

        // systemProcessPeriod=5 so system process fires at t=5 (after burst completes at t=3)
        SimulationConfig config = createConfig(2, 100, 10, 5, 10);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);
        SchedulingDecision decision = new SchedulingDecision(p, proc0);

        // First schedule: run burst 1. After syscall completes, run burst 2.
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(decision))   // dispatch first burst
                .thenReturn(List.of())           // after burst completes (syscall queued)
                .thenReturn(List.of(decision))   // after syscall completes, dispatch second burst
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);
        // Provide a free processor for the system process to execute syscalls
        when(scheduler.findFreeProcessor(any())).thenReturn(proc1);

        SimulationResult result = engine.run(config, List.of(p));

        assertTrue(p.isTerminated());
        // Should have SYSCALL entry in the log
        boolean hasSyscall = result.logEntries().stream()
                .anyMatch(e -> e.type() == EntryType.SYSCALL);
        assertTrue(hasSyscall, "Expected SYSCALL log entry");
    }

    // ── BURST_COMPLETED + no more bursts -> TERMINATED ─────────────────────────

    @Test
    void run_burstCompletedNoMoreBursts_processTerminatedAndMemoryUnloaded() {
        Process p = new Process("P1", 0, 10, List.of(3));

        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        Processor proc = new Processor(0);
        SchedulingDecision decision = new SchedulingDecision(p, proc);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(decision))
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        engine.run(config, List.of(p));

        assertTrue(p.isTerminated());
        verify(memoryManager).unloadProcess(p);
    }

    // ── TIME_SLICE_EXPIRED ─────────────────────────────────────────────────────

    @Test
    void run_timeSliceExpired_processReAddedToReadyQueue() {
        // Process burst=8, timeSlice=4 -> will be preempted once
        Process p = new Process("P1", 0, 10, List.of(8));

        SimulationConfig config = createConfig(1, 100, 4, 99999, 10);

        Processor proc = new Processor(0);
        SchedulingDecision decision = new SchedulingDecision(p, proc);

        // First schedule: burst 8 > slice 4 → TIME_SLICE_EXPIRED at t=4
        // After preemption, re-added to queue then scheduled again (remaining=4 <= slice=4 → completes)
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(decision))  // first dispatch (8>4 → preempted)
                .thenReturn(List.of(decision))  // second dispatch (4<=4 → completes)
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        SimulationResult result = engine.run(config, List.of(p));

        // addToReadyQueue called: once at release, once at preemption
        verify(scheduler, atLeast(2)).addToReadyQueue(p);
        assertTrue(p.isTerminated());
        assertEquals(8, result.totalTime()); // 4 + 4
    }

    // ── SYSCALL_COMPLETED ──────────────────────────────────────────────────────

    @Test
    void run_syscallCompleted_processReturnsToReady() {
        // [3, 2, 4]: burst=3, syscall=2, burst=4
        Process p = new Process("P1", 0, 10, List.of(3, 2, 4));

        // System process period = 5, fires at t=5 (after first burst completes at t=3)
        SimulationConfig config = createConfig(2, 100, 10, 5, 10);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);
        SchedulingDecision decision = new SchedulingDecision(p, proc0);

        // Sequence of trySchedule calls:
        // 1. t=0 PROCESS_RELEASE → dispatch first burst
        // 2. t=3 BURST_COMPLETED → syscall queued, no scheduling
        // 3. t=7 SYSTEM_PROCESS_COMPLETED → P1 still WAITING_SYSCALL, nothing to schedule
        // 4. t=7 SYSCALL_COMPLETED → P1 back to READY, dispatch second burst
        // 5. t=11 BURST_COMPLETED → terminated
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(decision))   // t=0: dispatch first burst
                .thenReturn(List.of())           // t=3: burst done, nothing to schedule
                .thenReturn(List.of())           // t=7: system_process_completed trySchedule
                .thenReturn(List.of(decision))   // t=7: syscall_completed trySchedule, dispatch second burst
                .thenReturn(List.of());          // t=11: final burst done
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);
        // Provide a free processor for executing system calls
        when(scheduler.findFreeProcessor(any())).thenReturn(proc1);

        engine.run(config, List.of(p));

        // Process should terminate after both bursts complete
        assertTrue(p.isTerminated());
        // addToReadyQueue called: release + after syscall completes
        verify(scheduler, atLeast(2)).addToReadyQueue(p);
    }

    // ── DISK_TRANSFER_COMPLETE ─────────────────────────────────────────────────

    @Test
    void run_diskTransferComplete_diskControllerInitiateMemoryLoadCalled() {
        Process p = new Process("P1", 0, 10, List.of(3));

        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        Processor proc = new Processor(0);
        SchedulingDecision decision = new SchedulingDecision(p, proc);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of())            // t=0: nothing in memory, pass 2 triggers disk load
                .thenReturn(List.of(decision))    // DISK_TRANSFER_COMPLETE: dispatches P1
                .thenReturn(List.of());           // burst complete trySchedule
        when(scheduler.hasReadyProcesses())
                .thenReturn(true)    // t=0: triggers pass 2
                .thenReturn(false);
        when(scheduler.dequeueNextProcessNeedingLoad(any())).thenReturn(p).thenReturn(null);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        // Mock diskController to simulate adding a DISK_TRANSFER_COMPLETE event
        doAnswer(invocation -> {
            MinHeapPriorityQueue<Event> eq = invocation.getArgument(3);
            eq.add(new Event(5, EventType.DISK_TRANSFER_COMPLETE, p));
            return null;
        }).when(diskController).initiateMemoryLoad(eq(p), anyInt(), any(), any());

        engine.run(config, List.of(p));

        verify(diskController).initiateMemoryLoad(eq(p), anyInt(), any(), any());
    }

    // ── Zero processes ─────────────────────────────────────────────────────────

    @Test
    void run_zeroProcesses_terminatesImmediatelyWithEmptyResult() {
        SimulationConfig config = createConfig(1, 100, 4, 100, 10);

        SimulationResult result = engine.run(config, new ArrayList<>());

        assertEquals(0, result.totalTime());
        assertTrue(result.logEntries().isEmpty());
    }

    // ── All processes released at t=0 ──────────────────────────────────────────

    @Test
    void run_allProcessesReleasedAtT0_allAddedToReadyQueue() {
        Process p1 = new Process("P1", 0, 10, List.of(3));
        Process p2 = new Process("P2", 1, 10, List.of(4));

        SimulationConfig config = createConfig(2, 100, 10, 99999, 10);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        // P1 released at t=0, P2 released at t=1
        // First trySchedule dispatches P1. Second dispatches P2.
        // Then BURST_COMPLETED events fire at t=3 and t=5.
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p1, proc0)))  // t=0: dispatch P1
                .thenReturn(List.of(new SchedulingDecision(p2, proc1)))  // t=1: dispatch P2
                .thenReturn(List.of())  // t=3: P1 burst complete trySchedule
                .thenReturn(List.of()); // t=5: P2 burst complete trySchedule
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(any())).thenReturn(true);

        engine.run(config, List.of(p1, p2));

        verify(scheduler).addToReadyQueue(p1);
        verify(scheduler).addToReadyQueue(p2);
        assertTrue(p1.isTerminated());
        assertTrue(p2.isTerminated());
    }

    // ── Event tie-break ────────────────────────────────────────────────────────

    @Test
    void eventComparison_sameTimeDifferentTypes_lowestPriorityIntProcessedFirst() {
        // PROCESS_RELEASE has priority 1, DISK_TRANSFER_COMPLETE has priority 7
        Event release = new Event(5, EventType.PROCESS_RELEASE, mock(Process.class));
        Event diskComplete = new Event(5, EventType.DISK_TRANSFER_COMPLETE, mock(Process.class));

        MinHeapPriorityQueue<Event> pq = new MinHeapPriorityQueue<>();
        pq.add(diskComplete);
        pq.add(release);

        // PROCESS_RELEASE (priority=1) should come out first
        Event first = pq.poll();
        assertEquals(EventType.PROCESS_RELEASE, first.type());
    }

    @Test
    void eventComparison_differentTimes_earlierTimeProcessedFirst() {
        Event early = new Event(3, EventType.BURST_COMPLETED, mock(Process.class));
        Event late = new Event(7, EventType.PROCESS_RELEASE, mock(Process.class));

        MinHeapPriorityQueue<Event> pq = new MinHeapPriorityQueue<>();
        pq.add(late);
        pq.add(early);

        Event first = pq.poll();
        assertEquals(3, first.time());
    }

    // ── trySchedule Pass-2: only ONE disk load per call ────────────────────────

    @Test
    void run_processNeedsLoading_onlyOneDiskLoadInitiatedPerScheduleCall() {
        Process p1 = new Process("P1", 0, 10, List.of(3));

        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        Processor proc = new Processor(0);
        SchedulingDecision decision = new SchedulingDecision(p1, proc);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of())            // t=0: not in memory, pass 2 triggers disk load
                .thenReturn(List.of(decision))    // DISK_TRANSFER_COMPLETE: dispatch P1
                .thenReturn(List.of());           // burst complete
        when(scheduler.hasReadyProcesses())
                .thenReturn(true)    // t=0: triggers pass 2
                .thenReturn(false);
        when(scheduler.dequeueNextProcessNeedingLoad(any())).thenReturn(p1).thenReturn(null);
        when(memoryManager.isLoaded(p1)).thenReturn(true);

        // Mock diskController to simulate adding a DISK_TRANSFER_COMPLETE event
        doAnswer(invocation -> {
            MinHeapPriorityQueue<Event> eq = invocation.getArgument(3);
            eq.add(new Event(5, EventType.DISK_TRANSFER_COMPLETE, p1));
            return null;
        }).when(diskController).initiateMemoryLoad(eq(p1), anyInt(), any(), any());

        engine.run(config, List.of(p1));

        // Verify diskController called exactly once (one disk load per trySchedule pass-2)
        verify(diskController, times(1)).initiateMemoryLoad(eq(p1), anyInt(), any(), any());
    }

    // ── SYSTEM_PROCESS_RELEASE with syscalls queued + free processor ────────────

    @Test
    void run_systemProcessReleaseWithSyscallsAndFreeProcessor_executesSyscalls() {
        // Process with syscall: [3, 2, 4]
        Process p = new Process("P1", 0, 10, List.of(3, 2, 4));

        // system process period = 5, so system process releases at t=5
        SimulationConfig config = createConfig(2, 100, 10, 5, 10);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc0))) // first burst
                .thenReturn(List.of())                                  // burst done, syscall queued
                .thenReturn(List.of(new SchedulingDecision(p, proc0))) // after syscall, second burst
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(scheduler.findFreeProcessor(any())).thenReturn(proc1);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        SimulationResult result = engine.run(config, List.of(p));

        // Process terminates successfully (both bursts ran)
        assertTrue(p.isTerminated());
        // Result should contain SYSCALL log entries
        boolean hasSyscallEntry = result.logEntries().stream()
                .anyMatch(e -> e.type() == EntryType.SYSCALL);
        assertTrue(hasSyscallEntry, "Should have syscall log entries");
    }

    // ── SYSTEM_PROCESS_RELEASE no free processor -> systemProcessWaiting ───────

    @Test
    void run_systemProcessReleaseNoFreeProcessor_syscallStillExecutesLater() {
        // Process with syscall: [3, 2, 4], only 1 processor
        Process p = new Process("P1", 0, 10, List.of(3, 2, 4));

        // period=5, 1 processor: at t=5 proc is busy with P1 (burst ends at t=3, but proc is free by then)
        // Actually with burst=3, proc is free at t=3. System process at t=5: proc IS free.
        // Let's use period=2 so it fires while P1 is still running its first burst.
        // burst=3 ends at t=3, period=2 fires at t=2 (P1 still running)
        SimulationConfig config = createConfig(1, 100, 10, 2, 10);

        Processor proc = new Processor(0);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc))) // first burst
                .thenReturn(List.of())                                 // burst done
                .thenReturn(List.of(new SchedulingDecision(p, proc))) // after syscall
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        // At t=2 (system release while P1 running): no free processor -> null
        // At t=3 (burst complete -> trySchedule -> system waiting + free): returns proc
        // For subsequent calls, return proc
        when(scheduler.findFreeProcessor(any()))
                .thenReturn(null)  // first call at t=2: busy
                .thenReturn(proc); // after burst completes, trySchedule checks again
        when(memoryManager.isLoaded(p)).thenReturn(true);

        engine.run(config, List.of(p));

        assertTrue(p.isTerminated());
    }

    // ── SYSTEM_PROCESS_COMPLETED ───────────────────────────────────────────────

    @Test
    void run_singleProcessWithSyscall_systemProcessCompletedFreesProcessor() {
        Process p = new Process("P1", 0, 10, List.of(3, 2, 4));

        SimulationConfig config = createConfig(2, 100, 10, 5, 10);

        Processor proc0 = new Processor(0);
        Processor proc1 = new Processor(1);

        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc0)))
                .thenReturn(List.of())
                .thenReturn(List.of(new SchedulingDecision(p, proc0)))
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(scheduler.findFreeProcessor(any())).thenReturn(proc1);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        SimulationResult result = engine.run(config, List.of(p));

        assertTrue(p.isTerminated());
        assertTrue(result.totalTime() > 0);
    }

    // ── SimulationResult correctness ───────────────────────────────────────────

    @Test
    void run_singleBurstProcess_resultContainsCpuBurstEntry() {
        Process p = new Process("P1", 0, 10, List.of(3));

        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        Processor proc = new Processor(0);
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc)))
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        SimulationResult result = engine.run(config, List.of(p));

        assertFalse(result.logEntries().isEmpty());
        boolean hasCpuBurst = result.logEntries().stream()
                .anyMatch(e -> e.type() == EntryType.CPU_BURST);
        assertTrue(hasCpuBurst);

        ExecutionLogEntry cpuEntry = result.logEntries().stream()
                .filter(e -> e.type() == EntryType.CPU_BURST).findFirst().orElseThrow();
        assertEquals(0, cpuEntry.startTime());
        assertEquals(3, cpuEntry.endTime());
        assertEquals("P1", cpuEntry.label());
    }

    @Test
    void run_singleBurstProcess_totalTimeEqualsEndOfBurst() {
        Process p = new Process("P1", 0, 10, List.of(3));

        SimulationConfig config = createConfig(1, 100, 10, 99999, 10);

        Processor proc = new Processor(0);
        when(scheduler.scheduleReadyProcesses(any(), any()))
                .thenReturn(List.of(new SchedulingDecision(p, proc)))
                .thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);
        when(memoryManager.isLoaded(p)).thenReturn(true);

        SimulationResult result = engine.run(config, List.of(p));

        assertEquals(3, result.totalTime());
    }

    // ── Integer overflow in system process scheduling ───────────────────

    @Test
    void run_systemProcessPeriodCausesIntegerOverflow_shouldNotCrash() {
        Process p = new Process("P1", 0, 10, List.of(3));

        // systemProcessPeriod near MAX_INT/2: after 2 additions, overflows
        SimulationConfig config = createConfig(1, 100, 10, Integer.MAX_VALUE / 2, 10);

        // Don't schedule the process (it never terminates), forcing the system process
        // timer to keep re-scheduling and eventually overflow.
        when(scheduler.scheduleReadyProcesses(any(), any())).thenReturn(List.of());
        when(scheduler.hasReadyProcesses()).thenReturn(false);

        // Expected: should complete without throwing (correct behavior)
        assertDoesNotThrow(() -> engine.run(config, List.of(p)));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private SimulationConfig createConfig(int processors, int memorySize, int timeSlice,
            int systemProcessPeriod, int diskTransferRate) {
        List<ProcessConfig> processConfigs = List.of(
                new ProcessConfig("dummy", 0, 10, List.of(1)));
        return new SimulationConfig(processors, memorySize, timeSlice,
                systemProcessPeriod, diskTransferRate, processConfigs);
    }
}
