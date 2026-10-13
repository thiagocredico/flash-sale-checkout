package br.com.ada.flashsale.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateOrderRequestTest {

    private static Validator validator;
    private static jakarta.validation.ValidatorFactory validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRequest() {
        var request = new CreateOrderRequest("PROD-001", 2, "50329291076", "Fernando Augusto");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankFieldsAndNonPositiveQuantity() {
        var request = new CreateOrderRequest(" ", 0, "", " ");

        assertThat(validator.validate(request)).hasSize(4);
    }
}