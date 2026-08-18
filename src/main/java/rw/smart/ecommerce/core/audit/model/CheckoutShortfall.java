package rw.smart.ecommerce.core.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A checkout that failed because a product was short.
 *
 * Two mapping decisions are deliberate and both follow from what an audit row is
 * for.
 *
 * The user and product are plain ids, not {@code @ManyToOne} associations. An
 * audit record states what was true at a moment in time; it must not stop a
 * product from being deleted later, and it must not disappear when one is. A
 * foreign key would enforce exactly the coupling this table exists to avoid.
 *
 * There is no association back to {@code Order} either, because in the case this
 * records no order was ever created — that is the whole point of the row.
 */
@Entity
@Table(name = "checkout_shortfalls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutShortfall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shortfall_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    /** Stock on hand at the moment the reservation was refused. */
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private java.time.LocalDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = java.time.LocalDateTime.now();
    }
}
