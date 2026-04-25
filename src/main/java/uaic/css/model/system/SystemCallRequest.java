package uaic.css.model.system;

import uaic.css.model.process.Process;

public record SystemCallRequest(Process requestingProcess, int duration) {
    public SystemCallRequest {
        assert requestingProcess != null : "Requesting process must not be null";
        assert duration > 0 : "Syscall duration must be positive, got: " + duration;

    }

    @Override
    public String toString() {
        return "SysCall(" + requestingProcess.getName() + ", duration=" + duration + ")";
    }
}
