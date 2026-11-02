package br.com.lab.vehiclecache.adapter.out.events;

import br.com.lab.vehiclecache.application.port.out.EventOutboxPort;
import br.com.lab.vehiclecache.domain.model.VehicleChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxAdapter implements EventOutboxPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    JdbcOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    @Override public void append(VehicleChangedEvent event) {
        try {
            jdbc.update("INSERT INTO outbox_events(id,event_type,vehicle_id,payload) VALUES (?, ?, ?, ?)",
                    event.eventId(), event.type(), event.vehicleId(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Falha ao serializar evento", ex); }
    }
}
