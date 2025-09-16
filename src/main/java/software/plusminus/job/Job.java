package software.plusminus.job;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class Job {

    private List<Step<?>> steps = new ArrayList<>();
    private List<Step<?>> progress = new ArrayList<>();
    @Nullable
    private Consumer<JobStatus> listener;
    @Getter()
    private JobStatus status = JobStatus.INVALID;

    public Job() {
        this(null);
    }

    public Job(@Nullable Consumer<JobStatus> listener) {
        this.listener = listener;
    }

    public <T> void addStep(Step<T> step) {
        steps.add(step);
        step.link(this);
    }

    public boolean removeStep(Step<?> step) {
        checkIsNotProgressed(step, "Cannot remove step: already present in job's progress");
        boolean result = steps.remove(step);
        if (result) {
            step.unlink();
        }
        return result;
    }

    public boolean replaceStep(Step<?> from, Step<?> to) {
        checkIsNotProgressed(from, "Cannot replace steps: "
                + "the 'from' step is already present in job's progress");
        int index = steps.indexOf(from);
        if (index == -1) {
            return false;
        }
        steps.set(index, to);
        from.unlink();
        to.link(this);
        return true;
    }

    public void run() {
        List<Step<?>> stepsToRun = stepsToRun();
        start(JobAction.RUN, stepsToRun);
        try {
            stepsToRun.forEach(Step::run);
        } finally {
            end(stepsToRun);
        }
    }

    public void rollback() {
        start(JobAction.ROLLBACK, progress);
        try {
            while (!progress.isEmpty()) {
                int index = progress.size() - 1;
                Step<?> step = progress.get(index);
                step.rollback();
                progress.remove(index);
            }
        } finally {
            end(progress);
        }
    }

    void addProgress(Step<?> step) {
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

    private void start(JobAction action, List<Step<?>> stepsToProcess) {
        stepsToProcess.forEach(Step::validate);
        calculateStatus();
        status.checkAction(action);
        stepsToProcess.forEach(Step::waiting);
    }

    private void end(List<Step<?>> stepsToProcess) {
        stepsToProcess.forEach(Step::unwaiting);
    }

    private void calculateStatus() {
        JobStatus calculatedStatus = JobStatus.max(steps, JobStatus.READY);
        changeStatus(calculatedStatus);
    }

    private List<Step<?>> stepsToRun() {
        return steps.stream()
                .filter(step -> step.getStatus() != JobStatus.SKIPPED)
                .filter(step -> !progress.contains(step))
                .collect(Collectors.toList());
    }

    private void checkIsNotProgressed(Step<?> step, String errorMessage) {
        if (progress.contains(step)) {
            throw new IllegalStateException(errorMessage);
        }
    }
}
