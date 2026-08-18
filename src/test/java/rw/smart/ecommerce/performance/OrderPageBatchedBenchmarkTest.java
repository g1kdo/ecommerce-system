package rw.smart.ecommerce.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The shipped configuration: {@code default_batch_fetch_size=25}. */
@SpringBootTest
@ActiveProfiles("test")
// Repeated from the base class on purpose: JUnit does not inherit this condition
// onto subclasses, so leaving it only on AbstractOrderPageBenchmark let both of
// these run - and seed and delete rows - on every ordinary `mvn test`.
@EnabledIfSystemProperty(named = "benchmark", matches = "true",
        disabledReason = "Benchmark: run with -Dbenchmark=true")
@DisplayName("Order page, batch fetching on (as shipped)")
class OrderPageBatchedBenchmarkTest extends AbstractOrderPageBenchmark {

    @Override
    protected String label() {
        return "batch_fetch_size=25";
    }
}
