package software.plusminus.job;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class Job {

    private List<Step<?, ?>> steps = new ArrayList<>();
    private List<Step<?, ?>> progress = new ArrayList<>();
    @Nullable
    private Consumer<JobStatus> listener;
    private JobStatus status = JobStatus.INVALID;

    public Job() {
        this(null);
    }

    public Job(@Nullable Consumer<JobStatus> listener) {
        this.listener = listener;
    }

    public <T> Step.StepBuilder<T, Void> createStep(Consumer<T> consumer) {
        return createStep(t -> {
            consumer.accept(t);
            return null;
        });
    }

    public <R> Step.StepBuilder<Void, R> createStep(Supplier<R> supplier) {
        return createStep(t -> {
            return supplier.get();
        });
    }

    public Step.StepBuilder<Void, Void> createStep(Runnable runnable) {
        return createStep(t -> {
            runnable.run();
            return null;
        });
    }

    public <T, R> Step.StepBuilder<T, R> createStep(Function<T, R> function) {
        return Step.of(function).job(this);
    }

    public void removeStep(Step<?, ?> step) {
        checkProgressIsEmpty();
        boolean removed = steps.remove(step);
        if (!removed) {
            throw new IllegalArgumentException("Cannot remove unknown step");
        }
    }

    public void replaceStep(Step<?, ?> from, Step<?, ?> to) {
        checkProgressIsEmpty();
        int indexFrom = steps.indexOf(from);
        if (indexFrom == -1) {
            throw new IllegalArgumentException("Cannot replace unknown step");
        }
        int indexTo = steps.indexOf(to);
        steps.set(indexFrom, to);
        if (indexTo != -1) {
            steps.remove(indexTo);
        }
    }

    public void run() {
        List<Step<?, ?>> stepsToRun = stepsToRun();
        start(JobAction.RUN, stepsToRun);
        try {
            steps.forEach(Step::run);
        } finally {
            end(stepsToRun);
        }
    }

    public void rollback() {
        start(JobAction.ROLLBACK, progress);
        try {
            while (!progress.isEmpty()) {
                int index = progress.size() - 1;
                Step<?, ?> step = progress.get(index);
                step.rollback();
                progress.remove(index);
            }
        } finally {
            end(progress);
        }
    }

    public JobStatus status() {
        return status;
    }

    void addStep(Step<?, ?> step) {
        checkProgressIsEmpty();
        if (steps.contains(step)) {
            return;
        }
        steps.add(step);
    }

    void addProgress(Step<?, ?> step) {
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

    private void start(JobAction action, List<Step<?, ?>> stepsToProcess) {
        stepsToProcess.forEach(Step::validate);
        calculateStatus();
        status.checkAction(action);
        stepsToProcess.forEach(Step::waiting);
    }

    private void end(List<Step<?, ?>> stepsToProcess) {
        stepsToProcess.forEach(Step::unwaiting);
    }

    private void calculateStatus() {
        JobStatus calculatedStatus = JobStatus.max(steps, JobStatus.READY);
        changeStatus(calculatedStatus);
    }

    private List<Step<?, ?>> stepsToRun() {
        return steps.stream()
                .filter(step -> step.status() != JobStatus.SKIPPED)
                .filter(step -> !progress.contains(step))
                .collect(Collectors.toList());
    }

    private void checkProgressIsEmpty() {
        if (!progress.isEmpty()) {
            throw new IllegalStateException("Can't change steps: there is progress on job");
        }
    }

}
