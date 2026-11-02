package br.com.lab.vehiclecache.adapter.in.web;

import br.com.lab.vehiclecache.domain.model.VehicleCommand;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank @Size(max = 100) String marca,
        @NotBlank @Size(max = 100) String modelo,
        @Min(1900) @Max(2100) int ano,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor
) {
    public VehicleCommand toCommand() { return new VehicleCommand(marca, modelo, ano, valor); }
}
