package rw.smart.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the whole context wires up: JPA mappings, the hand-built MongoDB
 * beans, the AOP proxying and the GraphQL schema.
 *
 * The test profile is pinned deliberately. Without it the suite would boot
 * against the developer database and run the seeder there, quietly writing
 * fixture rows into whatever data the developer was working with.
 */
@SpringBootTest
@ActiveProfiles("test")
class EcommerceApplicationTests {

	@Test
	void contextLoads() {
	}

}
