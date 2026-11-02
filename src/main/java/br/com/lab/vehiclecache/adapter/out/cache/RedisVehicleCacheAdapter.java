package br.com.lab.vehiclecache.adapter.out.cache;

import br.com.lab.vehiclecache.application.port.out.VehicleCachePort;
import br.com.lab.vehiclecache.domain.model.Vehicle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;

@Component
class RedisVehicleCacheAdapter implements VehicleCachePort {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;
    RedisVehicleCacheAdapter(StringRedisTemplate redis, ObjectMapper mapper,
                             @Value("${lab.cache.ttl:PT2M}") Duration ttl) {
        this.redis = redis; this.mapper = mapper; this.ttl = ttl;
    }
    @Override public Optional<Vehicle> findById(Long id) {
        String json = redis.opsForValue().get(key(id));
        if (json == null) return Optional.empty();
        try { return Optional.of(mapper.readValue(json, Vehicle.class)); }
        catch (JsonProcessingException ex) {
            // Corrupção: descarta o valor inválido e tenta recuperar do banco.
            redis.delete(key(id));
            return Optional.empty();
        }
    }
    @Override public void put(Vehicle vehicle) {
        try { redis.opsForValue().set(key(vehicle.id()), mapper.writeValueAsString(vehicle), ttl); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Falha ao serializar veículo", ex); }
    }
    @Override public void evict(Long id) { redis.delete(key(id)); }
    private String key(Long id) { return "vehicle:" + id; }
}
