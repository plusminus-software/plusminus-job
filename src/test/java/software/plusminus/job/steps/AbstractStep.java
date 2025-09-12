package software.plusminus.job.steps;

import software.plusminus.job.JobStatus;
import software.plusminus.job.Step;

public abstract class AbstractStep implements Step<Void> {

    private JobStatus status;
    private boolean rollback = true;

    @Override
    public Void run() {
        return null;
    }

    @Override
    public boolean rollback() {
        return rollback;
    }

    public void rollback(boolean newRollback) {
        this.rollback = newRollback;
    }

    @Override
    public void status(JobStatus newStatus) {
        this.status = newStatus;
    }

    public JobStatus status() {
        return status;
    }
}
