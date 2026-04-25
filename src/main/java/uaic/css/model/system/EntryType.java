package uaic.css.model.system;

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
