package com.swiftdrop.backend.modules.dispatch.dto;

import com.swiftdrop.backend.modules.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveCourierResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phone;

    public static ActiveCourierResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return ActiveCourierResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}
