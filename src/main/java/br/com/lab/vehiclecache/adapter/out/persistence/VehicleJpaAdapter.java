package br.com.lab.vehiclecache.adapter.out.persistence;

import br.com.lab.vehiclecache.application.port.out.VehicleRepositoryPort;
import br.com.lab.vehiclecache.domain.exception.VehicleNotFoundException;
import br.com.lab.vehiclecache.domain.model.Vehicle;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
class VehicleJpaAdapter implements VehicleRepositoryPort {
    private final SpringDataVehicleRepository repo;
    VehicleJpaAdapter(SpringDataVehicleRepository repo) { this.repo = repo; }
    @Override public Optional<Vehicle> findById(Long id) { return repo.findById(id).map(VehicleJpaAdapter::toDomain); }
    @Override public Vehicle save(Vehicle vehicle) {
        VehicleJpaEntity entity;
        if (vehicle.id() == null) {
            entity = new VehicleJpaEntity(vehicle.marca(), vehicle.modelo(), vehicle.ano(), vehicle.valor());
        } else {
            entity = repo.findById(vehicle.id()).orElseThrow(() -> new VehicleNotFoundException(vehicle.id()));
            if (entity.version() != vehicle.version()) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(VehicleJpaEntity.class, vehicle.id());
            }
            entity.update(vehicle.marca(), vehicle.modelo(), vehicle.ano(), vehicle.valor());
        }
        return toDomain(repo.saveAndFlush(entity));
    }
    @Override public void deleteById(Long id) { repo.deleteById(id); repo.flush(); }
    private static Vehicle toDomain(VehicleJpaEntity e) {
        return new Vehicle(e.id(), e.marca(), e.modelo(), e.ano(), e.valor(), e.version());
    }
}
