package uaic.css.model.process;

public enum ProcessState {
    NEW,
    READY,
    RUNNING,
    WAITING_SYSCALL,
    LOADING,
    TERMINATED
}
