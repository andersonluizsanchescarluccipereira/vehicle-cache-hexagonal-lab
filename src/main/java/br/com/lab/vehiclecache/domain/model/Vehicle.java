package br.com.lab.vehiclecache.domain.model;

import java.math.BigDecimal;

public record Vehicle(Long id, String marca, String modelo, int ano, BigDecimal valor, long version) {}
