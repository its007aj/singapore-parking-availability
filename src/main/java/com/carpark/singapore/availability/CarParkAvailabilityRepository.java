package com.carpark.singapore.availability;

import com.carpark.singapore.entities.CarParkAvailabilityEntity;
import com.carpark.singapore.entities.CarParkAvailabilityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarParkAvailabilityRepository extends JpaRepository<CarParkAvailabilityEntity, CarParkAvailabilityId> {
}
