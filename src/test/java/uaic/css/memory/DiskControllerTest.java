package uaic.css.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uaic.css.model.event.Event;
import uaic.css.model.event.EventType;
import uaic.css.model.process.Process;
import uaic.css.model.process.ProcessState;
import uaic.css.model.simulation.EntryType;
import uaic.css.model.simulation.EvictionResult;
import uaic.css.model.simulation.ExecutionLogEntry;
import uaic.css.util.MinHeapPriorityQueue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiskControllerTest {

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private Process process;

    private DiskController diskController;
    private List<ExecutionLogEntry> logEntries;
    private MinHeapPriorityQueue<Event> eventQueue;

    @BeforeEach
    void setUp() {
        diskController = new DiskController(memoryManager);
        logEntries = new ArrayList<>();
        eventQueue = new MinHeapPriorityQueue<>();
    }

    // ── No eviction load ───────────────────────────────────────────────────────

    @Test
    void initiateMemoryLoad_noEviction_noDiskSaveEntriesOneDiskLoadEntry() {
        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(process)).thenReturn(3);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        // No DISK_SAVE entries
        long saveCount = logEntries.stream().filter(e -> e.type() == EntryType.DISK_SAVE).count();
        assertEquals(0, saveCount);

        // One DISK_LOAD entry
        long loadCount = logEntries.stream().filter(e -> e.type() == EntryType.DISK_LOAD).count();
        assertEquals(1, loadCount);

        ExecutionLogEntry loadEntry = logEntries.stream()
                .filter(e -> e.type() == EntryType.DISK_LOAD).findFirst().orElseThrow();
        assertEquals(0, loadEntry.startTime());
        assertEquals(3, loadEntry.endTime());
        assertEquals(ExecutionLogEntry.DISK_PROCESSOR_ID, loadEntry.processorId());
    }

    @Test
    void initiateMemoryLoad_noEviction_reserveSpaceCalledWithMemoryRequired() {
        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(process)).thenReturn(3);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        verify(memoryManager).reserveSpace(30);
    }

    @Test
    void initiateMemoryLoad_noEviction_eventAddedWithDiskTransferCompleteAtLoadEndTime() {
        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(process)).thenReturn(3);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        assertFalse(eventQueue.isEmpty());
        Event event = eventQueue.poll();
        assertEquals(3, event.time());
        assertEquals(EventType.DISK_TRANSFER_COMPLETE, event.type());
        assertSame(process, event.process());
    }

    @Test
    void initiateMemoryLoad_noEviction_processStateSetToLoading() {
        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(process)).thenReturn(3);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        verify(process).setState(ProcessState.LOADING);
    }

    // ── With eviction ──────────────────────────────────────────────────────────

    @Test
    void initiateMemoryLoad_withEviction_correctOrderOfDiskSavesThenLoad() {
        Process evict1 = mock(Process.class);
        when(evict1.getName()).thenReturn("E1");
        Process evict2 = mock(Process.class);
        when(evict2.getName()).thenReturn("E2");

        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(evict1, evict2), 5));
        when(memoryManager.calculateTransferTime(evict1)).thenReturn(2);
        when(memoryManager.calculateTransferTime(evict2)).thenReturn(3);
        when(memoryManager.calculateTransferTime(process)).thenReturn(4);
        when(process.getMemoryRequired()).thenReturn(50);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        // Verify InOrder: unloadProcess for each evicted, then reserveSpace
        InOrder inOrder = inOrder(memoryManager);
        inOrder.verify(memoryManager).planEviction(process);
        inOrder.verify(memoryManager).calculateTransferTime(evict1);
        inOrder.verify(memoryManager).unloadProcess(evict1);
        inOrder.verify(memoryManager).calculateTransferTime(evict2);
        inOrder.verify(memoryManager).unloadProcess(evict2);
        inOrder.verify(memoryManager).calculateTransferTime(process);
        inOrder.verify(memoryManager).reserveSpace(50);

        // Verify log entries in time order
        assertEquals(3, logEntries.size());

        // DISK_SAVE for evict1: [0, 2]
        assertEquals(EntryType.DISK_SAVE, logEntries.get(0).type());
        assertEquals(0, logEntries.get(0).startTime());
        assertEquals(2, logEntries.get(0).endTime());

        // DISK_SAVE for evict2: [2, 5]
        assertEquals(EntryType.DISK_SAVE, logEntries.get(1).type());
        assertEquals(2, logEntries.get(1).startTime());
        assertEquals(5, logEntries.get(1).endTime());

        // DISK_LOAD for process: [5, 9]
        assertEquals(EntryType.DISK_LOAD, logEntries.get(2).type());
        assertEquals(5, logEntries.get(2).startTime());
        assertEquals(9, logEntries.get(2).endTime());

        // Event at t=9
        Event event = eventQueue.poll();
        assertEquals(9, event.time());
        assertEquals(EventType.DISK_TRANSFER_COMPLETE, event.type());
    }

    // ── diskBusyUntil accounting ───────────────────────────────────────────────

    @Test
    void initiateMemoryLoad_firstCallAtT0WithTransfer5_eventScheduledAt5() {
        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(process)).thenReturn(5);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        Event event = eventQueue.poll();
        assertEquals(5, event.time());
    }

    @Test
    void initiateMemoryLoad_secondCallWhileDiskBusy_startsAfterBusyUntil() {
        // First call: t=0, transfer=5 -> diskBusyUntil=5
        Process p1 = mock(Process.class);
        when(p1.getName()).thenReturn("P1");
        when(p1.getMemoryRequired()).thenReturn(30);
        when(memoryManager.planEviction(p1)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(p1)).thenReturn(5);

        diskController.initiateMemoryLoad(p1, 0, logEntries, eventQueue);

        // Second call at t=2 (disk busy until 5): should start at 5
        Process p2 = mock(Process.class);
        when(p2.getName()).thenReturn("P2");
        when(p2.getMemoryRequired()).thenReturn(20);
        when(memoryManager.planEviction(p2)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(p2)).thenReturn(3);

        logEntries.clear();
        diskController.initiateMemoryLoad(p2, 2, logEntries, eventQueue);

        // Load entry should start at 5 (not 2)
        ExecutionLogEntry loadEntry = logEntries.stream()
                .filter(e -> e.type() == EntryType.DISK_LOAD).findFirst().orElseThrow();
        assertEquals(5, loadEntry.startTime());
        assertEquals(8, loadEntry.endTime());
    }

    @Test
    void initiateMemoryLoad_secondCallWhenDiskIdle_startsAtCurrentTime() {
        // First call: t=0, transfer=5 -> diskBusyUntil=5
        Process p1 = mock(Process.class);
        when(p1.getName()).thenReturn("P1");
        when(p1.getMemoryRequired()).thenReturn(30);
        when(memoryManager.planEviction(p1)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(p1)).thenReturn(5);

        diskController.initiateMemoryLoad(p1, 0, logEntries, eventQueue);

        // Second call at t=10 (disk idle since 5): should start at 10
        Process p2 = mock(Process.class);
        when(p2.getName()).thenReturn("P2");
        when(p2.getMemoryRequired()).thenReturn(20);
        when(memoryManager.planEviction(p2)).thenReturn(new EvictionResult(List.of(), 0));
        when(memoryManager.calculateTransferTime(p2)).thenReturn(3);

        logEntries.clear();
        diskController.initiateMemoryLoad(p2, 10, logEntries, eventQueue);

        ExecutionLogEntry loadEntry = logEntries.stream()
                .filter(e -> e.type() == EntryType.DISK_LOAD).findFirst().orElseThrow();
        assertEquals(10, loadEntry.startTime());
        assertEquals(13, loadEntry.endTime());
    }

    // ── Log entries use DISK_PROCESSOR_ID ──────────────────────────────────────

    @Test
    void initiateMemoryLoad_allLogEntries_useDiskProcessorId() {
        Process evict = mock(Process.class);
        when(evict.getName()).thenReturn("E1");

        when(memoryManager.planEviction(process)).thenReturn(new EvictionResult(List.of(evict), 2));
        when(memoryManager.calculateTransferTime(evict)).thenReturn(2);
        when(memoryManager.calculateTransferTime(process)).thenReturn(3);
        when(process.getMemoryRequired()).thenReturn(30);
        when(process.getName()).thenReturn("P1");

        diskController.initiateMemoryLoad(process, 0, logEntries, eventQueue);

        for (ExecutionLogEntry entry : logEntries) {
            assertEquals(ExecutionLogEntry.DISK_PROCESSOR_ID, entry.processorId());
        }
    }
}
