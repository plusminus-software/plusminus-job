package software.plusminus.job.steps;

public class NoRollbackStep extends AbstractStep {

    @Override
    public Runnable rollback() {
        return null;
    }
}
