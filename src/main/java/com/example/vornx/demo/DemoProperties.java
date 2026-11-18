package com.example.vornx.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * vornx.demo.* (DEMO_SEED, DEMO_EMAIL, DEMO_PASSWORD). See DemoDataSeeder.
 *
 * @param seed     true = create the demo data at startup (once)
 * @param email    YOUR inbox: demo accounts use aliases of it (you+khalid@...), so their codes reach you
 * @param password the password of every demo account (10+ characters). A secret: set it only in Railway.
 */
@ConfigurationProperties(prefix = "vornx.demo")
public record DemoProperties(boolean seed, String email, String password) {
}
