package com.swiftdrop.backend.modules.auth.dto;

import com.swiftdrop.backend.modules.auth.entity.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SignUpRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private SignUpRequest validCustomerRequest() {
        SignUpRequest request = new SignUpRequest();
        request.setEmail("customer@swiftdrop.test");
        request.setPassword("TestPassword123!");
        request.setFullName("Test Customer");
        request.setPhone("077 123 4567");
        request.setRole(Role.ROLE_CUSTOMER);
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"TestPassword123!", "Abcdef1!", "P@ssw0rd12345678"})
    void validPasswordsPassValidation(String password) {
        SignUpRequest request = validCustomerRequest();
        request.setPassword(password);

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!xyz",              // too short (7 chars)
            "Abcdefgh1!1234567",    // too long (17 chars)
            "testpassword1!",       // missing uppercase
            "TESTPASSWORD1!",       // missing lowercase
            "TestPassword!!",       // missing digit
            "TestPassword12",       // missing special character
            "Test Pass123!"         // contains whitespace
    })
    void invalidPasswordsFailValidation(String password) {
        SignUpRequest request = validCustomerRequest();
        request.setPassword(password);

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(v -> "password".equals(v.getPropertyPath().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "077 123 4567", "+94 71 234 5678", "+94761234567", "070-123-4567"})
    void validSriLankanMobilesPassValidation(String phone) {
        SignUpRequest request = validCustomerRequest();
        request.setPhone(phone);

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "0000000000", "12345", "0112345678", "0731234567", "+94112345678", "07712345678"})
    void invalidMobilesFailValidation(String phone) {
        SignUpRequest request = validCustomerRequest();
        request.setPhone(phone);

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(v -> "phone".equals(v.getPropertyPath().toString()));
    }

    @Test
    void trimmedValidPhonePassesValidation() {
        SignUpRequest request = validCustomerRequest();
        request.setPhone("  077 123 4567  ");

        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
