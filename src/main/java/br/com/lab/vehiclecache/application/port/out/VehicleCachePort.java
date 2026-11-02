package br.com.lab.vehiclecache.application.port.out;

import br.com.lab.vehiclecache.domain.model.Vehicle;
import java.util.Optional;

public interface VehicleCachePort {
    Optional<Vehicle> findById(Long id);
    void put(Vehicle vehicle);
    void evict(Long id);
}
