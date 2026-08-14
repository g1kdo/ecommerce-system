package rw.smart.ecommerce.core.review.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A product review, stored as a document rather than a row.
 *
 * The shape genuinely varies: some reviews carry photos, some a seller response,
 * some free-form tags, most neither. Relationally that is a wide table of mostly
 * NULL columns or an EAV join; as a document the shape is the record.
 *
 * {@code productId} and {@code userId} are the identifiers of the relational
 * rows — the link is by value, not by a database-enforced foreign key, which is
 * the trade accepted in exchange for the flexible shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    /** Hex form of the Mongo {@code ObjectId}; null until first insert. */
    private String id;

    private Long productId;
    private Long userId;

    /** Constrained to 1..5 by the request DTO before it reaches this layer. */
    private Integer rating;

    private String title;
    private String comment;

    private List<String> tags = new ArrayList<>();
    private List<String> photos = new ArrayList<>();

    private Integer helpfulVotes;

    private Instant createdAt;
    private Instant updatedAt;
}
