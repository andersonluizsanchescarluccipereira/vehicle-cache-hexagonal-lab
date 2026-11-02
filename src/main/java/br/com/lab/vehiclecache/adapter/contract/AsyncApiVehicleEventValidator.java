package br.com.lab.vehiclecache.adapter.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validação de campos do Redis Stream orientada pelo payload declarado em AsyncAPI.
 * Suporta o subconjunto efetivamente usado: required, additionalProperties, type,
 * enum, format uuid/date-time. Não é um validador JSON Schema universal.
 */
@Component
public class AsyncApiVehicleEventValidator {
    private final ObjectMapper mapper;
    private final Set<String> required;
    private final Map<String, Map<String, Object>> properties;
    private final boolean rejectUnknown;

    @SuppressWarnings("unchecked")
    public AsyncApiVehicleEventValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream in = getClass().getResourceAsStream("/contracts/asyncapi.yaml")) {
            if (in == null) throw new IllegalStateException("Contrato AsyncAPI não encontrado");
            Map<String, Object> root = new Yaml().load(in);
            if (!"3.0.0".equals(root.get("asyncapi"))) throw new IllegalStateException("Versão AsyncAPI inesperada");
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> messages = (Map<String, Object>) components.get("messages");
            Map<String, Object> event = (Map<String, Object>) messages.get("VehicleChanged");
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            this.required = Set.copyOf((List<String>) payload.get("required"));
            this.properties = (Map<String, Map<String, Object>>) payload.get("properties");
            this.rejectUnknown = Boolean.FALSE.equals(payload.get("additionalProperties"));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar contrato AsyncAPI", e);
        }
    }

    public void validate(Map<String, String> event) {
        if (event == null) throw new IllegalArgumentException("Evento nulo");
        for (String name : required) {
            if (event.get(name) == null || event.get(name).isBlank())
                throw new IllegalArgumentException("Campo obrigatório ausente: " + name);
        }
        if (rejectUnknown) for (String name : event.keySet()) {
            if (!properties.containsKey(name)) throw new IllegalArgumentException("Campo não previsto: " + name);
        }
        for (var entry : event.entrySet()) {
            Map<String, Object> definition = properties.get(entry.getKey());
            if (definition == null) continue;
            if (!"string".equals(definition.get("type")))
                throw new IllegalStateException("Tipo não suportado no contrato: " + entry.getKey());
            Object allowed = definition.get("enum");
            if (allowed instanceof List<?> list && !list.contains(entry.getValue()))
                throw new IllegalArgumentException("Valor não permitido: " + entry.getKey());
            try {
                if ("uuid".equals(definition.get("format"))) UUID.fromString(entry.getValue());
                if ("date-time".equals(definition.get("format"))) OffsetDateTime.parse(entry.getValue());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Formato inválido: " + entry.getKey(), ex);
            }
        }
        // O payload é uma string JSON no XADD, conforme documentado no AsyncAPI.
        try {
            JsonNode vehicle = mapper.readTree(event.get("payload"));
            if (!vehicle.isObject()) throw new IllegalArgumentException("payload deve ser objeto JSON");
            for (String field : List.of("id", "marca", "modelo", "ano", "valor", "version")) {
                if (!vehicle.hasNonNull(field)) throw new IllegalArgumentException("payload sem campo: " + field);
            }
            if (!vehicle.path("id").isIntegralNumber() || !vehicle.path("ano").isIntegralNumber()
                    || !vehicle.path("version").isIntegralNumber()
                    || !vehicle.path("valor").isNumber()
                    || !vehicle.path("marca").isTextual() || !vehicle.path("modelo").isTextual())
                throw new IllegalArgumentException("Tipos inválidos no payload de veículo");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("payload não é JSON válido", ex);
        }
    }
}
