package rw.smart.ecommerce.core.log.dto;

import rw.smart.ecommerce.core.log.model.AccessLog;

public record AccessLogResponse(
        String id,
        String timestamp,
        String className,
        String methodName,
        String requestPath,
        String httpMethod,
        Long executionTimeMs,
        String outcome,
        String errorMessage) {

    public static AccessLogResponse from(AccessLog log) {
        return new AccessLogResponse(
                log.getId(),
                log.getTimestamp() == null ? null : log.getTimestamp().toString(),
                log.getClassName(),
                log.getMethodName(),
                log.getRequestPath(),
                log.getHttpMethod(),
                log.getExecutionTimeMs(),
                log.getOutcome(),
                log.getErrorMessage());
    }
}
