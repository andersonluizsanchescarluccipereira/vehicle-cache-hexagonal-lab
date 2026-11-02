package br.com.lab.vehiclecache.application.port.out;

import br.com.lab.vehiclecache.domain.model.Vehicle;
import java.util.Optional;

public interface VehicleRepositoryPort {
    Optional<Vehicle> findById(Long id);
    Vehicle save(Vehicle vehicle);
    void deleteById(Long id);
}
