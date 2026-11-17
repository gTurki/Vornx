package com.example.vornx.customer.service;

import com.example.vornx.customer.entity.Customer;
import com.example.vornx.whatsapp.WhatsAppSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * vornx.customer.phone-verification (CUSTOMER_PHONE_VERIFICATION):
 *   "whatsapp" = after sign-up the customer must confirm their mobile with a code sent by WhatsApp before using
 *                the app (the API answers 403 PHONE_NOT_VERIFIED until then)
 *   "off"      = no mobile check (default, until UltraMsg is set up)
 */
@Component
public class PhoneVerificationPolicy {

    private final boolean enabled;

    public PhoneVerificationPolicy(@Value("${vornx.customer.phone-verification:off}") String mode,
                                   @Value("${vornx.dev.expose-tokens:false}") boolean dev,
                                   WhatsAppSender whatsApp) {
        this.enabled = switch (mode.trim().toLowerCase()) {
            case "whatsapp" -> true;
            case "off" -> false;
            default -> throw new IllegalStateException("vornx.customer.phone-verification must be 'whatsapp' or 'off'");
        };
        if (enabled && !whatsApp.isReal() && !dev) {
            // fail at startup, not when the first customer asks for a code
            throw new IllegalStateException("CUSTOMER_PHONE_VERIFICATION=whatsapp needs ULTRAMSG_INSTANCE_ID and ULTRAMSG_TOKEN");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True while this customer still has to confirm their mobile. */
    public boolean required(Customer c) {
        return enabled && !c.isPhoneVerified();
    }
}
