package com.example.vornx.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Turns on automatic filling of @CreatedDate / @LastModifiedDate in BaseEntity. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
