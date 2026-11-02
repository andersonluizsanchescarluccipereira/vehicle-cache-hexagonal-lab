package br.com.lab.vehiclecache.adapter.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AsyncApiVehicleEventValidatorTest {
    private final AsyncApiVehicleEventValidator validator = new AsyncApiVehicleEventValidator(new ObjectMapper());

    private Map<String, String> valid() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", UUID.randomUUID().toString());
        fields.put("type", "vehicle.created");
        fields.put("vehicleId", "1001");
        fields.put("occurredAt", "2026-11-02T15:00:00Z");
        fields.put("payload", "{\"id\":1001,\"marca\":\"Toyota\",\"modelo\":\"Corolla\",\"ano\":2024,\"valor\":125000,\"version\":0}");
        return fields;
    }

    @Test void acceptsValidEvent() { assertDoesNotThrow(() -> validator.validate(valid())); }
    @Test void rejectsMissingRequired() { var fields=valid(); fields.remove("eventId"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void rejectsUnknownEventType() { var fields=valid(); fields.put("type", "vehicle.unknown"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void rejectsMalformedJson() { var fields=valid(); fields.put("payload", "{broken"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void rejectsUnknownProperty() { var fields=valid(); fields.put("extra", "unexpected"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void rejectsInvalidUuid() { var fields=valid(); fields.put("eventId", "invalid"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void rejectsMissingVehicleField() { var fields=valid(); fields.put("payload", "{\"id\":1001}"); assertThrows(IllegalArgumentException.class, () -> validator.validate(fields)); }
    @Test void packagedContractMatchesDocumentation() throws Exception {
        assertEquals(Files.readString(Path.of("docs/asyncapi.yaml")), Files.readString(Path.of("src/main/resources/contracts/asyncapi.yaml")));
    }
}
