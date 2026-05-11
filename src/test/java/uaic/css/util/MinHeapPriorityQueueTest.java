package uaic.css.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class MinHeapPriorityQueueTest {

    private MinHeapPriorityQueue<Integer> queue;

    @BeforeEach
    void setUp() {
        queue = new MinHeapPriorityQueue<>();
    }

    // ── add(null) ──────────────────────────────────────────────────────────────

    @Test
    void add_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> queue.add(null));
    }

    // ── poll() on empty ────────────────────────────────────────────────────────

    @Test
    void poll_emptyQueue_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> queue.poll());
    }

    // ── peek() on empty ────────────────────────────────────────────────────────

    @Test
    void peek_emptyQueue_returnsNull() {
        assertNull(queue.peek());
    }

    // ── add + poll: single element ─────────────────────────────────────────────

    @Test
    void addAndPoll_singleElement_returnsThatElement() {
        queue.add(42);
        assertEquals(42, queue.poll());
        assertTrue(queue.isEmpty());
    }

    // ── add + poll: two reversed elements ──────────────────────────────────────

    @Test
    void addAndPoll_twoReversedElements_returnsAscendingOrder() {
        queue.add(10);
        queue.add(5);
        assertEquals(5, queue.poll());
        assertEquals(10, queue.poll());
    }

    // ── add + poll: 100 shuffled elements ──────────────────────────────────────

    @Test
    void addAndPoll_100ShuffledElements_returnsAscendingOrder() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers);

        for (int n : numbers) {
            queue.add(n);
        }

        for (int i = 0; i < 100; i++) {
            assertEquals(i, queue.poll());
        }
    }

    // ── Duplicates ─────────────────────────────────────────────────────────────

    @Test
    void addAndPoll_duplicateElements_pollsAllWithoutCrash() {
        queue.add(5);
        queue.add(5);
        queue.add(5);
        assertEquals(3, queue.size());

        assertEquals(5, queue.poll());
        assertEquals(2, queue.size());
        assertEquals(5, queue.poll());
        assertEquals(1, queue.size());
        assertEquals(5, queue.poll());
        assertTrue(queue.isEmpty());
    }

    // ── size() / isEmpty() ─────────────────────────────────────────────────────

    @Test
    void size_emptyQueue_returnsZero() {
        assertEquals(0, queue.size());
    }

    @Test
    void isEmpty_emptyQueue_returnsTrue() {
        assertTrue(queue.isEmpty());
    }

    @Test
    void size_afterAdds_returnsCorrectCount() {
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertEquals(3, queue.size());
    }

    @Test
    void isEmpty_afterAdd_returnsFalse() {
        queue.add(1);
        assertFalse(queue.isEmpty());
    }

    @Test
    void size_afterAddAndPoll_decreases() {
        queue.add(1);
        queue.add(2);
        queue.poll();
        assertEquals(1, queue.size());
    }

    @Test
    void isEmpty_afterPollingAll_returnsTrue() {
        queue.add(1);
        queue.poll();
        assertTrue(queue.isEmpty());
    }

    // ── peek() correctness ─────────────────────────────────────────────────────

    @Test
    void peek_nonEmptyQueue_returnsMinWithoutRemoving() {
        queue.add(10);
        queue.add(3);
        queue.add(7);
        assertEquals(3, queue.peek());
        assertEquals(3, queue.size()); // not removed
    }

    // ── Stress test ────────────────────────────────────────────────────────────

    @Test
    void addAndPoll_10000Elements_returnsAscendingOrder() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers);

        for (int n : numbers) {
            queue.add(n);
        }

        int prev = Integer.MIN_VALUE;
        while (!queue.isEmpty()) {
            int current = queue.poll();
            assertTrue(current >= prev, "Expected ascending order but got " + current + " after " + prev);
            prev = current;
        }
    }
}
