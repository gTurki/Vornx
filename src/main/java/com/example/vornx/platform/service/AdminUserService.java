package com.example.vornx.platform.service;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.platform.dto.AdminUserResponse;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.enums.AdminRole;
import com.example.vornx.platform.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;

    /** Used by the login. Returns the entity because AuthService must check the password hash. */
    public Optional<AdminUser> findByEmail(String email) {
        return adminUserRepository.findByEmail(email);
    }

    public AdminUserResponse getProfile(Long adminId) {
        return adminUserRepository.findById(adminId)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));
    }

    /** For other modules (support assignment, names on messages...). */
    public AdminUser getEntity(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));
    }

    /** For other modules (e.g. partner) that need to link to an admin who can manage accounts. */
    public AdminUser getAccountManager(Long adminId) {
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", adminId));
        if (admin.getRole() != AdminRole.ACCOUNT_MANAGER && admin.getRole() != AdminRole.SUPER_ADMIN) {
            throw new BusinessException("NOT_ACCOUNT_MANAGER", "This admin cannot be an account manager");
        }
        return admin;
    }
}
