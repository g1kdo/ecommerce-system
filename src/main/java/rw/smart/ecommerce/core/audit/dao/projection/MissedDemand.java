package rw.smart.ecommerce.core.audit.dao.projection;

/** Demand the catalogue could not serve, aggregated per product. */
public interface MissedDemand {

    Long getProductId();

    long getOccurrences();

    long getUnitsMissed();
}
