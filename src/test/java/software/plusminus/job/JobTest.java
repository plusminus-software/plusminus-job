package software.plusminus.job;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static software.plusminus.check.Checks.check;

class JobTest {

    private AtomicBoolean paused = new AtomicBoolean(false);
    private AtomicBoolean error = new AtomicBoolean(true);
    private Consumer<JobStatus> listener = mock(Consumer.class);
    private ArgumentCaptor<JobStatus> captor = ArgumentCaptor.forClass(JobStatus.class);
    private Job job = new Job(listener);
    private Step<?, ?> step1 = job.createStep(this::notPausedFunction)
            .rollback((a, b) -> notPausedFunction())
            .build();
    private Step<?, ?> step2 = job.createStep(this::pausedFunction)
            .rollback((a, b) -> pausedFunction())
            .build();

    @Test
    void emptyJob() {
        job = new Job();
        check(job.status()).is(JobStatus.INVALID);
    }

    @Test
    void skippedJob() {
        step1.skip();
        step2.skip();

        check(job.status()).is(JobStatus.SKIPPED);
        check(step1.status()).is(JobStatus.SKIPPED);
        check(step2.status()).is(JobStatus.SKIPPED);
    }

    @Test
    void readyJob() {
        checkJobStatusChanges(JobStatus.READY);
        check(job.status()).is(JobStatus.READY);
        check(step1.status()).is(JobStatus.READY);
        check(step2.status()).is(JobStatus.READY);
    }

    @Test
    void successJob() {
        job.run();
        check(job.status()).is(JobStatus.SUCCESS);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(step2.status()).is(JobStatus.SUCCESS);
    }

    @Test
    void successRollbackJob() {
        job.run();
        job.rollback();

        check(job.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step2.status()).is(JobStatus.SUCCESS_ROLLBACK);
    }

    @Test
    void partialRollbackJob() {
        Step<?, ?> toReplace = step2;
        step2 = job.createStep(this::pausedFunction)
                .rollback(null)
                .build();
        job.replaceStep(toReplace, step2);

        job.run();
        job.rollback();

        check(job.status()).is(JobStatus.PARTIAL_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS_ROLLBACK);
        check(step2.status()).is(JobStatus.PARTIAL_ROLLBACK);
    }

    @Test
    void waitingJob() {
        replaceStep1(this::pausedFunction);
        paused.set(true);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING);
        check(job.status()).is(JobStatus.RUNNING);
        check(step1.status()).is(JobStatus.RUNNING);
        check(step2.status()).is(JobStatus.WAITING);
        unlock(future);
    }

    @Test
    void runningJob() {
        paused.set(true);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::run);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING);
        check(job.status()).is(JobStatus.RUNNING);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(step2.status()).is(JobStatus.RUNNING);
        unlock(future);
    }

    @Test
    void errorJob() {
        replaceStep1(this::errorFunction);

        IllegalStateException exception = assertThrows(IllegalStateException.class, job::run);

        check(exception.getMessage()).is("Test error");
        check(job.status()).is(JobStatus.ERROR);
        check(step1.status()).is(JobStatus.ERROR);
        check(step2.status()).is(JobStatus.READY);
    }

    @Test
    void rollbackJob() {
        job.run();
        paused.set(true);

        CompletableFuture<?> future = CompletableFuture.runAsync(job::rollback);

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING, JobStatus.SUCCESS, JobStatus.WAITING, JobStatus.ROLLBACK);
        check(job.status()).is(JobStatus.ROLLBACK);
        check(step1.status()).is(JobStatus.WAITING);
        check(step2.status()).is(JobStatus.ROLLBACK);
        unlock(future);
    }

    @Test
    void errorRollbackJob() {
        Step<?, ?> toReplace = step2;
        step2 = job.createStep(this::pausedFunction)
                .rollback((a, b) -> this.errorFunction())
                .build();
        job.replaceStep(toReplace, step2);
        job.run();

        IllegalStateException exception = assertThrows(IllegalStateException.class, job::rollback);

        check(exception.getMessage()).is("Test error");
        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING,
                JobStatus.RUNNING, JobStatus.SUCCESS, JobStatus.WAITING, JobStatus.ROLLBACK, JobStatus.ERROR_ROLLBACK);
        check(job.status()).is(JobStatus.ERROR_ROLLBACK);
        check(step1.status()).is(JobStatus.SUCCESS);
        check(step2.status()).is(JobStatus.ERROR_ROLLBACK);
    }

    @Test
    void invalidJob() {
        clearInvocations(listener);
        job = new Job(listener);
        step1 = job.createStep(p -> { })
                .parameters(validParameters())
                .build();
        step2 = job.createStep(p -> { })
                .parameters(invalidParameters())
                .build();

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID);
        check(job.status()).is(JobStatus.INVALID);
        check(step1.status()).is(JobStatus.READY);
        check(step2.status()).is(JobStatus.INVALID);
    }

    @Test
    void validJob() {
        TestParameters parameters = invalidParameters();
        clearInvocations(listener);
        job = new Job(listener);
        step1 = job.createStep(p -> { })
                .parameters(validParameters())
                .build();
        step2 = job.createStep(p -> { })
                .parameters(parameters)
                .build();

        parameters.setString("correct string");
        parameters.setNumber(10);
        step2.validate();

        checkJobStatusChanges(JobStatus.READY, JobStatus.INVALID, JobStatus.READY);
        check(job.status()).is(JobStatus.READY);
        check(step1.status()).is(JobStatus.READY);
        check(step2.status()).is(JobStatus.READY);
    }

    @Test
    void retryRun() {
        replaceStep1(this::errorFunction);
        assertThrows(IllegalStateException.class, job::run);
        error.set(false);

        job.run();

        checkJobStatusChanges(JobStatus.READY, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.ERROR,
                JobStatus.WAITING, JobStatus.RUNNING, JobStatus.WAITING, JobStatus.RUNNING, JobStatus.SUCCESS);
        check(job.status()).is(JobStatus.SUCCESS);
    }

    @Test
    void retryRollback() {
        replaceStep1(this::errorFunction);
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

    private TestParameters validParameters() {
        TestParameters parameters = new TestParameters();
        parameters.setString("someString");
        parameters.setNumber(42);
        return parameters;
    }

    private TestParameters invalidParameters() {
        return new TestParameters();
    }

    private void pausedFunction() {
        await().until(() -> !paused.get());
    }

    private void notPausedFunction() {
        // Just empty function
    }

    private void errorFunction() {
        if (error.get()) {
            throw new IllegalStateException("Test error");
        }
    }

    private void unlock(CompletableFuture<?> future) {
        paused.set(false);
        future.join();
    }

    private void replaceStep1(Runnable runnable) {
        Step<?, ?> toReplace = step1;
        step1 = job.createStep(runnable)
                .rollback((a, b) -> runnable.run())
                .build();
        job.replaceStep(toReplace, step1);
    }

    private void checkJobStatusChanges(JobStatus... statuses) {
        await().until(() -> mockingDetails(listener).getInvocations().size(),
                size -> size == statuses.length);
        verify(listener, times(statuses.length)).accept(captor.capture());
        check(captor.getAllValues()).is(statuses);
    }
}