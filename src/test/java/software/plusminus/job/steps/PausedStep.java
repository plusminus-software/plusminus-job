package software.plusminus.job.steps;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

public class PausedStep extends AbstractStep {

    private AtomicBoolean paused;

    public PausedStep(AtomicBoolean paused) {
        this.paused = paused;
    }

    @Override
    public Void run() {
        if (paused.get()) {
            await().until(() -> !paused.get());
        }
        return super.run();
    }

    @Override
    public boolean rollback() {
        if (paused.get()) {
            await().until(() -> !paused.get());
        }
        return super.rollback();
    }
}
