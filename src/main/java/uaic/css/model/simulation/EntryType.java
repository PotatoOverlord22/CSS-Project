package uaic.css.model.simulation;

/**
 * Types of entries in the simulation execution log.
 */
public enum EntryType {
    CPU_BURST,
    SYSCALL,
    DISK_LOAD,
    DISK_SAVE,
    IDLE
}
