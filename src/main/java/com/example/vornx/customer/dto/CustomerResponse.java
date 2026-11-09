package com.example.vornx.customer.dto;

import com.example.vornx.common.enums.Language;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.enums.LoyaltyTier;

public record CustomerResponse(
        String vornxId,
        String firstName,
        String lastName,
        String email,
        String phone,
        Language language,
        int points,
        LoyaltyTier tier
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getVornxId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), c.getLanguage(), c.getPoints(), c.getTier());
    }
}
