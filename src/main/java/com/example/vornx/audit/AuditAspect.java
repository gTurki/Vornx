package com.example.vornx.audit;

import com.example.vornx.audit.service.ActivityLogService;
import com.example.vornx.common.enums.ActorType;
import com.example.vornx.security.AuthUser;
import com.example.vornx.security.device.DevicePrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP = "do something around methods without touching their code".
 * After a method marked @Audited RETURNS normally, this writes one activity log line.
 * (If the method throws - validation error, 403, 409... - nothing is logged, because nothing happened.)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ActivityLogService activityLogService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void afterSuccess(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                return;
            }
            String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            Object[] args = joinPoint.getArgs();

            // SimpleEvaluationContext = a SAFE subset of SpEL: can read values and call methods on them,
            // but cannot create objects or call static methods.
            SimpleEvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
            ctx.setVariable("result", result);
            Long partnerIdParam = null;
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
                if ("partnerId".equals(names[i]) && args[i] instanceof Long l) {
                    partnerIdParam = l; // admin endpoints like /api/admin/partners/{partnerId}/...
                }
            }

            Long targetId = audited.targetId().isEmpty() ? idOf(result) : toLong(eval(audited.targetId(), ctx));
            String details = audited.details().isEmpty() ? null : String.valueOf(eval(audited.details(), ctx));
            String target = audited.target().isEmpty() ? null : audited.target();

            ActorType actorType;
            Long actorId;
            Long partnerId;
            if (auth.getPrincipal() instanceof Jwt jwt) {
                AuthUser me = AuthUser.from(jwt);
                actorType = ActorType.valueOf(me.type().name());
                actorId = me.id();
                // Order: the actor's own partner (partner staff) > explicit @Audited(partnerId) >
                // a "partnerId" method parameter > the target itself when it IS a partner.
                partnerId = me.partnerId() != null ? me.partnerId()
                        : !audited.partnerId().isEmpty() ? toLong(eval(audited.partnerId(), ctx))
                        : partnerIdParam != null ? partnerIdParam
                        : "PARTNER".equals(target) ? targetId : null;
            } else if (auth.getPrincipal() instanceof DevicePrincipal device) {
                actorType = ActorType.SYSTEM;
                actorId = device.apiKeyId();
                partnerId = device.partnerId();
            } else {
                return;
            }

            activityLogService.record(actorType, actorId, partnerId, audited.action(), target, targetId, details,
                    clientIp());
        } catch (Exception e) {
            // Never break the user's request because of logging. But make the failure visible.
            log.error("Could not write activity log for {}", audited.action(), e);
        }
    }

    private Object eval(String expression, SimpleEvaluationContext ctx) {
        return expressionCache.computeIfAbsent(expression, parser::parseExpression).getValue(ctx);
    }

    /** Most of our responses are records with an id() component. */
    private static Long idOf(Object result) {
        if (result == null) {
            return null;
        }
        try {
            Method m = result.getClass().getMethod("id");
            return toLong(m.invoke(result));
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && s.matches("\\d+")) {
            return Long.valueOf(s); // e.g. the JWT subject
        }
        return null;
    }

    /** Behind a proxy/load balancer the real client IP is in X-Forwarded-For. */
    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
