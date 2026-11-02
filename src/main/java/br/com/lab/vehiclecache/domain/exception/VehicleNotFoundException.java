package br.com.lab.vehiclecache.domain.exception;

public class VehicleNotFoundException extends RuntimeException {
    public VehicleNotFoundException(Long id) { super("Veículo não encontrado: " + id); }
}
