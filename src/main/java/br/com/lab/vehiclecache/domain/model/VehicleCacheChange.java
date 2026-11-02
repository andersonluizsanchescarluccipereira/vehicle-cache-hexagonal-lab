package br.com.lab.vehiclecache.domain.model;

public record VehicleCacheChange(Long id, Vehicle vehicle) {
    // vehicle == null representa uma invalidação (DELETE).
}
