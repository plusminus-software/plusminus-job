package software.plusminus.job;

import lombok.Builder;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

@Builder(builderMethodName = "of")
public class Step<T, R> {



    @Nullable
    private T parameters;
    private Function<T, R> function;
    @Nullable
    private Job job;
    @Nullable
    private BiConsumer<T, R> rollback;
    @Nullable
    private Consumer<JobStatus> listener;
    @Nullable
    private Consumer<R> handler;
    private JobStatus savedStatus;
    private JobStatus status;
    @Nullable
    private R lastResult;

    @Nullable
    public R run() {
        checkAction(JobAction.RUN);
        try {
            changeStatus(JobStatus.RUNNING);
            R result = function.apply(parameters);
            this.lastResult = result;
            changeStatus(JobStatus.SUCCESS);
            if (handler != null) {
                handler.accept(result);
            }
            if (job != null) {
                job.addProgress(this);
            }
            return result;
        } catch (Exception e) {
            changeStatus(JobStatus.ERROR);
            throw e;
        }
    }

    public void rollback() {
        checkAction(JobAction.ROLLBACK);
        if (rollback == null) {
            changeStatus(JobStatus.PARTIAL_ROLLBACK);
            return;
        }
        try {
            changeStatus(JobStatus.ROLLBACK);
            rollback.accept(parameters, lastResult);
            this.lastResult = null;
            changeStatus(JobStatus.SUCCESS_ROLLBACK);
        } catch (Exception e) {
            changeStatus(JobStatus.ERROR_ROLLBACK);
            throw e;
        }
    }

    public void validate() {
        checkAction(JobAction.VALIDATE);
        if (parameters == null) {
            return;
        }
        changeStatus(isValid() ? savedStatus : JobStatus.INVALID);
    }

    public JobStatus status() {
        return status;
    }

    public void skip() {
        checkAction(JobAction.SKIP);
        changeStatus(JobStatus.SKIPPED);
    }

    public void unskip() {
        if (status == JobStatus.SKIPPED) {
            changeStatus(isValid() ? savedStatus : JobStatus.INVALID);
        }
    }

    void waiting() {
        checkAction(JobAction.WAIT);
        changeStatus(JobStatus.WAITING);
    }

    void unwaiting() {
        if (status == JobStatus.WAITING) {
            changeStatus(isValid() ? savedStatus : JobStatus.INVALID);
        }
    }

    private void init() {
        changeStatus(isValid() ? JobStatus.READY : JobStatus.INVALID);
        savedStatus = JobStatus.READY;
        if (job != null) {
            job.addStep(this);
        }
    }

    private void checkAction(JobAction action) {
        status.checkAction(action);
    }

    private void changeStatus(JobStatus newStatus) {
        if (status == newStatus) {
            return;
        }
        if (JobStatus.SAVED_STATUSES.contains(newStatus)) {
            savedStatus = newStatus;
        }
        status = newStatus;
        if (listener != null) {
            listener.accept(newStatus);
        }
        if (job != null) {
            job.listenStep(newStatus);
        }
    }

    private boolean isValid() {
        if (parameters == null) {
            return true;
        }
        return ValidationUtils.validate(parameters).isEmpty();
    }

    static <T, R> StepBuilder<T, R> of(Function<T, R> function) {
        return new StepBuilder<T, R>().function(function);
    }

    public static class StepBuilder<T, R> {

        public Step<T, R> build() {
            Step<T, R> step = new Step<>(parameters, function,
                    job, rollback, listener, handler,
                    savedStatus, status, lastResult);
            step.init();
            return step;
        }

        @SuppressWarnings("java:S1144")
        private StepBuilder<T, R> savedStatus(JobStatus savedStatus) {
            this.savedStatus = savedStatus;
            return this;
        }

        @SuppressWarnings("java:S1144")
        private StepBuilder<T, R> status(JobStatus status) {
            this.status = status;
            return this;
        }

        @SuppressWarnings("java:S1144")
        private StepBuilder<T, R> lastResult(R lastResult) {
            this.lastResult = lastResult;
            return this;
        }

        StepBuilder<T, R> job(Job job) {
            this.job = job;
            return this;
        }
    }
}
