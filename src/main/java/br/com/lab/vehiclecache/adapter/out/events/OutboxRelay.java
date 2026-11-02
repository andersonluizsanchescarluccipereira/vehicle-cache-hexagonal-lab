package br.com.lab.vehiclecache.adapter.out.events;

import br.com.lab.vehiclecache.adapter.contract.AsyncApiVehicleEventValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final String stream;
    private final AsyncApiVehicleEventValidator contract;
    OutboxRelay(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper mapper, MeterRegistry metrics, AsyncApiVehicleEventValidator contract,
                @Value("${lab.events.stream:stream:veiculos}") String stream) {
        this.jdbc = jdbc; this.redis = redis; this.mapper = mapper; this.metrics = metrics; this.stream = stream; this.contract = contract;
    }
    @Scheduled(fixedDelayString = "${lab.events.relay.fixed-delay-ms:5000}",
               initialDelayString = "${lab.events.relay.initial-delay-ms:3000}")
    public void relay() {
        try {
            // Laboratório single-instance. Para múltiplas instâncias, usar leasing/lock com SKIP LOCKED.
            List<OutboxRow> rows = jdbc.query("""
                    SELECT id, event_type, vehicle_id, payload
                    FROM outbox_events WHERE published_at IS NULL ORDER BY created_at LIMIT 50
                    """, (rs, i) -> new OutboxRow(rs.getObject("id", UUID.class), rs.getString("event_type"),
                            rs.getLong("vehicle_id"), rs.getString("payload")));
            for (OutboxRow row : rows) {
                try {
                    JsonNode doc = mapper.readTree(row.payload());
                    Map<String, String> message = new LinkedHashMap<>();
                    message.put("eventId", row.id().toString());
                    message.put("type", row.type());
                    message.put("vehicleId", String.valueOf(row.vehicleId()));
                    message.put("occurredAt", doc.path("occurredAt").asText());
                    message.put("payload", doc.path("payload").toString());
                    contract.validate(message);
                    redis.opsForStream().add(stream, message);
                    jdbc.update("UPDATE outbox_events SET published_at=NOW() WHERE id=? AND published_at IS NULL", row.id());
                    metrics.counter("lab.outbox.published").increment();
                } catch (Exception ex) {
                    metrics.counter("lab.outbox.publish_error").increment();
                    log.warn("Evento {} permanece pendente no outbox: {}", row.id(), ex.toString());
                    break;
                }
            }
        } catch (Exception ex) { log.warn("Relay do outbox indisponível: {}", ex.toString()); }
    }
    private record OutboxRow(UUID id, String type, long vehicleId, String payload) {}
}
