package rw.smart.ecommerce.utils.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.smart.ecommerce.core.log.model.AccessLog;
import rw.smart.ecommerce.core.log.service.AccessLogService;

import java.time.Instant;

/**
 * Execution-time monitoring for the whole service layer.
 *
 * One advice replaces a timing block that would otherwise be copy-pasted into
 * every service method — the classic case for AOP: the concern is real, but it
 * is orthogonal to what any individual method is for.
 *
 * AspectJ proxying needs no extra dependency here: {@code aspectjweaver} arrives
 * transitively through {@code spring-boot-starter-data-jpa}, and Boot's
 * {@code AopAutoConfiguration} enables it automatically.
 */
@Slf4j
@Aspect
@Component
public class ExecutionTimeAspect {

    private final AccessLogService accessLogService;
    private final long slowThresholdMs;

    public ExecutionTimeAspect(AccessLogService accessLogService,
                               @Value("${app.monitoring.slow-threshold-ms:500}") long slowThresholdMs) {
        this.accessLogService = accessLogService;
        this.slowThresholdMs = slowThresholdMs;
    }

    /**
     * Every service implementation except the log service itself — advising the
     * component that writes the logs would recurse on every call.
     */
    @Pointcut("within(rw.smart.ecommerce.core..service.impl..*) "
            + "&& !within(rw.smart.ecommerce.core.log.service.impl.AccessLogServiceImpl)")
    public void serviceLayer() {
        // pointcut declaration only
    }

    @Around("serviceLayer()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long startedAt = System.nanoTime();
        String outcome = "SUCCESS";
        String errorMessage = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            // Failures are the invocations most worth timing, so the record is
            // written for them too — then the original exception continues
            // unchanged to the global handler.
            outcome = "FAILURE";
            errorMessage = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            throw throwable;
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

            if (elapsedMs >= slowThresholdMs || !"SUCCESS".equals(outcome)) {
                log.warn("[{}] {}.{} took {} ms", outcome, className, methodName, elapsedMs);
            } else {
                log.debug("[{}] {}.{} took {} ms", outcome, className, methodName, elapsedMs);
            }

            accessLogService.record(buildEntry(className, methodName, elapsedMs, outcome, errorMessage));
        }
    }

    private AccessLog buildEntry(String className, String methodName, long elapsedMs,
                                 String outcome, String errorMessage) {

        AccessLog entry = new AccessLog();
        entry.setTimestamp(Instant.now());
        entry.setClassName(className);
        entry.setMethodName(methodName);
        entry.setExecutionTimeMs(elapsedMs);
        entry.setOutcome(outcome);
        entry.setErrorMessage(errorMessage);

        // Null outside a web request (scheduled work, tests) — the aspect must
        // not assume it is always running on a request thread.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            entry.setRequestPath(attributes.getRequest().getRequestURI());
            entry.setHttpMethod(attributes.getRequest().getMethod());
        }

        return entry;
    }
}
