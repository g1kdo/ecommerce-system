package rw.smart.ecommerce.core.log.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One service-method invocation recorded by the monitoring aspect.
 *
 * Append-only, never joined, and read by time range — the access pattern a
 * document store is built for. Keeping this out of PostgreSQL also keeps the
 * index write amplification of a fast-growing table away from the tables that
 * serve orders and stock.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessLog {

    private String id;

    private Instant timestamp;

    private String className;
    private String methodName;

    /** Null for invocations outside a web request (scheduled work, tests). */
    private String requestPath;
    private String httpMethod;

    private Long executionTimeMs;

    /** {@code SUCCESS} or {@code FAILURE} — kept as a String, see class notes. */
    private String outcome;

    private String errorMessage;
}
