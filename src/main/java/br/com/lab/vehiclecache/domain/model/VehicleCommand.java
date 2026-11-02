package br.com.lab.vehiclecache.domain.model;

import java.math.BigDecimal;

public record VehicleCommand(String marca, String modelo, int ano, BigDecimal valor) {}
