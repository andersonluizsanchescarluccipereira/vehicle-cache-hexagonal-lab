package br.com.lab.vehiclecache.application.service;

import br.com.lab.vehiclecache.application.port.out.*;
import br.com.lab.vehiclecache.domain.exception.VehicleNotFoundException;
import br.com.lab.vehiclecache.domain.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VehicleServiceTest {
    private VehicleRepositoryPort repository;
    private VehicleCachePort cache;
    private EventOutboxPort outbox;
    private AfterCommitEventPort events;
    private VehicleService service;
    private final Vehicle vehicle = new Vehicle(1001L, "Toyota", "Corolla", 2024, new BigDecimal("125000.00"), 0);

    @BeforeEach void setup() {
        repository = mock(VehicleRepositoryPort.class);
        cache = mock(VehicleCachePort.class);
        outbox = mock(EventOutboxPort.class);
        events = mock(AfterCommitEventPort.class);
        service = new VehicleService(repository, cache, outbox, events, new SimpleMeterRegistry());
    }
    @Test void hitNaoConsultaBanco() {
        when(cache.findById(1001L)).thenReturn(Optional.of(vehicle));
        var result = service.find(1001L);
        assertEquals("HIT", result.cacheStatus());
        verifyNoInteractions(repository);
    }
    @Test void missConsultaBancoEPopulaCache() {
        when(cache.findById(1001L)).thenReturn(Optional.empty());
        when(repository.findById(1001L)).thenReturn(Optional.of(vehicle));
        var result = service.find(1001L);
        assertEquals("MISS", result.cacheStatus());
        verify(cache).put(vehicle);
    }
    @Test void redisIndisponivelContinuaNoPostgres() {
        when(cache.findById(1001L)).thenThrow(new RuntimeException("Redis indisponível"));
        when(repository.findById(1001L)).thenReturn(Optional.of(vehicle));
        var result = service.find(1001L);
        assertEquals("BYPASS", result.cacheStatus());
        verify(cache, never()).put(any());
    }
    @Test void naoEncontradoRetornaErroDeDominio() {
        when(cache.findById(1001L)).thenReturn(Optional.empty());
        when(repository.findById(1001L)).thenReturn(Optional.empty());
        assertThrows(VehicleNotFoundException.class, () -> service.find(1001L));
    }
    @Test void criacaoEnfileiraOutboxEAtualizacaoPosCommit() {
        when(repository.save(any())).thenReturn(vehicle);
        var command = new VehicleCommand("Toyota", "Corolla", 2024, new BigDecimal("125000.00"));
        assertEquals(vehicle, service.create(command));
        verify(outbox).append(argThat(e -> e.type().equals("vehicle.created") && e.vehicleId().equals(1001L)));
        verify(events).publish(new VehicleCacheChange(1001L, vehicle));
        verifyNoInteractions(cache);
    }
    @Test void exclusaoInvalidaDepoisDoCommit() {
        when(repository.findById(1001L)).thenReturn(Optional.of(vehicle));
        service.delete(1001L);
        verify(repository).deleteById(1001L);
        verify(events).publish(new VehicleCacheChange(1001L, null));
        verify(outbox).append(argThat(e -> e.type().equals("vehicle.deleted")));
    }
}
