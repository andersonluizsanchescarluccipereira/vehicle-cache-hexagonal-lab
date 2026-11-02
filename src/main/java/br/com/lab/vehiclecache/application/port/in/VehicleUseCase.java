package br.com.lab.vehiclecache.application.port.in;

import br.com.lab.vehiclecache.domain.model.Vehicle;
import br.com.lab.vehiclecache.domain.model.VehicleCommand;

public interface VehicleUseCase {
    Vehicle create(VehicleCommand command);
    VehicleLookup find(Long id);
    Vehicle update(Long id, VehicleCommand command);
    void delete(Long id);

    record VehicleLookup(Vehicle vehicle, String cacheStatus) {}
}
