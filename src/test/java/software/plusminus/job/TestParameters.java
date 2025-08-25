package software.plusminus.job;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;

@Data
public class TestParameters {

    @NotEmpty
    private String string;

    @Positive
    private int number;

}
