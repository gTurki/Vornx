package com.example.vornx.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * vornx.demo.* (DEMO_SEED, DEMO_EMAIL, DEMO_PASSWORD, DEMO_EXTRA_EMAILS). See DemoDataSeeder.
 *
 * @param seed        true = create the demo data at startup (once)
 * @param email       YOUR inbox: demo accounts use aliases of it (you+khalid@...), so their codes reach you
 * @param password    the password of every demo account (10+ characters). A secret: set it only in Railway.
 * @param extraEmails other EXISTING customer accounts that also get the demo data (comma separated), e.g. the
 *                    people you present VORNX to. Kept in Railway, not in the code.
 */
@ConfigurationProperties(prefix = "vornx.demo")
public record DemoProperties(boolean seed, String email, String password, List<String> extraEmails) {

    public List<String> extraEmails() {
        return extraEmails == null ? List.of() : extraEmails;
    }
}
