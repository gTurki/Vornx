package com.example.vornx.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * UltraMsg when ULTRAMSG_INSTANCE_ID + ULTRAMSG_TOKEN are set; otherwise a sender that only writes to the log
 * (development). PhoneVerificationPolicy refuses to start with CUSTOMER_PHONE_VERIFICATION=whatsapp and no
 * real sender outside development.
 */
@Slf4j
@Configuration
public class WhatsAppConfig {

    @Bean
    public WhatsAppSender whatsAppSender(@Value("${vornx.whatsapp.ultramsg.base-url:https://api.ultramsg.com}") String baseUrl,
                                         @Value("${vornx.whatsapp.ultramsg.instance-id:}") String instanceId,
                                         @Value("${vornx.whatsapp.ultramsg.token:}") String token) {
        if (StringUtils.hasText(instanceId) && StringUtils.hasText(token)) {
            return new UltraMsgWhatsAppSender(baseUrl, instanceId, token);
        }
        return new WhatsAppSender() {
            @Override
            public void send(String toE164, String text) {
                log.info("[DEV] WhatsApp NOT sent (no UltraMsg settings) to {}•••{}", toE164.substring(0, 5),
                        toE164.substring(toE164.length() - 3));
            }

            @Override
            public boolean isReal() {
                return false;
            }
        };
    }
}
