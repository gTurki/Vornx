package com.example.vornx.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a controller method and every SUCCESSFUL call is written to the activity log
 * (who, their partner, what, which record, IP, when). Failed calls (exceptions) are not logged.
 *
 * targetId / details are SpEL expressions (the same language as @PreAuthorize). You can use:
 *   #result      -> what the method returned
 *   #<paramName> -> any method parameter, e.g. #req, #partnerId
 *
 * Examples:
 *   @Audited(action = "PERMIT_REVOKED", target = "PERMIT")                    // id taken from #result.id()
 *   @Audited(action = "API_KEY_CREATED", target = "API_KEY", targetId = "#result.key().id()", details = "#req.name()")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** e.g. "PERMIT_REVOKED" */
    String action();

    /** e.g. "PERMIT" */
    String target() default "";

    /** SpEL. Empty = use #result.id() if the result has one. */
    String targetId() default "";

    /** SpEL. Empty = no details. */
    String details() default "";

    /**
     * SpEL: which partner this ADMIN action concerns, so it appears when filtering the log by partner.
     * Not needed for partner staff (their partner comes from the token) or when the method has a "partnerId" parameter.
     * e.g. partnerId = "#id" or "#result.partnerId()"
     */
    String partnerId() default "";
}
