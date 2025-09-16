package software.plusminus.job;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum JobStatus {

    SKIPPED(0),
    READY(1, JobAction.SKIP, JobAction.WAIT, JobAction.RUN, JobAction.VALIDATE),
    SUCCESS(2, JobAction.SKIP, JobAction.WAIT, JobAction.ROLLBACK, JobAction.VALIDATE),
    SUCCESS_ROLLBACK(3),
    NO_ROLLBACK(4),
    PARTIAL_ROLLBACK(5),
    WAITING(6, JobAction.RUN, JobAction.ROLLBACK),
    RUNNING(7),
    ERROR(8, JobAction.SKIP, JobAction.WAIT, JobAction.RUN, JobAction.ROLLBACK, JobAction.VALIDATE),
    ROLLBACK(9),
    ERROR_ROLLBACK(10, JobAction.SKIP, JobAction.WAIT, JobAction.ROLLBACK, JobAction.VALIDATE),
    INVALID(11, JobAction.SKIP, JobAction.VALIDATE);

    static final Set<JobStatus> SAVED_STATUSES = new HashSet<>(Arrays.asList(
            JobStatus.SUCCESS, JobStatus.ERROR,
            JobStatus.SUCCESS_ROLLBACK, JobStatus.PARTIAL_ROLLBACK, JobStatus.ERROR_ROLLBACK));

    private final int priority;
    private final Set<JobAction> allowedActions;

    JobStatus(int priority, JobAction... allowedActions) {
        this.priority = priority;
        this.allowedActions = Stream.of(allowedActions)
                .collect(Collectors.toSet());
    }

    public void checkAction(JobAction action) {
        boolean isAllowed = allowedActions.contains(action);
        if (!isAllowed) {
            throw new IllegalStateException("The action " + action
                    + " is not allowed for status " + this);
        }
    }

    public static JobStatus max(Collection<Step<?>> steps, JobStatus defaultValue) {
        return steps.stream()
                .map(Step::getStatus)
                .max(Comparator.comparingInt(status -> status.priority))
                .map(status -> handleNoRollbackStatus(steps, status))
                .orElse(defaultValue);
    }

    private static JobStatus handleNoRollbackStatus(Collection<Step<?>> steps, JobStatus currentStatus) {
        if (currentStatus != NO_ROLLBACK) {
            return currentStatus;
        }
        boolean allNoRollback = steps.stream()
                .map(Step::getStatus)
                .allMatch(s -> s == NO_ROLLBACK);
        if (allNoRollback) {
            return NO_ROLLBACK;
        } else {
            return PARTIAL_ROLLBACK;
        }
    }
}
