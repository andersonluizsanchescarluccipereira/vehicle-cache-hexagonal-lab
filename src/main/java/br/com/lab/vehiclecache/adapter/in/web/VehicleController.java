package br.com.lab.vehiclecache.adapter.in.web;

import br.com.lab.vehiclecache.application.port.in.VehicleUseCase;
import br.com.lab.vehiclecache.domain.model.Vehicle;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/veiculos")
public class VehicleController {
    private final VehicleUseCase useCase;
    public VehicleController(VehicleUseCase useCase) { this.useCase = useCase; }

    @PostMapping
    public ResponseEntity<Vehicle> create(@Valid @RequestBody VehicleRequest request) {
        Vehicle created = useCase.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/veiculos/" + created.id())).body(created);
    }
    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> find(@PathVariable @jakarta.validation.constraints.Positive Long id) {
        var result = useCase.find(id);
        return ResponseEntity.ok().header("X-Cache", result.cacheStatus()).body(result.vehicle());
    }
    @PutMapping("/{id}")
    public ResponseEntity<Vehicle> update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(useCase.update(id, request.toCommand()));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        useCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
