package com.carpark.singapore.search;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import com.carpark.singapore.entities.CarParkAvailabilityId;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Answers one question for the nearby-search feature: which car parks (of a given lot
 * type) currently have an available lot, joined with their location.
 *
 * <p>Not a repository in the usual sense — it manages no entity's lifecycle (that belongs
 * to {@code CarParkRepository} and {@code CarParkAvailabilityRepository}), joins two
 * otherwise-unrelated entities, and returns a read-only projection
 * ({@link CarParkAvailabilitySnapshot}) rather than a persisted type. It extends Spring
 * Data's bare {@code Repository<>} marker purely so Spring can proxy this interface;
 * {@code CarParkAvailabilityEntity} here is a technical anchor, not the thing this class
 * is "for."
 */
public interface NearbyCarParkQuery extends Repository<CarParkAvailabilityEntity, CarParkAvailabilityId> {

    String CAR_LOT_TYPE = "C";

    /** All car parks (of the car lot type) with at least one lot currently available. */
    @Query("""
            SELECT new com.carpark.singapore.search.CarParkAvailabilitySnapshot(
                c.carParkNo, c.address, c.latitude, c.longitude, a.lotsAvailable, a.totalLots, a.lotUpdatedAt)
            FROM CarParkAvailabilityEntity a
            JOIN CarParkEntity c ON c.carParkNo = a.id.carParkNo
            WHERE a.id.lotType = :lotType AND a.lotsAvailable > 0
            """)
    List<CarParkAvailabilitySnapshot> findAvailableCarParks(@Param("lotType") String lotType);

    default List<CarParkAvailabilitySnapshot> findAvailableCarParks() {
        return findAvailableCarParks(CAR_LOT_TYPE);
    }
}
