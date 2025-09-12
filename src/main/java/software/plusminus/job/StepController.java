package software.plusminus.job;

import javax.annotation.Nullable;

public class StepController<T> {

    @Nullable
    private Job job;
    private Step<T> step;
    private JobStatus savedStatus;
    private JobStatus status;

    public T run() {
        checkAction(JobAction.RUN);
        try {
            changeStatus(JobStatus.RUNNING);
            T result = step.run();
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
            boolean rollbackResult = step.rollback();
            JobStatus rollbackStatus = rollbackResult
                    ? JobStatus.SUCCESS_ROLLBACK
                    : JobStatus.PARTIAL_ROLLBACK;
            changeStatus(rollbackStatus);
        } catch (Exception e) {
            changeStatus(JobStatus.ERROR_ROLLBACK);
            throw e;
        }
    }

    public void validate() {
        setValidationResult(step.validate(), JobAction.VALIDATE);
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
            setValidationResult(step.validate(), null);
        }
    }

    void waiting() {
        checkAction(JobAction.WAIT);
        changeStatus(JobStatus.WAITING);
    }

    void unwaiting() {
        if (status == JobStatus.WAITING) {
            setValidationResult(step.validate(), null);
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

    Step<T> step() {
        return step;
    }

    private void init() {
        changeStatus(step.validate() ? JobStatus.READY : JobStatus.INVALID);
        savedStatus = JobStatus.READY;
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
        step.status(newStatus);
        if (job != null) {
            job.listenStep(newStatus);
        }
    }

    private void setValidationResult(boolean isValid, @Nullable JobAction jobAction) {
        if (jobAction != null) {
            checkAction(jobAction);
        }
        changeStatus(isValid ? savedStatus : JobStatus.INVALID);
    }

    public static <T> StepController<T> of(Step<T> step) {
        StepController<T> controller = new StepController<>();
        controller.step = step;
        controller.init();
        return controller;
    }
}
