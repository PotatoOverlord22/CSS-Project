package uaic.css.model.system;

import uaic.css.model.process.Process;

public class SystemCallRequest {
    private final Process requestingProcess;
    private final int duration;

    public SystemCallRequest(Process requestingProcess, int duration) {
        assert requestingProcess != null : "Requesting process must not be null";
        assert duration > 0 : "Syscall duration must be positive, got: " + duration;

        this.requestingProcess = requestingProcess;
        this.duration = duration;
    }

    public Process getRequestingProcess() {
        return requestingProcess;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return "SysCall(" + requestingProcess.getName() + ", duration=" + duration + ")";
    }
}
