package software.plusminus.job;

import lombok.experimental.UtilityClass;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

@UtilityClass
public class ValidationUtils {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();

    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    public <T> Set<ConstraintViolation<T>> validate(T object) {
        return VALIDATOR.validate(object);
    }
}
