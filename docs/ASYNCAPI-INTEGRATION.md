# Integração AsyncAPI + Java + Redis Streams

## Como funciona

O contrato fonte é `docs/asyncapi.yaml`. Uma cópia em `src/main/resources/contracts/asyncapi.yaml` é empacotada no JAR e carregada por `AsyncApiVehicleEventValidator`.

- **Produtor:** `OutboxRelay` monta o map do XADD e chama `contract.validate(message)` antes de publicar. Evento inválido permanece pendente no outbox e gera métrica de erro.
- **Consumidor:** `RedisStreamAuditConsumer` valida o map antes de fazer `XACK`. Eventos inválidos não são confirmados; permanecem na PEL para investigação e recuperação manual. O consumidor atual só lê mensagens novas, portanto não reprocessa automaticamente a PEL.
- **Contrato:** os campos obrigatórios, enums, formatos e campos adicionais são lidos do YAML. O JSON serializado do veículo é validado separadamente.

## Executar

```bash
mvn test
cp .env.example .env
docker compose up --build -d
curl -i http://localhost:8080/actuator/health
```

Criar um veículo:

```bash
curl -X POST http://localhost:8080/api/v1/veiculos -H 'Content-Type: application/json' -d '{"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":125000}'
```

Inspecionar eventos:

```bash
docker compose exec redis redis-cli XRANGE stream:veiculos - +
docker compose exec redis redis-cli XINFO GROUPS stream:veiculos
docker compose exec redis redis-cli XPENDING stream:veiculos grupo-auditoria
```

## Governança

Ao editar `docs/asyncapi.yaml`, atualize também `src/main/resources/contracts/asyncapi.yaml`; o teste `packagedContractMatchesDocumentation` impede divergência silenciosa.

**Limites:** este é um validador do subconjunto de schema utilizado pelo laboratório, não um validador completo de JSON Schema/AsyncAPI. O contrato não gera classes Java. A validação é síncrona na publicação/consumo, não uma garantia de exactly-once. Para produção, implementar DLQ/quarentena, recuperação da PEL, idempotência durável e testes de integração com Redis/PostgreSQL reais.
