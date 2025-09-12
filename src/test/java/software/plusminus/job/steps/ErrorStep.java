package software.plusminus.job.steps;

import java.util.concurrent.atomic.AtomicBoolean;

public class ErrorStep extends AbstractStep {

    private AtomicBoolean error;

    public ErrorStep(AtomicBoolean error) {
        this.error = error;
    }

    @Override
    public Void run() {
        checkError();
        return super.run();
    }

    @Override
    public boolean rollback() {
        checkError();
        return super.rollback();
    }

    private void checkError() {
        if (error.get()) {
            throw new IllegalStateException("Test error");
        }
    }
}
