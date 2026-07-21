# Plusminus Job

The implementation of running, re-running, rolling back, and performing other actions on multi-step jobs.

A job is an ordered list of steps. Each step wraps a run action, an optional rollback action, an optional
validator, and an optional status listener. Steps and the job itself move through the `JobStatus` lifecycle
(`READY`, `WAITING`, `RUNNING`, `SUCCESS`, `ERROR`, `SKIPPED`, `INVALID`, rollback statuses and more), and
every status permits only specific `JobAction`s — illegal transitions throw `IllegalStateException`.

## Features

- Run all steps of a job in order; a re-run executes only the steps that have not completed yet (skipped
  and already completed steps are excluded).
- Roll back completed steps in reverse order; steps without a rollback action are reported as
  `NO_ROLLBACK`, and a mix of both kinds yields `PARTIAL_ROLLBACK`.
- Skip and unskip individual steps, or remove/replace steps that have not progressed yet.
- Validate steps before running — with a custom `Supplier<Boolean>`, or by default for `StepRunner`
  implementations with JSR-380 bean validation (Hibernate Validator).
- Observe status changes of a step or the whole job via `Consumer<JobStatus>` listeners; the job status
  is derived from the statuses of its steps.

## Usage

Compose a job from lambda-based steps, run it, and roll back on error:

```java
Job job = new Job();
job.addStep(new Step<>(this::createUser, this::deleteUser, null, null));
job.addStep(Step.of(this::sendEmail));

job.run();
if (job.getStatus() == JobStatus.ERROR) {
    job.rollback();
}
```

For reusable steps, implement the `StepRunner` interface:

```java
public class CreateUserStep implements StepRunner<User> {

    @Override
    public User run() { /* perform the action and return its result */ }

    @Override
    public Runnable rollback() { return () -> { /* undo the action */ }; }

    @Override
    public void status(JobStatus status) { /* react to status changes */ }
}

Step<User> step = Step.of(new CreateUserStep());
```

## Getting started

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>software.plusminus</groupId>
    <artifactId>plusminus-job</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Building

The project requires JDK 8 and builds with the Maven wrapper:

```bash
./mvnw clean install
```

The build enforces Checkstyle, PMD, SpotBugs and JaCoCo coverage checks.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
