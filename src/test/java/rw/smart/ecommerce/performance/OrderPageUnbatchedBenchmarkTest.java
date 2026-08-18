package rw.smart.ecommerce.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The same page without batch fetching — the Phase 2 behaviour, and the "before"
 * column of the report.
 *
 * A value of 1 rather than -1: Hibernate reads -1 as "use the factory default",
 * so -1 would quietly measure the optimization against itself.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=1")
@ActiveProfiles("test")
// Repeated from the base class on purpose: JUnit does not inherit this condition
// onto subclasses, so leaving it only on AbstractOrderPageBenchmark let both of
// these run - and seed and delete rows - on every ordinary `mvn test`.
@EnabledIfSystemProperty(named = "benchmark", matches = "true",
        disabledReason = "Benchmark: run with -Dbenchmark=true")
@DisplayName("Order page, batch fetching off (Phase 2 behaviour)")
class OrderPageUnbatchedBenchmarkTest extends AbstractOrderPageBenchmark {

    @Override
    protected String label() {
        return "batch_fetch_size=1";
    }
}
