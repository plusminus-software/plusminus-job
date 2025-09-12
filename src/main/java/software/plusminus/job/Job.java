package software.plusminus.job;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class Job {

    private List<StepController<?>> steps = new ArrayList<>();
    private List<StepController<?>> progress = new ArrayList<>();
    @Nullable
    private Consumer<JobStatus> listener;
    private JobStatus status = JobStatus.INVALID;

    public Job() {
        this(null);
    }

    public Job(@Nullable Consumer<JobStatus> listener) {
        this.listener = listener;
    }

    public <T> void addStep(Step<T> step) {
        checkIsNotPresent(steps, step, "Cannot add step: already present in job");
        StepController<T> controller = StepController.of(step);
        steps.add(controller);
        controller.link(this);
    }

    public void removeStep(Step<?> step) {
        StepController<?> toRemove = checkIsPresent(steps, step, "Cannot remove step: not present in job");
        checkIsNotPresent(progress, step, "Cannot remove step: already present in job's progress");
        boolean result = steps.remove(toRemove);
        toRemove.unlink();
        if (!result) {
            throw new IllegalStateException("Cannot remove step: was already removed in a parallel thread");
        }
    }

    public void replaceStep(Step<?> from, Step<?> to) {
        StepController<?> fromController = checkIsPresent(steps, from,
                "Cannot replace steps: the 'from' step is not present in job");
        checkIsNotPresent(progress, from, "Cannot replace steps: "
                + "the 'from' step is already present in job's progress");
        checkIsNotPresent(progress, to, "Cannot replace steps: "
                + "the 'to' step is already present in job's progress");
        int index = steps.indexOf(fromController);
        if (index == -1) {
            throw new IllegalStateException("Cannot replace steps:"
                    + " the 'from' step was already removed in a parallel thread");
        }

        StepController<?> toController = find(steps, to);
        if (toController == null) {
            toController = StepController.of(to);
            steps.set(index, toController);
            toController.link(this);
        } else {
            steps.set(index, toController);
        }
        fromController.unlink();
    }

    public void skip(Step<?> step) {
        checkIsNotPresent(progress, step, "Cannot skip step: already present in job's progress");
        StepController<?> controller = checkIsPresent(steps, step, "Cannot skip step: not present in job");
        controller.skip();
    }

    public void unskip(Step<?> step) {
        checkIsNotPresent(progress, step, "Cannot unskip step: already present in job's progress");
        StepController<?> controller = checkIsPresent(steps, step, "Cannot unskip step: not present in job");
        controller.unskip();
    }

    public void run() {
        List<StepController<?>> stepsToRun = stepsToRun();
        start(JobAction.RUN, stepsToRun);
        try {
            stepsToRun.forEach(StepController::run);
        } finally {
            end(stepsToRun);
        }
    }

    public void rollback() {
        start(JobAction.ROLLBACK, progress);
        try {
            while (!progress.isEmpty()) {
                int index = progress.size() - 1;
                StepController<?> step = progress.get(index);
                step.rollback();
                progress.remove(index);
            }
        } finally {
            end(progress);
        }
    }

    public <T> void validate(Step<T> step) {
        StepController<?> controller = checkIsPresent(steps, step,
                "Cannot validate step: not present in job");
        controller.validate();
    }

    public JobStatus status() {
        return status;
    }

    void addProgress(StepController<?> step) {
        progress.add(step);
    }

    void listenStep(JobStatus stepStatus) {
        if (stepStatus == JobStatus.INVALID) {
            changeStatus(stepStatus);
        } else {
            calculateStatus();
        }
    }

    private void changeStatus(JobStatus newStatus) {
        if (status == newStatus) {
            return;
        }
        status = newStatus;
        if (listener != null) {
            listener.accept(status);
        }
    }

    private void start(JobAction action, List<StepController<?>> stepsToProcess) {
        stepsToProcess.forEach(StepController::validate);
        calculateStatus();
        status.checkAction(action);
        stepsToProcess.forEach(StepController::waiting);
    }

    private void end(List<StepController<?>> stepsToProcess) {
        stepsToProcess.forEach(StepController::unwaiting);
    }

    private void calculateStatus() {
        JobStatus calculatedStatus = JobStatus.max(steps, JobStatus.READY);
        changeStatus(calculatedStatus);
    }

    private List<StepController<?>> stepsToRun() {
        return steps.stream()
                .filter(step -> step.status() != JobStatus.SKIPPED)
                .filter(step -> !progress.contains(step))
                .collect(Collectors.toList());
    }

    @Nullable
    private StepController<?> find(List<StepController<?>> controllers, Step<?> step) {
        return controllers.stream()
                .filter(controller -> controller.step() == step)
                .findFirst()
                .orElse(null);
    }

    private void checkIsNotPresent(List<StepController<?>> controllers,
                                   Step<?> step,
                                   String errorMessage) {
        StepController<?> present = find(controllers, step);
        if (present != null) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private StepController<?> checkIsPresent(List<StepController<?>> controllers,
                                             Step<?> step,
                                             String errorMessage) {
        StepController<?> present = find(controllers, step);
        if (present == null) {
            throw new IllegalStateException(errorMessage);
        }
        return present;
    }
}
