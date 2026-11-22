package com.swiftdrop.backend.modules.courier.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProofInputValidatorTest {

    private final ProofInputValidator validator =
            new ProofInputValidator();

    @Test
    void removesSpacesAroundRecipientName() {
        assertEquals(
                "Kamal Perera",
                validator.validateRecipientName("  Kamal Perera  ")
        );
    }

    @Test
    void rejectsMissingRecipientName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRecipientName(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRecipientName("   ")
        );
    }

    @Test
    void checksRecipientNameLengthLimit() {
        String allowedName = "A".repeat(150);

        assertEquals(
                allowedName,
                validator.validateRecipientName(allowedName)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRecipientName("A".repeat(151))
        );
    }

    @Test
    void checksCorrectionReason() {
        assertEquals(
                "Corrected recipient name",
                validator.validateChangeReason(
                        "  Corrected recipient name  "
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateChangeReason("   ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateChangeReason("A".repeat(501))
        );
    }
}