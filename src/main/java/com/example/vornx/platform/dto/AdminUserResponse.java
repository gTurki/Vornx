package com.example.vornx.platform.dto;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;

/** What the API returns for an admin. Notice: no passwordHash. */
public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        AdminRole role,
        AccountStatus status
) {
    public static AdminUserResponse from(AdminUser u) {
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus());
    }
}
