package com.example.vornx.partner.dto;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.partner.enums.PartnerRole;

/** Change a staff member's role and/or status. Send only what you want to change. */
public record PartnerUserUpdateRequest(
        PartnerRole role,
        AccountStatus status
) {
}
