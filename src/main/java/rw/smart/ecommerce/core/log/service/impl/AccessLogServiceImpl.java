package rw.smart.ecommerce.core.log.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import rw.smart.ecommerce.config.AsyncConfig;
import rw.smart.ecommerce.core.log.model.AccessLog;
import rw.smart.ecommerce.core.log.dto.AccessLogResponse;
import rw.smart.ecommerce.core.log.dao.AccessLogRepository;
import rw.smart.ecommerce.core.log.service.AccessLogService;

import java.util.List;

/**
 * Deliberately excluded from the monitoring aspect's pointcut — see
 * {@code ExecutionTimeAspect}. An aspect that logged its own logging would
 * recurse on every service call.
 */
@Slf4j
@Service
public class AccessLogServiceImpl implements AccessLogService {

    private static final int MAX_LIMIT = 500;

    private final AccessLogRepository accessLogRepository;

    public AccessLogServiceImpl(AccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    /**
     * Runs off the request thread. The aspect fires on every service call, and
     * a synchronous insert here charged each one a MongoDB round trip before the
     * response could be written — pure latency for something nobody is waiting on.
     *
     * Still swallows every failure by design. Monitoring is an observation of the
     * system, not part of it: an unreachable document store should cost the
     * operator a log line, not the customer their order.
     */
    @Override
    @Async(AsyncConfig.ACCESS_LOG_EXECUTOR)
    public void record(AccessLog entry) {
        try {
            accessLogRepository.insert(entry);
        } catch (RuntimeException e) {
            log.warn("Access log could not be persisted for {}.{}: {}",
                    entry.getClassName(), entry.getMethodName(), e.getMessage());
        }
    }

    @Override
    public List<AccessLogResponse> findRecent(int limit) {
        return accessLogRepository.findRecent(clamp(limit)).stream()
                .map(AccessLogResponse::from)
                .toList();
    }

    @Override
    public List<AccessLogResponse> findSlowerThan(long thresholdMs, int limit) {
        return accessLogRepository.findSlowerThan(thresholdMs, clamp(limit)).stream()
                .map(AccessLogResponse::from)
                .toList();
    }

    private int clamp(int limit) {
        if (limit < 1) return 50;
        return Math.min(limit, MAX_LIMIT);
    }
}
