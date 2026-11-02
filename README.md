# Vehicle Cache Lab — Arquitetura Hexagonal, Redis e PostgreSQL

![Java](https://img.shields.io/badge/Java-21-blue) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-green) ![AsyncAPI](https://img.shields.io/badge/AsyncAPI-3.0-purple)

> **Laboratório educacional reproduzível** de System Design: Cache-Aside, escrita dupla (PostgreSQL + cache após commit), eventos Redis Streams, Transactional Outbox, C4 Model, métricas e experimentos de resiliência. O domínio é o catálogo de veículos.

## 1. O que este projeto ensina

| Conceito | Implementação verificável |
|---|---|
| Cache-Aside / Lazy Loading | `GET` faz `Redis GET`, busca PostgreSQL no miss, popula com TTL. |
| Write-Through aplicado na aplicação | `POST`/`PUT`: grava PostgreSQL, depois do **commit** atualiza o Redis. |
| Invalidação | `DELETE` no PostgreSQL + `DEL` Redis após commit. |
| Fail-open | Se Redis falhar durante `GET`, API faz bypass para PostgreSQL. |
| Outbox transacional | `vehicles` e `outbox_events` são alterados na **mesma transação**. |
| Eventos confiáveis | Relay envia `XADD` para `stream:veiculos`; marca `published_at` somente após envio. |
| Consumidor | `XREADGROUP` + `XACK`, grupo `grupo-auditoria`, com log auditável. |
| Hexagonal | Domínio e portas independem de Redis, PostgreSQL e HTTP. |
| C4 Model | `docs/c4/workspace.dsl`: C1 Contexto, C2 Contêineres, C3 Componentes. |
| AsyncAPI e OpenAPI | `docs/asyncapi.yaml` e `docs/openapi.yaml` (servidos também em `/docs/asyncapi.yaml` e `/docs/openapi.yaml`). |

### Fonte de verdade e limites

O PostgreSQL é a **fonte de verdade**. A escrita no Redis **não é transacional com o banco** e pode ficar defasada em caso de falha. A comunicação assíncrona entrega **pelo menos uma vez**. Este projeto é um laboratório, não uma solução pronta para dados financeiros críticos.

## 2. Requisitos

- Docker Engine / Docker Desktop com Compose V2 (para subir tudo, **não precisa Maven instalado**).
- Portas locais livres: `8080` API, `5433` PostgreSQL e `6380` Redis; opcionais via `.env`.
- `curl` e, para scripts de teste, `python3` (ou `jq`).
- Se rodar a API fora do Docker: **Java 21 + Maven 3.9+**.

## 3. Executar — totalmente dockerizado

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs -f api
```

Quando aparecer `Started VehicleCacheApplication`, abra outro terminal:

```bash
curl -i http://localhost:8080/actuator/health
# HTTP 200 -> {"status":"UP"}
```

Para atualizar a imagem após modificar código:

```bash
docker compose up --build -d api
```

Para encerrar (mantém volumes): `docker compose down`. Para apagar **todos os dados do laboratório**: `docker compose down -v`.

### Executar Java fora do Docker (opcional)

```bash
docker compose up -d postgres redis
mvn test
mvn spring-boot:run
```

As configurações locais estão em `src/main/resources/application.yml`: PostgreSQL na porta `5433` e Redis em `6380`. Dentro do container API, os endereços passam a ser `postgres:5432` e `redis:6379` por variáveis de ambiente.

## 4. API HTTP e exemplos curl

**URL base:** `http://localhost:8080/api/v1/veiculos`

### 4.1 Criar veículo — `POST` / 201

```bash
curl -i -X POST http://localhost:8080/api/v1/veiculos \
  -H 'Content-Type: application/json' \
  -d '{"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":125000.00}'
```

Exemplo de resposta (ID depende do estado do banco):

```http
HTTP/1.1 201 Created
Location: /api/v1/veiculos/1
Content-Type: application/json

{"id":1,"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":125000.00,"version":0}
```

> A resposta HTTP não aguarda o relay do outbox. O evento será publicado de maneira assíncrona.

### 4.2 Forçar MISS e depois HIT — `GET` / 200

Ao criar um veículo a estratégia de escrita já deixa a entrada aquecida. Para observar o Cache-Aside, **remova manualmente a entrada do Redis** (troque `1` pelo ID recebido):

```bash
docker compose exec redis redis-cli DEL vehicle:1
curl -i http://localhost:8080/api/v1/veiculos/1
# X-Cache: MISS
curl -i http://localhost:8080/api/v1/veiculos/1
# X-Cache: HIT
```

Verifique a chave e a expiração:

```bash
docker compose exec redis redis-cli GET vehicle:1
docker compose exec redis redis-cli TTL vehicle:1
```

### 4.3 Atualizar veículo — `PUT` / 200

```bash
curl -i -X PUT http://localhost:8080/api/v1/veiculos/1 \
  -H 'Content-Type: application/json' \
  -d '{"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":130000.00}'
curl -i http://localhost:8080/api/v1/veiculos/1
# X-Cache: HIT (após sincronização de cache pós-commit)
```

### 4.4 Excluir veículo — `DELETE` / 204

```bash
curl -i -X DELETE http://localhost:8080/api/v1/veiculos/1
curl -i http://localhost:8080/api/v1/veiculos/1
# 404 Not Found (Problem Details JSON)
```

### 4.5 Validação — `POST` / 400

```bash
curl -i -X POST http://localhost:8080/api/v1/veiculos \
  -H 'Content-Type: application/json' \
  -d '{"marca":"","modelo":"Corolla","ano":1800,"valor":-2}'
# 400 Bad Request
```

**Automação de smoke test:**

```bash
bash scripts/smoke.sh
```

O script cria um registro, força `MISS`, comprova `HIT`, faz `PUT`, verifica o valor atualizado e remove o registro; não depende de um ID fixo.

## 5. Explorar Redis e PostgreSQL

```bash
# Cache: chaves apenas do laboratório
docker compose exec redis redis-cli --scan --pattern 'vehicle:*'

# Eventos no Stream (em até ~5 segundos após a transação)
docker compose exec redis redis-cli XRANGE stream:veiculos - + COUNT 20

# Grupos e pendências
docker compose exec redis redis-cli XINFO GROUPS stream:veiculos
docker compose exec redis redis-cli XPENDING stream:veiculos grupo-auditoria

# Fonte de verdade
docker compose exec postgres psql -U vehicle -d vehicledb \
  -c 'SELECT * FROM vehicles ORDER BY id DESC LIMIT 10;'

# Eventos no outbox
docker compose exec postgres psql -U vehicle -d vehicledb \
  -c 'SELECT id,event_type,vehicle_id,created_at,published_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;'
```

Para observar o consumidor:

```bash
docker compose logs -f api | grep AUDITORIA
```

**Simulação de publicação manual compatível com a AsyncAPI:**

```bash
docker compose exec redis redis-cli XADD stream:veiculos '*' \
  eventId 'c95c845b-4bdb-4651-9742-518466dfbe5e' \
  type vehicle.updated \
  vehicleId 1001 \
  occurredAt 2026-11-02T15:00:00Z \
  payload '{"id":1001,"marca":"Toyota","modelo":"Corolla","ano":2024,"valor":130000.00,"version":1}'
```

O `XADD` acima é **apenas ilustrativo**: não grava no PostgreSQL e não deve ser usado para simular uma alteração real de domínio. O conteúdo é descrito em `docs/asyncapi.yaml`.

**Exercício manual de consumer group** (grupo independente do consumidor Java):

```bash
# Cria o grupo; caso já exista, o Redis retornará BUSYGROUP.
docker compose exec redis redis-cli XGROUP CREATE stream:veiculos grupo-manual 0 MKSTREAM

# Consome mensagens que ainda não foram entregues ao grupo manual.
docker compose exec redis redis-cli XREADGROUP GROUP grupo-manual worker-manual COUNT 10 STREAMS stream:veiculos '>'

# Confirma processamento. Substitua ID_DO_STREAM pelo ID exibido (ex.: 1762110000000-0).
docker compose exec redis redis-cli XACK stream:veiculos grupo-manual ID_DO_STREAM

# Inspeciona eventos entregues mas não confirmados.
docker compose exec redis redis-cli XPENDING stream:veiculos grupo-manual
```

Ao contrário de Redis Pub/Sub, o Redis Streams mantém entradas (até remoção/retenção configurada), suporta grupos e a lista de mensagens pendentes (*Pending Entries List*). `XACK` não apaga a entrada do Stream; apenas remove sua pendência no grupo.


## 6. Experimentos de System Design

### Experimento A — Cache-Aside e latência

1. Crie o veículo e anote o ID.
2. `docker compose exec redis redis-cli DEL vehicle:ID`.
3. `curl -i` no GET: `MISS`.
4. Repita GET: `HIT`.
5. `docker compose exec redis redis-cli TTL vehicle:ID`; aguarde TTL e observe novo `MISS`.

### Experimento B — tolerância à falha do Redis

```bash
docker compose stop redis
curl -i http://localhost:8080/api/v1/veiculos/ID
# 200 X-Cache: BYPASS se o veículo existe no PostgreSQL
# PUT/POST persistem no banco, mas o outbox fica pendente

docker compose start redis
# Aguarde o relay e verifique XRANGE + published_at
```

### Experimento C — consistência eventual e falha parcial

1. Faça `PUT` e, em outro terminal, monitore `docker compose logs -f api`.
2. Faça `GET` e `redis-cli GET vehicle:ID`; compare os campos.
3. Repita com Redis parado. PostgreSQL persistirá a atualização, mas a entrada antiga pode sobreviver e ficar obsoleta após o Redis retornar. Isso prova **por que duas escritas não são atomicamente equivalentes a uma transação**.
4. Para garantir uma leitura nova imediatamente, `DEL` a chave. Em sistemas reais, projete reconciliação e controle de versão.

### Experimento D — idempotência e Redis Streams

1. Inspecione `XRANGE` e `XPENDING`.
2. Desative o consumidor via `LAB_EVENTS_CONSUMER_ENABLED=false` no `.env` e recrie a API com `docker compose up -d --force-recreate api`.
3. Crie eventos e verifique que o Stream cresce, mas o consumer group não avança.
4. Reative e recrie a API; os eventos novos serão consumidos. Para recuperar mensagens **já entregues e pendentes de XACK**, estude `XPENDING` e `XAUTOCLAIM` (não implementados aqui).

### Experimento E — carga com k6 (opcional)

Crie um veículo, identifique o ID e execute (troque `1`):

```bash
VEHICLE_ID=1 docker compose --profile loadtest run --rm k6
```

O teste aumenta de 5 para 20 usuários virtuais e registra latências p95, erros e throughput. Compare a execução com cache aquecido versus Redis indisponível. A condição `p(95)<500ms` é **meta didática**, não SLO de produção.

## 7. Métricas e observabilidade

```bash
curl -s http://localhost:8080/actuator/metrics
curl -s http://localhost:8080/actuator/metrics/lab.cache.lookup
curl -s http://localhost:8080/actuator/prometheus | grep '^lab_'
```

Counters: `lab.cache.lookup{result="hit|miss|bypass"}`, `lab.outbox.published`, `lab.outbox.publish_error`. Há também as métricas de JVM, HTTP e Hikari geradas pelo Actuator. `HIT`, `MISS` e `BYPASS` representam leitura; não inferir consistência de dados apenas do hit ratio.

## 8. C4 Model e documentos

A API também disponibiliza os contratos YAML, sem depender de plugins de Swagger UI:

```bash
curl http://localhost:8080/docs/openapi.yaml
curl http://localhost:8080/docs/asyncapi.yaml
```

| Arquivo | Responsabilidade |
|---|---|
| `docs/c4/workspace.dsl` | Modelo C4 editável em Structurizr DSL (C1/C2/C3). |
| `docs/architecture.md` | Diagramas de sequência e decisões de design. |
| `docs/asyncapi.yaml` | Eventos Redis Streams, send/receive e payload. |
| `docs/openapi.yaml` | Contrato REST HTTP (OpenAPI 3.0). |
| `scripts/smoke.sh` | Teste ponta a ponta, via curl. |
| `src/test/.../VehicleServiceTest.java` | Testes unitários do serviço com Mockito. |

Para visualizar o C4 em um navegador, execute:

```bash
docker compose --profile docs up -d structurizr
# Abrir http://localhost:8081
```

Também há CI em `.github/workflows/ci.yml`, que executa `mvn verify` em pushes e pull requests no GitHub.

## 9. Organização hexagonal

```text
src/main/java/br/com/lab/vehiclecache/
├── domain/
│   ├── model/          Vehicle, VehicleCommand, VehicleChangedEvent, VehicleCacheChange
│   └── exception/      VehicleNotFoundException
├── application/
│   ├── port/in/        VehicleUseCase
│   ├── port/out/       VehicleRepositoryPort, VehicleCachePort,
│   │                   EventOutboxPort, AfterCommitEventPort
│   └── service/        VehicleService
└── adapter/
    ├── in/web/         VehicleController, Validation, ExceptionHandler
    ├── in/events/      RedisStreamAuditConsumer
    └── out/
        ├── persistence/ VehicleJpaAdapter, JPA entity/repository
        ├── cache/       RedisVehicleCacheAdapter
        └── events/      JdbcOutboxAdapter, AfterCommitCacheSynchronizer,
                        SpringAfterCommitPublisher, OutboxRelay
```

**Direção:** `adapters → ports ← use cases`, e a camada de domínio não conhece frameworks.

## 10. Limitações conhecidas (propositalmente didáticas)

- Não há autenticação/autorização; API/Actuator estão presos ao loopback do host pelo Compose. Não exponha o laboratório na Internet.
- Em produção, use secrets externos e migração para um Spring Boot com suporte OSS vigente.
- `OutboxRelay` só suporta **uma instância de API** neste estágio (não usa locks/leases).
- O consumidor não trata redelivery/recovery de PEL; requer deduplicação persistida pelo `eventId` para processamento de negócio.
- Redis `noeviction` protege a retenção de mensagens contra expulsão silenciosa, mas pode produzir erros de escrita em pressão de memória.
- Cache e eventos compartilham instância para facilitar o estudo; devem ser isolados em ambientes críticos.
- Atualizações concorrentes de cache e write-behind precisam CAS/versionamento se houver requisito de consistência forte.

## 11. Referências oficiais

- Spring Boot: https://docs.spring.io/spring-boot/
- Spring Data Redis Streams: https://docs.spring.io/spring-data/redis/reference/redis/redis-streams.html
- AsyncAPI 3.0: https://www.asyncapi.com/docs/reference/specification/v3.0.0
- Redis Streams: https://redis.io/docs/latest/develop/data-types/streams/
- Structurizr DSL: https://docs.structurizr.com/dsl

---

Este laboratório foi pensado como sequência de estudo: **cache → consistência → eventos → falhas → observabilidade**, com base no tema Estratégias de Uso de Cache do livro *System Design Workbook*, de Matheus Fidelis.


## AsyncAPI integrado ao Java

A publicação e o consumo agora validam o contrato em tempo de execução. Veja [guia de integração](docs/ASYNCAPI-INTEGRATION.md). Execute `mvn test` para validar o contrato empacotado e os casos negativos.
