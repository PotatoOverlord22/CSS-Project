package uaic.css.model.simulation;

import uaic.css.model.process.Process;

public record SystemCallRequest(Process requestingProcess, int duration) {
    public SystemCallRequest {
        if (requestingProcess == null) {
            throw new IllegalArgumentException("Requesting process must not be null");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("Syscall duration must be positive, got: " + duration);
        }
    }

    @Override
    public String toString() {
        return "SysCall(" + requestingProcess.getName() + ", duration=" + duration + ")";
    }
}
