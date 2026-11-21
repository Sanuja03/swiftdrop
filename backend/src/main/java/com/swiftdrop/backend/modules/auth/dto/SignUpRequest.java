package com.swiftdrop.backend.modules.auth.dto;

import com.swiftdrop.backend.modules.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,16}$";
    public static final String MOBILE_REGEX = "^\\s*(?:\\+94[\\s-]?|0)7[01245678](?:[\\s-]?\\d){7}\\s*$";

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 16, message = "Password must be between 8 and 16 characters")
    @Pattern(
            regexp = PASSWORD_REGEX,
            message = "Password must be 8-16 characters and include at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(
            regexp = MOBILE_REGEX,
            message = "Please enter a valid Sri Lankan mobile number (e.g. 077 123 4567 or +94 77 123 4567)"
    )
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

