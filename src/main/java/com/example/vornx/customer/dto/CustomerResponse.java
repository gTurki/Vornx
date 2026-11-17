package com.example.vornx.customer.dto;

import com.example.vornx.common.enums.Language;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.enums.LoyaltyTier;

/**
 * phoneVerificationRequired: the app must show "confirm your mobile by WhatsApp" first
 * (until then the other /api/customer endpoints answer 403 PHONE_NOT_VERIFIED).
 */
public record CustomerResponse(
        String vornxId,
        String firstName,
        String lastName,
        String email,
        String phone,
        Language language,
        int points,
        LoyaltyTier tier,
        boolean emailVerified,
        boolean phoneVerified,
        boolean phoneVerificationRequired,
        boolean totpEnabled
) {
    public static CustomerResponse from(Customer c, boolean phoneVerificationRequired) {
        return new CustomerResponse(c.getVornxId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), c.getLanguage(), c.getPoints(), c.getTier(),
                c.isEmailVerified(), c.isPhoneVerified(), phoneVerificationRequired, c.isTotpEnabled());
    }
}
