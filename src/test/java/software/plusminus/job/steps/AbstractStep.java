package software.plusminus.job.steps;

import software.plusminus.job.JobStatus;
import software.plusminus.job.StepRunner;

public abstract class AbstractStep implements StepRunner<Void> {

    private JobStatus status;

    @Override
    public Void run() {
        return null;
    }

    @Override
    public Runnable rollback() {
        return () -> { };
    }

    @Override
    public void status(JobStatus newStatus) {
        this.status = newStatus;
    }

    public JobStatus status() {
        return status;
    }
}
