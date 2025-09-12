package software.plusminus.job;

public interface Step<T> {

    T run();

    default boolean rollback() {
        return false;
    }

    default boolean validate() {
        return ValidationUtils.validate(this).isEmpty();
    }

    void status(JobStatus status);

}
