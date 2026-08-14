package rw.smart.ecommerce.core.log.service;

import rw.smart.ecommerce.core.log.model.AccessLog;
import rw.smart.ecommerce.core.log.dto.AccessLogResponse;

import java.util.List;

public interface AccessLogService {

    /** Best-effort: a failure here must never propagate into a business call. */
    void record(AccessLog entry);

    List<AccessLogResponse> findRecent(int limit);

    List<AccessLogResponse> findSlowerThan(long thresholdMs, int limit);
}
