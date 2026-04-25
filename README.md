# ScheduleSim — OS Process Scheduling Simulator

A discrete event-driven simulator for operating system process scheduling, implementing **preemptive Round-Robin** with **virtual memory (LRU replacement)**, **processor affinity**, and a **higher-priority system process** for syscall execution. Written in Java with no external logic libraries — only Jackson for JSON parsing and Swing for graphical output.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Event-Driven Simulation Engine](#event-driven-simulation-engine)
4. [Scheduling: Preemptive Round-Robin with Processor Affinity](#scheduling-preemptive-round-robin-with-processor-affinity)
5. [System Process and System Calls](#system-process-and-system-calls)
6. [Memory Management and Virtual Memory (LRU)](#memory-management-and-virtual-memory-lru)
7. [Non-Blocking Disk I/O](#non-blocking-disk-io)
8. [Process Model and Lifecycle](#process-model-and-lifecycle)
9. [Input Format](#input-format)
10. [Output](#output)
11. [Key Design Decisions](#key-design-decisions)
12. [How to Run](#how-to-run)

---

## Architecture Overview

The simulator follows a clean pipeline architecture:

```
Main → ApplicationOrchestrator → InputParser → EventDrivenSimulationEngine → TextOutputWriter + GanttChartPanel
```

1. **`Main`** — Entry point. Reads the input file path from command-line args (defaults to `src/main/resources/input.json`).
2. **`ApplicationOrchestrator`** — Wires all components together: parses input, constructs `Process` objects, runs the simulation engine, writes the text output file, and displays the Gantt chart.
3. **`InputParser`** — Deserializes the JSON configuration into `SimulationConfig` and `ProcessConfig` records using Jackson.
4. **`EventDrivenSimulationEngine`** — The core simulation loop. Processes events from a priority queue, coordinating the scheduler, memory manager, and disk controller.
5. **`TextOutputWriter`** — Writes a structured text log of the simulation (chronological, per-processor, and disk operations).
6. **`GanttChartPanel`** — Renders a color-coded Gantt chart in a Swing window with scrolling support.

---

## Project Structure

```
src/main/java/uaic/css/
├── Main.java                          # Entry point
├── ApplicationOrchestrator.java       # Orchestrates the full pipeline
├── config/
│   ├── InputParser.java               # JSON → SimulationConfig (Jackson)
│   ├── SimulationConfig.java          # Immutable record: global simulation parameters
│   └── ProcessConfig.java             # Immutable record: per-process configuration
├── engine/
│   ├── SimulationEngine.java          # Interface for simulation engines
│   └── EventDrivenSimulationEngine.java  # Core event-loop implementation
├── scheduler/
│   └── Scheduler.java                 # Round-Robin scheduler with affinity
├── memory/
│   ├── MemoryManager.java             # RAM tracking, LRU eviction planning
│   └── DiskController.java            # Sequential disk I/O operations
├── model/
│   ├── event/
│   │   ├── Event.java                 # Immutable event record (time, type, process, processor)
│   │   └── EventType.java             # 7 event types with tie-breaking priorities
│   ├── process/
│   │   ├── Process.java               # Mutable process entity with burst tracking
│   │   └── ProcessState.java          # NEW, READY, RUNNING, WAITING_SYSCALL, LOADING, TERMINATED
│   └── simulation/
│       ├── Processor.java             # CPU core: tracks current process and system-process occupancy
│       ├── ReadyQueue.java            # FIFO queue with predicate-based search
│       ├── SchedulingDecision.java    # (Process, Processor) assignment pair
│       ├── SystemCallRequest.java     # Queued syscall: requesting process + duration
│       ├── EvictionResult.java        # Planned eviction: processes to evict + total save time
│       ├── ExecutionLogEntry.java     # One row of output: label, processor, time range, type
│       ├── EntryType.java             # CPU_BURST, SYSCALL, DISK_LOAD, DISK_SAVE, IDLE
│       └── SimulationResult.java      # All log entries + total simulation time
├── output/
│   └── TextOutputWriter.java          # Writes structured text output file
└── ui/
    └── GanttChartPanel.java           # Swing-based Gantt chart visualization
```

---

## Event-Driven Simulation Engine

The simulation is built on a **discrete event-driven** architecture. Instead of stepping through every time unit, the engine jumps directly from one meaningful event to the next, making it both efficient and conceptually clean.

### Core Mechanism

- A **`PriorityQueue<Event>`** serves as the event queue, ordered by time (and by event-type priority for tie-breaking).
- The main loop repeatedly polls the next event, advances `currentTime`, and dispatches to the appropriate handler.
- Each handler may **enqueue new events** into the queue, driving the simulation forward.
- The loop terminates when all processes have reached the `TERMINATED` state.

### Event Types and Priority

When multiple events occur at the same simulation time, they are processed in priority order (lower value = higher priority):

| Priority | Event Type | Description |
|----------|-----------|-------------|
| 1 | `PROCESS_RELEASE` | A user process is created and enters the ready queue |
| 2 | `SYSTEM_PROCESS_COMPLETED` | The system process finishes executing all pending syscalls |
| 3 | `BURST_COMPLETED` | A process finishes its current CPU burst entirely |
| 4 | `TIME_SLICE_EXPIRED` | A process's time slice runs out (preemption) |
| 5 | `SYSCALL_COMPLETED` | A system call for a specific process has been executed |
| 6 | `SYSTEM_PROCESS_RELEASE` | Periodic release of the system process instance |
| 7 | `DISK_TRANSFER_COMPLETE` | A disk load finishes; the process is now in memory |

This priority ordering ensures correct behavior, for example: process releases are handled before scheduling decisions triggered by completions, and burst completions take precedence over time-slice expirations.

### Event Flow Example

```
PROCESS_RELEASE(P1, T=0)
  → P1 added to ready queue
  → trySchedule() assigns P1 to CPU 0
  → If burst(5) > timeSlice(4): enqueue TIME_SLICE_EXPIRED(P1, T=4)

TIME_SLICE_EXPIRED(P1, T=4)
  → P1 preempted, back to ready queue with remainingBurstTime=1
  → trySchedule() re-assigns P1 to CPU 0 (affinity)
  → burst(1) ≤ timeSlice(4): enqueue BURST_COMPLETED(P1, T=5)

BURST_COMPLETED(P1, T=5)
  → P1 has a syscall after this burst → queued as SystemCallRequest
  → P1 state → WAITING_SYSCALL
  → trySchedule() schedules another process if available
```

---

## Scheduling: Preemptive Round-Robin with Processor Affinity

The `Scheduler` class manages the ready queue and makes scheduling decisions. All user processes have **equal priority** and are served in **FIFO order**.

### Round-Robin Preemption

When a process is dispatched onto a processor, the engine compares its remaining burst time against the time slice:

- **`remainingBurstTime ≤ timeSlice`** → Schedule a `BURST_COMPLETED` event. The process runs to completion of the current burst.
- **`remainingBurstTime > timeSlice`** → Schedule a `TIME_SLICE_EXPIRED` event. After the time slice, the process is preempted, its `remainingBurstTime` is decremented, and it goes back to the ready queue.

### Processor Affinity

When the scheduler needs to assign a process to a processor and **multiple processors are free**, it prefers the processor on which the process most recently executed:

```java
// In Scheduler.findBestProcessor():
for (Processor processor : processors) {
    if (processor.isFree()) {
        if (process.getLastProcessorId() == processor.getId()) {
            return processor;  // Affinity match — prefer this one
        }
        if (anyFreeProcessor == null) {
            anyFreeProcessor = processor;  // Fallback
        }
    }
}
return affinityProcessor != null ? affinityProcessor : anyFreeProcessor;
```

Each process tracks its `lastProcessorId` (initialized to `-1`), which is updated every time the process is dispatched. This models CPU cache affinity — a process benefits from running on the same core it previously used.

### Two-Pass Scheduling (`trySchedule`)

Every time the simulation state changes (process released, burst completed, time slice expired, disk transfer done, etc.), `trySchedule()` is called. It works in two passes:

1. **Pass 1 — In-memory processes**: Match ready processes that are already loaded in RAM with free processors. Multiple processes can be scheduled in a single call.
2. **Pass 2 — Disk load**: If there are still ready processes that are **not in memory**, initiate **one** disk load for the next such process (to avoid overloading the single disk channel).

The system process has higher priority and is checked **before** Pass 1 — if it's waiting for a processor, it gets the first available one.

---

## System Process and System Calls

The system process is a special, higher-priority process responsible for executing system calls requested by user processes.

### How It Works

1. **Periodic release**: The system process is released at fixed intervals (`systemProcessPeriod`). The timing is strictly periodic with no drift — each release is scheduled at the exact next multiple of the period.
2. **Syscall queueing**: When a user process completes a CPU burst that is followed by a syscall, the syscall is added to a shared `syscallQueue` with its duration, and the process enters `WAITING_SYSCALL` state.
3. **Execution**: When the system process is released and the syscall queue is non-empty, it claims a free processor (no affinity — the system process uses any available CPU). If no processor is free at release time, it **waits** (`systemProcessWaiting = true`) and gets priority over user processes as soon as one becomes available.
4. **Uninterrupted execution**: The system process runs **uninterrupted** through all pending syscalls in the queue, executing them back-to-back on the claimed processor. It is not subject to the Round-Robin time slice.
5. **Notification**: As each syscall completes, a `SYSCALL_COMPLETED` event is scheduled for the requesting process, moving it back to `READY` so it can be scheduled for its next CPU burst.

---

## Memory Management and Virtual Memory (LRU)

The `MemoryManager` class simulates a fixed-size RAM. The total memory available may be **less than the combined memory requirements of all processes**, so virtual memory with disk swapping is used.

### Memory Tracking

- A `LinkedHashMap<Process, Integer>` maps each loaded process to its **last-used time** (updated every time the process is dispatched to a CPU).
- `usedMemory` tracks currently committed memory; `reservedMemory` tracks memory reserved for in-flight disk loads.
- `getFreeMemory() = totalMemory - usedMemory - reservedMemory`

### Two-Phase Loading: Reserve → Commit

To prevent double-booking of memory while a disk transfer is in progress:

1. **`reserveSpace(amount)`** — Called when a disk load begins. Deducts from free memory immediately so no other load tries to use the same space.
2. **`commitLoad(process, time)`** — Called when the disk transfer completes (`DISK_TRANSFER_COMPLETE` event). Moves the memory from "reserved" to "used" and adds the process to the loaded-processes map.

This two-phase approach ensures correctness when multiple events are processed while a disk transfer is ongoing.

### LRU Eviction Policy

When there isn't enough free memory to load a process, `planEviction()` determines which loaded processes to evict:

1. Sort all loaded processes by `lastUsedTime` in ascending order (least recently used first).
2. Skip processes that are `RUNNING` or `LOADING` — these cannot be evicted.
3. Accumulate processes to evict until enough memory is freed.
4. Return an `EvictionResult` containing the eviction list and the total disk save time.

Before starting a load, `canFreeEnoughMemory()` checks whether eviction is even feasible (enough evictable processes exist to free the required space).

### When Memory Is Freed

- **Eviction**: `unloadProcess()` is called during disk save operations, immediately freeing the memory.
- **Termination**: When a process terminates, it is unloaded from memory if still loaded.

---

## Non-Blocking Disk I/O

A critical design point: **the disk does not block the processors**. The disk operates as an **independent resource** on its own timeline, and processors continue executing other in-memory processes while disk transfers happen in the background.

### DiskController Design

The `DiskController` manages all disk I/O through a **single sequential channel**:

- It tracks `diskBusyUntil` — the time at which the disk becomes free.
- All operations (both eviction saves and new loads) are queued **sequentially** on the disk timeline.
- If the disk is already busy, new operations start at `diskBusyUntil` rather than `currentTime`.

### Disk Transfer Flow

When a process needs to be loaded from disk (`initiateMemoryLoad`):

```
1. Set process state → LOADING
2. Plan eviction (if needed) via MemoryManager.planEviction()
3. For each process to evict:
   a. Log a DISK_SAVE entry on the disk timeline
   b. Unload process from memory (frees RAM immediately)
   c. Advance disk timeline by the transfer time
4. Log a DISK_LOAD entry for the new process
5. Reserve memory space (prevents double-booking)
6. Schedule DISK_TRANSFER_COMPLETE event at load end time
7. Update diskBusyUntil
```

Transfer time is calculated as: `ceil(memoryRequired / diskTransferRate)`

### Why the Disk Doesn't Block Processors

- Disk operations exist on a separate timeline (`diskBusyUntil`) from processor execution.
- While a disk transfer is in progress, the event queue continues processing CPU events (time-slice expirations, burst completions, process releases) normally.
- Only one disk load is initiated per scheduling round (Pass 2 in `trySchedule`), preventing queue flooding.
- When the `DISK_TRANSFER_COMPLETE` event fires, the process is committed to memory, set to `READY`, and scheduling re-evaluates — potentially dispatching the now-loaded process to a free CPU.

This models real OS behavior where DMA (Direct Memory Access) handles disk-to-memory transfers without CPU involvement.

---

## Process Model and Lifecycle

### Process States

```
NEW → READY → RUNNING → WAITING_SYSCALL → READY → RUNNING → ... → TERMINATED
                ↓
             LOADING (if not in memory) → READY (after disk load)
```

| State | Meaning |
|-------|---------|
| `NEW` | Created but not yet released |
| `READY` | In the ready queue, eligible for scheduling |
| `RUNNING` | Currently executing on a processor |
| `WAITING_SYSCALL` | Burst completed, waiting for the system process to execute its syscall |
| `LOADING` | Being loaded from disk into memory |
| `TERMINATED` | All bursts completed, process finished |

### Execution Sequence

Each process has an execution sequence — an odd-length array of integers alternating between CPU burst durations and syscall durations:

```
[5, 2, 3, 4, 9, 4, 6]
 ↑  ↑  ↑  ↑  ↑  ↑  ↑
 B1 S1 B2 S2 B3 S3 B4     (4 CPU bursts, 3 syscalls)
```

The `Process` class parses this into separate `cpuBursts` and `syscallDurations` lists and tracks the current burst index and remaining burst time for preemption support.

---

## Input Format

The simulation reads a JSON configuration file (`src/main/resources/input.json`):

```json
{
  "processors": 2,
  "memorySize": 100,
  "timeSlice": 4,
  "systemProcessPeriod": 20,
  "diskTransferRate": 10,
  "processes": [
    {
      "name": "P1",
      "releaseTime": 0,
      "memoryRequired": 30,
      "executionSequence": [5, 2, 3, 4, 9, 4, 6]
    },
    {
      "name": "P2",
      "releaseTime": 2,
      "memoryRequired": 50,
      "executionSequence": [8, 3, 4]
    },
    {
      "name": "P3",
      "releaseTime": 5,
      "memoryRequired": 40,
      "executionSequence": [3, 1, 7, 2, 4]
    }
  ]
}
```

### Parameters

| Parameter | Description |
|-----------|-------------|
| `processors` | Number of CPUs in the system |
| `memorySize` | Total RAM available (units) |
| `timeSlice` | Round-Robin time quantum |
| `systemProcessPeriod` | Period at which the system process is released |
| `diskTransferRate` | Units of memory transferred per time unit |
| `processes` | List of user processes with release time, memory requirement, and execution sequence |

---

## Output

### Text Output (`output.txt`)

The simulation produces a structured text file with three sections:

1. **Chronological Log** — All events sorted by time, showing what ran where and when.
2. **Per-Processor Timeline** — What each CPU did over time.
3. **Disk Operations** — All disk saves and loads in order.

### Gantt Chart (Swing GUI)

A graphical Gantt chart rendered with Java Swing:

- Each processor and the disk have their own row.
- **CPU bursts** are shown in a unique color per process.
- **Syscalls** are shown in a darker shade of the process's color.
- **Disk loads** use a semi-transparent version of the process color.
- **Disk saves** use an even more transparent shade.
- A **"FINISHED"** block marks the end of simulation on all rows.
- A color-coded legend is drawn below the chart.
- The panel is scrollable for simulations with long timelines.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Event-driven (not time-stepped)** | Jumps directly between meaningful state changes. No wasted computation on idle time units. |
| **Event priority for tie-breaking** | When multiple events occur at the same time, processing order matters for correctness (e.g., releases before scheduling). |
| **Two-phase memory reservation** | `reserveSpace` + `commitLoad` prevents double-booking of RAM during concurrent disk transfers and CPU execution. |
| **Single disk channel with `diskBusyUntil`** | Models a real serial disk. Operations queue behind each other, but don't stall the CPUs. |
| **One disk load per scheduling round** | Prevents flooding the disk queue and ensures fairness — other events (burst completions, releases) get a chance to trigger re-evaluation. |
| **System process runs uninterrupted** | The specification allows this choice. The system process drains all pending syscalls in one burst, simplifying the model and matching the stated assumption that the period is long enough for completion. |
| **Assertions for contracts** | Every constructor, state transition, and critical method uses Java `assert` statements to catch violations early — no external validation libraries needed. |
| **Java records for immutable data** | `Event`, `SimulationConfig`, `ProcessConfig`, `ExecutionLogEntry`, `SchedulingDecision`, `EvictionResult`, `SystemCallRequest`, and `SimulationResult` are all records — immutable, concise, and with auto-generated `equals`/`hashCode`/`toString`. |
| **Single-responsibility classes** | `Scheduler` only decides assignments, `MemoryManager` only tracks memory state, `DiskController` only sequences I/O. The engine orchestrates them all. |
| **No external libraries for logic** | As required by the specification, the implementation uses no library functions for simulation logic. Jackson is used only for JSON parsing, and Swing only for the GUI. |

---

## How to Run

### Prerequisites
- Java 17+
- Maven

### Build and Run

```bash
mvn clean compile exec:java -Dexec.mainClass="uaic.css.Main"
```

Or with a custom input file:

```bash
mvn clean compile exec:java -Dexec.mainClass="uaic.css.Main" -Dexec.args="path/to/input.json"
```

### Output
- **Text**: `output.txt` in the project root
- **Gantt chart**: Opens automatically in a Swing window
