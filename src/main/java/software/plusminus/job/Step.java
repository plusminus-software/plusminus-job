package software.plusminus.job;

import lombok.Getter;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class Step<T> {

    private Supplier<T> run;
    private Supplier<Runnable> rollback;
    @Nullable
    private Supplier<Boolean> validator;
    @Nullable
    private Consumer<JobStatus> listener;

    @Getter
    @Nullable
    private volatile T result;
    private volatile JobStatus savedStatus;
    @Getter
    private volatile JobStatus status;
    @Nullable
    private Job job;

    public Step(Supplier<T> run,
                @Nullable Runnable rollback,
                @Nullable Supplier<Boolean> validator,
                @Nullable Consumer<JobStatus> listener) {
        this(run, () -> rollback, validator, listener);
    }

    private Step(Supplier<T> run,
                 Supplier<Runnable> rollback,
                 @Nullable Supplier<Boolean> validator,
                 @Nullable Consumer<JobStatus> listener) {
        this.run = run;
        this.rollback = rollback;
        this.validator = validator;
        this.listener = listener;
        savedStatus = JobStatus.READY;
        validate(null);
    }

    public T run() {
        checkAction(JobAction.RUN);
        try {
            changeStatus(JobStatus.RUNNING);
            result = run.get();
            changeStatus(JobStatus.SUCCESS);
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
        try {
            changeStatus(JobStatus.ROLLBACK);
            Runnable rollbackAction = rollback.get();
            if (rollbackAction != null) {
                rollbackAction.run();
                changeStatus(JobStatus.SUCCESS_ROLLBACK);
            } else {
                changeStatus(JobStatus.NO_ROLLBACK);
            }
        } catch (Exception e) {
            changeStatus(JobStatus.ERROR_ROLLBACK);
            throw e;
        }
    }

    public void validate() {
        validate(JobAction.VALIDATE);
    }

    private void validate(@Nullable JobAction jobAction) {
        if (validator == null) {
            changeStatus(savedStatus);
            return;
        }
        boolean isValid = validator.get();
        if (jobAction != null) {
            checkAction(jobAction);
        }
        changeStatus(isValid ? savedStatus : JobStatus.INVALID);
    }

    public void skip() {
        checkAction(JobAction.SKIP);
        changeStatus(JobStatus.SKIPPED);
    }

    public void unskip() {
        if (status == JobStatus.SKIPPED) {
            validate(null);
        }
    }

    void waiting() {
        checkAction(JobAction.WAIT);
        changeStatus(JobStatus.WAITING);
    }

    void unwaiting() {
        if (status == JobStatus.WAITING) {
            validate(null);
        }
    }

    @SuppressWarnings("checkstyle:HiddenField")
    void link(Job job) {
        this.job = job;
        job.listenStep(status);
    }

    void unlink() {
        job = null;
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

    private void checkAction(JobAction action) {
        status.checkAction(action);
    }

    public static <T> Step<T> of(Supplier<T> run) {
        return new Step<>(run, (Runnable) null, null, null);
    }

    public static <T> Step<T> of(Supplier<T> run,
                                 Supplier<Boolean> validator) {
        return new Step<>(run, (Runnable) null, validator, null);
    }

    public static <T> Step<T> of(StepRunner<T> runner) {
        Supplier<Runnable> rollbackSupplier = runner::rollback;
        return new Step<>(runner::run, rollbackSupplier, runner::validate, runner::status);
    }
}
