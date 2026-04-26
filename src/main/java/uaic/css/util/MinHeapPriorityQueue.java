package uaic.css.util;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Binary min-heap priority queue — custom implementation replacing java.util.PriorityQueue.
 * Elements must implement Comparable; the smallest element is always at the top.
 *
 * Heap invariant: heap[parent] <= heap[child] for every node.
 *
 * Operations:
 *   add    — O(log n)  insert element and sift up
 *   poll   — O(log n)  remove minimum and sift down
 *   peek   — O(1)      inspect minimum without removal
 *   size   — O(1)
 *   isEmpty — O(1)
 */
public class MinHeapPriorityQueue<T extends Comparable<T>> {

    private final List<T> heap;

    public MinHeapPriorityQueue() {
        this.heap = new ArrayList<>();
    }

    public void add(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Cannot add null element to the heap");
        }
        heap.add(element);
        siftUp(heap.size() - 1);
    }

    public T poll() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot poll from an empty priority queue");
        }

        T min = heap.get(0);

        int lastIndex = heap.size() - 1;
        heap.set(0, heap.get(lastIndex));
        heap.remove(lastIndex);

        if (!heap.isEmpty()) {
            siftDown(0);
        }

        return min;
    }

    public T peek() {
        if (isEmpty()) {
            return null;
        }
        return heap.get(0);
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public int size() {
        return heap.size();
    }

    // ── Heap maintenance ──────────────────────────────────────────────────────

    /**
     * After inserting at index i, swap upward while the element is smaller than its parent.
     */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (heap.get(i).compareTo(heap.get(parent)) < 0) {
                swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    /**
     * After replacing root, swap downward with the smaller child until heap order is restored.
     */
    private void siftDown(int i) {
        int size = heap.size();
        while (true) {
            int left  = 2 * i + 1;
            int right = 2 * i + 2;
            int smallest = i;

            if (left < size && heap.get(left).compareTo(heap.get(smallest)) < 0) {
                smallest = left;
            }
            if (right < size && heap.get(right).compareTo(heap.get(smallest)) < 0) {
                smallest = right;
            }

            if (smallest == i) {
                break;
            }

            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        T tmp = heap.get(a);
        heap.set(a, heap.get(b));
        heap.set(b, tmp);
    }
}
