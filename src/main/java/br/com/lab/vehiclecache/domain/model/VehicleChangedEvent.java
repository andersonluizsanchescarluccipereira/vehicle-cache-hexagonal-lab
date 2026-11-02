package br.com.lab.vehiclecache.domain.model;

import java.time.Instant;
import java.util.UUID;

public record VehicleChangedEvent(UUID eventId, String type, Long vehicleId, Instant occurredAt, Vehicle payload) {
    public static VehicleChangedEvent of(String type, Vehicle vehicle) {
        return new VehicleChangedEvent(UUID.randomUUID(), type, vehicle.id(), Instant.now(), vehicle);
    }
}
