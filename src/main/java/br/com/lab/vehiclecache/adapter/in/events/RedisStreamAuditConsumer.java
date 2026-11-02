package br.com.lab.vehiclecache.adapter.in.events;

import br.com.lab.vehiclecache.adapter.contract.AsyncApiVehicleEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lab.events.consumer.enabled", havingValue = "true", matchIfMissing = true)
class RedisStreamAuditConsumer {
    private static final Logger log = LoggerFactory.getLogger(RedisStreamAuditConsumer.class);
    private final StringRedisTemplate redis;
    private final String stream;
    private final AsyncApiVehicleEventValidator contract;
    private final String group;
    private final String consumer;
    private boolean groupReady;
    RedisStreamAuditConsumer(StringRedisTemplate redis, AsyncApiVehicleEventValidator contract,
                             @Value("${lab.events.stream:stream:veiculos}") String stream,
                             @Value("${lab.events.group:grupo-auditoria}") String group,
                             @Value("${lab.events.consumer.name:audit-worker-1}") String consumer) {
        this.redis = redis; this.contract = contract; this.stream = stream; this.group = group; this.consumer = consumer;
    }
    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void poll() {
        try {
            ensureGroup();
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(group, consumer), StreamReadOptions.empty().count(20),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()));
            if (records == null) return;
            for (MapRecord<String, Object, Object> record : records) {
                // Validação antes do ACK. Mensagens inválidas permanecem pendentes para investigação.
                java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
                record.getValue().forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
                try {
                    contract.validate(fields);
                } catch (IllegalArgumentException invalid) {
                    log.error("Evento inválido id={} motivo={}; sem XACK", record.getId(), invalid.getMessage());
                    continue;
                }
                // Demonstra consumo e confirmação. Em produção, salvar progresso de forma idempotente.
                log.info("AUDITORIA redis-stream id={} eventId={} type={} vehicleId={}",
                        record.getId(), record.getValue().get("eventId"),
                        record.getValue().get("type"), record.getValue().get("vehicleId"));
                redis.opsForStream().acknowledge(stream, group, record.getId());
            }
        } catch (RuntimeException error) {
            groupReady = false;
            log.warn("Consumidor Redis Streams: {}", error.toString());
        }
    }
    private void ensureGroup() {
        if (groupReady) return;
        try {
            redis.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                    b("CREATE"), b(stream), b(group), b("0"), b("MKSTREAM")));
        } catch (RuntimeException error) {
            if (!isBusyGroup(error)) throw error;
        }
        groupReady = true;
    }
    private static boolean isBusyGroup(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (String.valueOf(current.getMessage()).contains("BUSYGROUP")) return true;
        }
        return false;
    }
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
