package com.carpark.singapore.carpark;

import com.carpark.singapore.entities.CarParkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarParkRepository extends JpaRepository<CarParkEntity, String> {
}
