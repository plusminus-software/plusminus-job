package software.plusminus.job.steps;

import software.plusminus.job.TestParameters;

import javax.validation.Valid;

public class InvalidStep extends AbstractStep {

    @Valid
    private TestParameters parameters = new TestParameters();

    public void makeValid() {
        parameters.setString("correct string");
        parameters.setNumber(1);
    }

    public void makeInvalid() {
        parameters.setString(null);
        parameters.setNumber(-1);
    }
}
