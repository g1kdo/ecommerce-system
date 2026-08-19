package rw.smart.ecommerce.core.report;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 3 GraphQL surface, executed for real against the schema.
 *
 * Two things are worth asserting here that no REST test can assert, and they are
 * the reasons the reporting endpoints were given a GraphQL face at all:
 *
 * <ol>
 *   <li>Four dashboard panels arrive in <em>one</em> request. Over REST that is
 *       four calls, each paying authentication again — and section 6.5 of the
 *       performance report measured that at ~95 ms apiece.</li>
 *   <li>A panel that is not selected is not computed. {@code salesReport} is a
 *       type whose every field is a resolver, so asking only for the revenue
 *       headline runs one statement rather than the whole dashboard's worth.
 *       That is measured below with Hibernate's statement counter, not asserted
 *       by eye.</li>
 * </ol>
 *
 * <h4>Requires PostgreSQL</h4>
 *
 * <pre>createdb -U postgres smart_ecommerce_test_db</pre>
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
@DisplayName("Phase 3 GraphQL surface")
class ReportGraphQLTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void signInAsAdmin() {
        // spring-security-test is not on the classpath and adding it would step
        // outside the agreed dependency set, so the context is populated directly.
        // @PreAuthorize reads it from exactly here.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-under-test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("one query returns four dashboard panels")
    void oneQueryReturnsTheWholeDashboard() {
        graphQlTester.document("""
                        query {
                          salesReport {
                            totalRevenue
                            daily { day orderCount revenue }
                            byStatus { status orderCount revenue }
                            topProducts(limit: 5) { productName unitsSold }
                          }
                        }
                        """)
                .execute()
                .path("salesReport.totalRevenue").hasValue()
                .path("salesReport.daily").hasValue()
                .path("salesReport.byStatus").hasValue()
                .path("salesReport.topProducts").hasValue();
    }

    @Test
    @DisplayName("a field that is not selected is not queried")
    void unselectedPanelsCostNothing() {
        long headlineOnly = statementsFor("""
                query { salesReport { totalRevenue } }
                """);

        long threePanels = statementsFor("""
                query {
                  salesReport {
                    totalRevenue
                    daily { day }
                    byStatus { status }
                  }
                }
                """);

        // The exact counts depend on how the caches are warmed, so the assertion
        // is the relationship, not the numbers: asking for more must cost more,
        // which is only true if the resolvers really are per-field.
        assertTrue(threePanels > headlineOnly,
                "selecting three panels should issue more statements than selecting one; got "
                        + threePanels + " vs " + headlineOnly);
    }

    @Test
    @DisplayName("the catalogue report resolves its fields, including a paginated one")
    void catalogueReportResolves() {
        graphQlTester.document("""
                        query {
                          catalogueReport {
                            categories { name productCount }
                            lowStock(threshold: 5, page: 0, size: 5) { totalElements content { sku quantity } }
                            missedDemand(limit: 5) { productId unitsMissed }
                          }
                        }
                        """)
                .execute()
                .path("catalogueReport.categories").hasValue()
                .path("catalogueReport.lowStock.totalElements").hasValue()
                .path("catalogueReport.missedDemand").hasValue();
    }

    @Test
    @DisplayName("orderHistory returns a page, not an unbounded list")
    void orderHistoryIsPaginated() {
        graphQlTester.document("""
                        query {
                          orders(page: 0, size: 5) {
                            page
                            size
                            totalElements
                            content { id status totalAmount }
                          }
                        }
                        """)
                .execute()
                .path("orders.size").entity(Integer.class).isEqualTo(5)
                .path("orders.page").entity(Integer.class).isEqualTo(0);
    }

    @Test
    @DisplayName("searchUsers reaches the Query by Example probe through GraphQL")
    void searchUsersIsWired() {
        graphQlTester.document("""
                        query {
                          searchUsers(keyword: "no-such-user-at-all", page: 0, size: 5) {
                            totalElements
                            content { username email }
                          }
                        }
                        """)
                .execute()
                .path("searchUsers.totalElements").entity(Integer.class).isEqualTo(0);
    }

    @Test
    @DisplayName("a malformed date is a bad request naming the argument, not an internal error")
    void malformedDateIsRejectedReadably() {
        graphQlTester.document("""
                        query { salesReport(from: "last-tuesday") { totalRevenue } }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertEquals(1, errors.size());
                    assertTrue(errors.getFirst().getMessage().contains("from"),
                            "the message should name the offending argument: "
                                    + errors.getFirst().getMessage());
                });
    }

    @Test
    @DisplayName("reporting is UNAUTHORIZED with no principal at all")
    void reportingRequiresAuthentication() {
        SecurityContextHolder.clearContext();

        graphQlTester.document("""
                        query { salesReport { totalRevenue } }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> assertTrue(errors.stream().anyMatch(
                                error -> "UNAUTHORIZED".equals(String.valueOf(error.getErrorType()))),
                        // This is the case that used to answer INTERNAL_ERROR. Method
                        // security throws AuthenticationCredentialsNotFoundException
                        // when there is no principal, and GraphQlExceptionResolver only
                        // classified AccessDeniedException - so "sign in" reached the
                        // client as "we crashed".
                        "no principal must be UNAUTHORIZED, not an internal error; got " + errors));
    }

    @Test
    @DisplayName("reporting is FORBIDDEN for a signed-in caller without the role")
    void reportingRequiresAdminRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer-under-test", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

        graphQlTester.document("""
                        query { salesReport { totalRevenue } }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> assertTrue(errors.stream().anyMatch(
                                error -> "FORBIDDEN".equals(String.valueOf(error.getErrorType()))),
                        // GraphQL is one POST that must stay open for the public
                        // catalogue, so no URL rule can protect this. It is refused here
                        // or nowhere - the lesson of section 6.6.
                        "a CUSTOMER must be FORBIDDEN; got " + errors));
    }

    private long statementsFor(String document) {
        // Without this the second call measures a cache hit rather than a query,
        // and both sides come back as zero statements - which is how this
        // assertion first passed for the wrong reason.
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();

        graphQlTester.document(document).execute();

        return statistics.getPrepareStatementCount() - before;
    }
}
