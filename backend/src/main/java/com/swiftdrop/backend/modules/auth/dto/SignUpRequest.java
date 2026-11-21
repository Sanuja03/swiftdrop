package com.swiftdrop.backend.modules.auth.dto;

import com.swiftdrop.backend.modules.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phone;

    private Role role; // Nullable; defaults to ROLE_CUSTOMER if not supplied

    @Size(max = 255)
    private String businessName;

    @Size(max = 100)
    private String registrationNumber;

    private String businessAddress;

    /** Public signup creates customers and merchants only; staff roles are never self-assigned. */
    @AssertTrue(message = "Sign up as a customer or a merchant")
    public boolean isSelfServiceRole() {
        return role == null || role == Role.ROLE_CUSTOMER || role == Role.ROLE_MERCHANT;
    }

    @AssertTrue(message = "Merchant signup requires business name, business registration number, contact phone and business address")
    public boolean isMerchantDetailsComplete() {
        return role != Role.ROLE_MERCHANT || (hasText(businessName) && hasText(registrationNumber) && hasText(phone) && hasText(businessAddress));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

