package software.plusminus.job;

import javax.annotation.Nullable;

public interface StepRunner<T> {

    T run();

    @Nullable
    Runnable rollback();

    void status(JobStatus status);

    default boolean validate() {
        return ValidationUtils.validate(this).isEmpty();
    }
}
