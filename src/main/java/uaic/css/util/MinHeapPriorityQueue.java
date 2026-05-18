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
        int sizeBefore = heap.size();

        heap.add(element);
        siftUp(heap.size() - 1);

        assert heap.size() == sizeBefore + 1 : "Heap size must increase by 1 after add";
        assert checkHeapInvariant() : "Heap invariant violated after add";
    }

    public T poll() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot poll from an empty priority queue");
        }

        int sizeBefore = heap.size();
        T min = heap.get(0);

        int lastIndex = heap.size() - 1;
        heap.set(0, heap.get(lastIndex));
        heap.remove(lastIndex);

        if (!heap.isEmpty()) {
            siftDown(0);
        }

        assert heap.size() == sizeBefore - 1 : "Heap size must decrease by 1 after poll";
        assert checkHeapInvariant() : "Heap invariant violated after poll";
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
        assert i == 0 || heap.get(i).compareTo(heap.get((i - 1) / 2)) >= 0
                : "After siftUp, element must be >= its parent";
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
        assert (2 * i + 1 >= size || heap.get(i).compareTo(heap.get(2 * i + 1)) <= 0)
                : "After siftDown, element must be <= its left child";
        assert (2 * i + 2 >= size || heap.get(i).compareTo(heap.get(2 * i + 2)) <= 0)
                : "After siftDown, element must be <= its right child";
    }

    private void swap(int a, int b) {
        T tmp = heap.get(a);
        heap.set(a, heap.get(b));
        heap.set(b, tmp);
    }

    private boolean checkHeapInvariant() {
        for (int i = 1; i < heap.size(); i++) {
            int parent = (i - 1) / 2;
            if (heap.get(i).compareTo(heap.get(parent)) < 0) {
                return false;
            }
        }
        return true;
    }
}
