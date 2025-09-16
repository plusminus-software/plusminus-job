package software.plusminus.job;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.plusminus.job.steps.ErrorStep;
import software.plusminus.job.steps.InvalidStep;
import software.plusminus.job.steps.NoRollbackStep;
import software.plusminus.job.steps.NotPausedStep;
import software.plusminus.job.steps.PausedStep;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static software.plusminus.check.Checks.check;

@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
class JobTest {

    private AtomicBoolean paused = new AtomicBoolean(true);
    private AtomicBoolean error = new AtomicBoolean(true);
    private Consumer<JobStatus> listener = mock(Consumer.class);
    private Job job = new Job(listener);
    private Step<Void> step1 = Step.of(new NotPausedStep());
    private Step<Void> step2 = Step.of(new NotPausedStep());
    private Step<Void> pausedStep = Step.of(new PausedStep(paused));
    private Step<Void> errorStep = Step.of(new ErrorStep(error));
    private InvalidStep invalidStepRunner = new InvalidStep();
    private Step<Void> invalidStep = Step.of(invalidStepRunner);
    private Step<Void> noRollbackStep = Step.of(new NoRollbackStep());

    @Test
    void emptyJob() {
        job = new Job();
        check(job.getStatus()).is(JobStatus.INVALID);
    }

    @Test
    void skippedJob() {
        job.addStep(step1);
        job.addStep(step2);
        step1.skip();
        step2.skip();

        checkJobStatusChanges(JobStatus.READY, JobStatus.SKIPPED);
        check(job.getStatus()).is(JobStatus.SKIPPED);
        check(step1.getStatus()).is(JobStatus.SKIPPED);
        check(step2.getStatus()).is(JobStatus.SKIPPED);
    }

    @Test
    void readyJob() {
        job.addStep(step1);
        job.addStep(step2);

        checkJobStatusChanges(JobStatus.READY);
        check(job.getStatus()).is(JobStatus.READY);
        check(step1.getStatus()).is(JobStatus.READY);
        check(step2.getStatus()).is(JobStatus.READY);
    }

    @Test
    void successJob() {
        job.addStep(step1);
        job.addStep(step2);

        job.run();

        check(job.getStatus()).is(JobStatus.SUCCESS);
        check(step1.getStatus()).is(JobStatus.SUCCESS);
        check(step2.getStatus()).is(JobStatus.SUCCESS);
    }

    @Test
    void successRollbackJob() {
        job.addStep(step1);
        job.addStep(step2);

        job.run();
        job.rollback();

        check(job.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step1.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step2.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    @Test
    void partialRollbackJob() {
        job.addStep(step1);
        job.addStep(noRollbackStep);

        job.run();
        job.rollback();

        check(job.getStatus()).is(JobStatus.PARTIAL_ROLLBACK);
        check(step1.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
        check(noRollbackStep.getStatus()).is(JobStatus.NO_ROLLBACK);
    }

    @Test
    void waitingJob() {
        job.addStep(pausedStep);
        job.addStep(step2);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING);
        check(job.getStatus()).is(JobStatus.RUNNING);
        check(pausedStep.getStatus()).is(JobStatus.RUNNING);
        check(step2.getStatus()).is(JobStatus.WAITING);
        unlock(future);
        check(job.getStatus()).is(JobStatus.SUCCESS);
        check(pausedStep.getStatus()).is(JobStatus.SUCCESS);
        check(step2.getStatus()).is(JobStatus.SUCCESS);
    }

    @Test
    void runningJob() {
        job.addStep(step1);
        job.addStep(pausedStep);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING);
        check(job.getStatus()).is(JobStatus.RUNNING);
        check(step1.getStatus()).is(JobStatus.SUCCESS);
        check(pausedStep.getStatus()).is(JobStatus.RUNNING);
        unlock(future);
        check(job.getStatus()).is(JobStatus.SUCCESS);
        check(step1.getStatus()).is(JobStatus.SUCCESS);
        check(pausedStep.getStatus()).is(JobStatus.SUCCESS);
    }

    @Test
    void errorJob() {
        job.addStep(errorStep);
        job.addStep(step2);

        IllegalStateException exception = assertThrows(IllegalStateException.class, job::run);

        check(exception.getMessage()).is("Test error");
        check(job.getStatus()).is(JobStatus.ERROR);
        check(errorStep.getStatus()).is(JobStatus.ERROR);
        check(step2.getStatus()).is(JobStatus.READY);
    }

    @Test
    void rollbackJob() {
        job.addStep(step1);
        job.addStep(pausedStep);
        paused.set(false);
        job.run();
        paused.set(true);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::rollback);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING, JobStatus.SUCCESS, JobStatus.WAITING, JobStatus.ROLLBACK);
        check(job.getStatus()).is(JobStatus.ROLLBACK);
        check(step1.getStatus()).is(JobStatus.WAITING);
        check(pausedStep.getStatus()).is(JobStatus.ROLLBACK);
        unlock(future);
        check(job.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step1.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
        check(pausedStep.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    @Test
    void errorRollbackJob() {
        job.addStep(step1);
        job.addStep(errorStep);
        error.set(false);
        job.run();
        error.set(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, job::rollback);

        check(exception.getMessage()).is("Test error");
        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING, JobStatus.SUCCESS, JobStatus.WAITING, JobStatus.ROLLBACK, JobStatus.ERROR_ROLLBACK);
        check(job.getStatus()).is(JobStatus.ERROR_ROLLBACK);
        check(step1.getStatus()).is(JobStatus.SUCCESS);
        check(errorStep.getStatus()).is(JobStatus.ERROR_ROLLBACK);
    }

    @Test
    void invalidJob() {
        job.addStep(step1);
        job.addStep(invalidStep);

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID);
        check(job.getStatus()).is(JobStatus.INVALID);
        check(step1.getStatus()).is(JobStatus.READY);
        check(invalidStep.getStatus()).is(JobStatus.INVALID);
    }

    @Test
    void validJob() {
        job.addStep(step1);
        job.addStep(invalidStep);
        invalidStepRunner.makeValid();
        invalidStep.validate();

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID, JobStatus.READY);
        check(job.getStatus()).is(JobStatus.READY);
        check(step1.getStatus()).is(JobStatus.READY);
        check(invalidStep.getStatus()).is(JobStatus.READY);
    }

    @Test
    void retryRun() {
        job.addStep(errorStep);
        job.addStep(step2);
        assertThrows(IllegalStateException.class, job::run);
        error.set(false);

        job.run();

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.ERROR,
                JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.SUCCESS);
        check(job.getStatus()).is(JobStatus.SUCCESS);
    }

    @Test
    void retryRollback() {
        job.addStep(errorStep);
        job.addStep(step2);
        error.set(false);
        job.run();
        error.set(true);
        assertThrows(IllegalStateException.class, job::rollback);
        error.set(false);

        job.rollback();

        checkJobStatusChanges(JobStatus.READY,
                JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.SUCCESS,
                JobStatus.WAITING, JobStatus.ROLLBACK, JobStatus.WAITING, JobStatus.ROLLBACK,
                JobStatus.ERROR_ROLLBACK,
                JobStatus.WAITING, JobStatus.ROLLBACK, JobStatus.SUCCESS_ROLLBACK);
        check(job.getStatus()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    private void unlock(CompletableFuture<?> future) {
        paused.set(false);
        future.join();
    }

    private void checkJobStatusChanges(JobStatus... statuses) {
        ArgumentCaptor<JobStatus> captor = ArgumentCaptor.forClass(JobStatus.class);
        await().until(() -> mockingDetails(listener).getInvocations().size(),
                size -> size == statuses.length);
        verify(listener, times(statuses.length)).accept(captor.capture());
        check(captor.getAllValues()).is(statuses);
    }
}