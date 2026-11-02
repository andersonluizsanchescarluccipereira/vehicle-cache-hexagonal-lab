package br.com.lab.vehiclecache.application.service;

import br.com.lab.vehiclecache.application.port.in.VehicleUseCase;
import br.com.lab.vehiclecache.application.port.out.*;
import br.com.lab.vehiclecache.domain.exception.VehicleNotFoundException;
import br.com.lab.vehiclecache.domain.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class VehicleService implements VehicleUseCase {
    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);
    private final VehicleRepositoryPort repository;
    private final VehicleCachePort cache;
    private final EventOutboxPort outbox;
    private final AfterCommitEventPort cacheEvents;
    private final MeterRegistry metrics;

    public VehicleService(VehicleRepositoryPort repository, VehicleCachePort cache,
                          EventOutboxPort outbox, AfterCommitEventPort cacheEvents, MeterRegistry metrics) {
        this.repository = repository;
        this.cache = cache;
        this.outbox = outbox;
        this.cacheEvents = cacheEvents;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Vehicle create(VehicleCommand command) {
        Vehicle vehicle = repository.save(new Vehicle(null, command.marca(), command.modelo(),
                command.ano(), command.valor(), 0));
        outbox.append(VehicleChangedEvent.of("vehicle.created", vehicle));
        cacheEvents.publish(new VehicleCacheChange(vehicle.id(), vehicle));
        return vehicle;
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleLookup find(Long id) {
        String cacheStatus = "MISS";
        try {
            Optional<Vehicle> cached = cache.findById(id);
            if (cached.isPresent()) {
                metrics.counter("lab.cache.lookup", "result", "hit").increment();
                return new VehicleLookup(cached.get(), "HIT");
            }
            metrics.counter("lab.cache.lookup", "result", "miss").increment();
        } catch (RuntimeException error) {
            log.warn("Redis indisponível na leitura; consultando PostgreSQL: {}", error.toString());
            metrics.counter("lab.cache.lookup", "result", "bypass").increment();
            cacheStatus = "BYPASS";
        }
        Vehicle vehicle = repository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
        if (!"BYPASS".equals(cacheStatus)) {
            try { cache.put(vehicle); }
            catch (RuntimeException error) { log.warn("Falha ao popular cache: {}", error.toString()); }
        }
        return new VehicleLookup(vehicle, cacheStatus);
    }

    @Override
    @Transactional
    public Vehicle update(Long id, VehicleCommand command) {
        Vehicle previous = repository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
        Vehicle updated = repository.save(new Vehicle(id, command.marca(), command.modelo(),
                command.ano(), command.valor(), previous.version()));
        outbox.append(VehicleChangedEvent.of("vehicle.updated", updated));
        cacheEvents.publish(new VehicleCacheChange(id, updated));
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Vehicle previous = repository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
        repository.deleteById(id);
        outbox.append(VehicleChangedEvent.of("vehicle.deleted", previous));
        cacheEvents.publish(new VehicleCacheChange(id, null));
    }
}
