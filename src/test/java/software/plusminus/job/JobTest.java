package software.plusminus.job;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.plusminus.job.steps.ErrorStep;
import software.plusminus.job.steps.InvalidStep;
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

class JobTest {

    private AtomicBoolean paused = new AtomicBoolean(true);
    private AtomicBoolean error = new AtomicBoolean(true);
    private Consumer<JobStatus> listener = mock(Consumer.class);
    private ArgumentCaptor<JobStatus> captor = ArgumentCaptor.forClass(JobStatus.class);
    private Job job = new Job(listener);
    private NotPausedStep step1 = new NotPausedStep();
    private NotPausedStep step2 = new NotPausedStep();
    private PausedStep pausedStep = new PausedStep(paused);
    private ErrorStep errorStep = new ErrorStep(error);
    private InvalidStep invalidStep = new InvalidStep();

    @Test
    void emptyJob() {
        job = new Job();
        check(job.status()).is(JobStatus.INVALID);
    }

    @Test
    void skippedJob() {
        job.addStep(step1);
        job.addStep(step2);
        job.skip(step1);
        job.skip(step2);

        checkJobStatusChanges(JobStatus.READY, JobStatus.SKIPPED);
        check(job.status()).is(JobStatus.SKIPPED);
        check(step1.status()).is(JobStatus.SKIPPED);
        check(step2.status()).is(JobStatus.SKIPPED);
    }

    @Test
    void readyJob() {
        job.addStep(step1);
        job.addStep(step2);

        checkJobStatusChanges(JobStatus.READY);
        check(job.status()).is(JobStatus.READY);
        check(step1.status()).is(JobStatus.READY);
        check(step2.status()).is(JobStatus.READY);
    }

    @Test
    void successJob() {
        job.addStep(step1);
        job.addStep(step2);

        job.run();

        check(job.status()).is(JobStatus.SUCCESS);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(step2.status()).is(JobStatus.SUCCESS);
    }

    @Test
    void successRollbackJob() {
        job.addStep(step1);
        job.addStep(step2);

        job.run();
        job.rollback();

        check(job.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step2.status()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    @Test
    void partialRollbackJob() {
        job.addStep(step1);
        job.addStep(step2);
        step2.rollback(false);

        job.run();
        job.rollback();

        check(job.status()).is(JobStatus.PARTIAL_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step2.status()).is(JobStatus.PARTIAL_ROLLBACK);
    }

    @Test
    void waitingJob() {
        job.addStep(pausedStep);
        job.addStep(step2);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING);
        check(job.status()).is(JobStatus.RUNNING);
        check(pausedStep.status()).is(JobStatus.RUNNING);
        check(step2.status()).is(JobStatus.WAITING);
        unlock(future);
        check(job.status()).is(JobStatus.SUCCESS);
        check(pausedStep.status()).is(JobStatus.SUCCESS);
        check(step2.status()).is(JobStatus.SUCCESS);
    }

    @Test
    void runningJob() {
        job.addStep(step1);
        job.addStep(pausedStep);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING);
        check(job.status()).is(JobStatus.RUNNING);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(pausedStep.status()).is(JobStatus.RUNNING);
        unlock(future);
        check(job.status()).is(JobStatus.SUCCESS);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(pausedStep.status()).is(JobStatus.SUCCESS);
    }

    @Test
    void errorJob() {
        job.addStep(errorStep);
        job.addStep(step2);

        IllegalStateException exception = assertThrows(IllegalStateException.class, job::run);

        check(exception.getMessage()).is("Test error");
        check(job.status()).is(JobStatus.ERROR);
        check(errorStep.status()).is(JobStatus.ERROR);
        check(step2.status()).is(JobStatus.READY);
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
        check(job.status()).is(JobStatus.ROLLBACK);
        check(step1.status()).is(JobStatus.WAITING);
        check(pausedStep.status()).is(JobStatus.ROLLBACK);
        unlock(future);
        check(job.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(pausedStep.status()).is(JobStatus.SUCCESS_ROLLBACK);
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
        check(job.status()).is(JobStatus.ERROR_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(errorStep.status()).is(JobStatus.ERROR_ROLLBACK);
    }

    @Test
    void invalidJob() {
        job.addStep(step1);
        job.addStep(invalidStep);

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID);
        check(job.status()).is(JobStatus.INVALID);
        check(step1.status()).is(JobStatus.READY);
        check(invalidStep.status()).is(JobStatus.INVALID);
    }

    @Test
    void validJob() {
        job.addStep(step1);
        job.addStep(invalidStep);
        invalidStep.makeValid();
        job.validate(invalidStep);

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID, JobStatus.READY);
        check(job.status()).is(JobStatus.READY);
        check(step1.status()).is(JobStatus.READY);
        check(invalidStep.status()).is(JobStatus.READY);
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
        check(job.status()).is(JobStatus.SUCCESS);
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
        check(job.status()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    private void unlock(CompletableFuture<?> future) {
        paused.set(false);
        future.join();
    }

    private void checkJobStatusChanges(JobStatus... statuses) {
        await().until(() -> mockingDetails(listener).getInvocations().size(),
                size -> size == statuses.length);
        verify(listener, times(statuses.length)).accept(captor.capture());
        check(captor.getAllValues()).is(statuses);
    }
}