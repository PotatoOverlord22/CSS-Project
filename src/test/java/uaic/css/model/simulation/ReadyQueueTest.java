package uaic.css.model.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uaic.css.model.process.Process;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadyQueueTest {

    private ReadyQueue queue;

    @BeforeEach
    void setUp() {
        queue = new ReadyQueue();
    }

    // ── enqueue(null) ──────────────────────────────────────────────────────────

    @Test
    void enqueue_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
    }

    // ── dequeue() on empty ─────────────────────────────────────────────────────

    @Test
    void dequeue_emptyQueue_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> queue.dequeue());
    }

    // ── FIFO order ─────────────────────────────────────────────────────────────

    @Test
    void enqueueAndDequeue_multipleProcesses_maintainsFIFO() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        Process c = new Process("C", 0, 10, List.of(5));

        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        assertSame(a, queue.dequeue());
        assertSame(b, queue.dequeue());
        assertSame(c, queue.dequeue());
    }

    // ── findFirst ──────────────────────────────────────────────────────────────

    @Test
    void findFirst_predicateMatchesNothing_returnsNull() {
        Process a = new Process("A", 0, 10, List.of(5));
        queue.enqueue(a);
        assertNull(queue.findFirst(p -> p.getName().equals("X")));
    }

    @Test
    void findFirst_predicateMatches_returnsMatchWithoutRemoving() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        queue.enqueue(a);
        queue.enqueue(b);

        Process found = queue.findFirst(p -> p.getName().equals("B"));
        assertSame(b, found);
        assertEquals(2, queue.size()); // not removed
    }

    @Test
    void findFirst_emptyQueue_returnsNull() {
        assertNull(queue.findFirst(p -> true));
    }

    // ── removeFirstMatching ────────────────────────────────────────────────────

    @Test
    void removeFirstMatching_predicateMatchesNothing_returnsNullQueueUnchanged() {
        Process a = new Process("A", 0, 10, List.of(5));
        queue.enqueue(a);
        assertNull(queue.removeFirstMatching(p -> p.getName().equals("X")));
        assertEquals(1, queue.size());
    }

    @Test
    void removeFirstMatching_preservesFIFOForRemaining() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        Process c = new Process("C", 0, 10, List.of(5));
        Process d = new Process("D", 0, 10, List.of(5));

        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);
        queue.enqueue(d);

        Process removed = queue.removeFirstMatching(p -> p.getName().equals("B"));
        assertSame(b, removed);
        assertEquals(3, queue.size());

        // Remaining FIFO: A, C, D
        assertSame(a, queue.dequeue());
        assertSame(c, queue.dequeue());
        assertSame(d, queue.dequeue());
    }

    @Test
    void removeFirstMatching_emptyQueue_returnsNull() {
        assertNull(queue.removeFirstMatching(p -> true));
    }

    // ── contains ───────────────────────────────────────────────────────────────

    @Test
    void contains_processInQueue_returnsTrue() {
        Process a = new Process("A", 0, 10, List.of(5));
        queue.enqueue(a);
        assertTrue(queue.contains(a));
    }

    @Test
    void contains_processNotInQueue_returnsFalse() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        queue.enqueue(a);
        assertFalse(queue.contains(b));
    }

    @Test
    void contains_emptyQueue_returnsFalse() {
        Process a = new Process("A", 0, 10, List.of(5));
        assertFalse(queue.contains(a));
    }

    // ── size ───────────────────────────────────────────────────────────────────

    @Test
    void size_emptyQueue_returnsZero() {
        assertEquals(0, queue.size());
    }

    @Test
    void size_afterEnqueue_increments() {
        Process a = new Process("A", 0, 10, List.of(5));
        queue.enqueue(a);
        assertEquals(1, queue.size());
    }

    @Test
    void size_afterDequeue_decrements() {
        Process a = new Process("A", 0, 10, List.of(5));
        queue.enqueue(a);
        queue.dequeue();
        assertEquals(0, queue.size());
    }

    // ── isEmpty ────────────────────────────────────────────────────────────────

    @Test
    void isEmpty_emptyQueue_returnsTrue() {
        assertTrue(queue.isEmpty());
    }

    @Test
    void isEmpty_nonEmptyQueue_returnsFalse() {
        queue.enqueue(new Process("A", 0, 10, List.of(5)));
        assertFalse(queue.isEmpty());
    }

    // ── remove(Process) ────────────────────────────────────────────────────────

    @Test
    void remove_processInQueue_returnsTrueAndRemoves() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        queue.enqueue(a);
        queue.enqueue(b);

        assertTrue(queue.remove(a));
        assertEquals(1, queue.size());
        assertSame(b, queue.dequeue());
    }

    @Test
    void remove_processNotInQueue_returnsFalse() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        queue.enqueue(a);

        assertFalse(queue.remove(b));
        assertEquals(1, queue.size());
    }

    // ── peek ───────────────────────────────────────────────────────────────────

    @Test
    void peek_emptyQueue_returnsNull() {
        assertNull(queue.peek());
    }

    @Test
    void peek_nonEmptyQueue_returnsFirstWithoutRemoving() {
        Process a = new Process("A", 0, 10, List.of(5));
        Process b = new Process("B", 0, 10, List.of(5));
        queue.enqueue(a);
        queue.enqueue(b);
        assertSame(a, queue.peek());
        assertEquals(2, queue.size());
    }
}
