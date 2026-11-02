
workspace "Vehicle Cache Lab - C4 Model" "C1 Contexto, C2 Containers e C3 Componentes da API Hexagonal" {

    model {
        developer = person "Cliente HTTP" "Pessoa ou script curl que consome a API."

        vehicleSystem = softwareSystem "Vehicle Cache Lab" "Laboratorio de cache e mensageria com arquitetura hexagonal." {

            api = container "Vehicle API" "REST, Cache-Aside, Write-Through, Outbox e consumer" "Java 21 / Spring Boot" {
                rest = component "REST Adapter" "VehicleController / JSON e validacao" "Spring MVC"
                service = component "Use Cases" "VehicleService: regras e portas" "Java"
                persistence = component "Persistence Adapter" "JpaAdapter + JdbcOutboxAdapter" "JPA / JDBC"
                cacheSync = component "Post-Commit Cache Sync" "Atualizacao e invalidacao do cache apos commit" "Spring Transaction Events"
                cache = component "Cache Adapter" "RedisVehicleCacheAdapter" "Spring Data Redis"
                relay = component "Outbox Relay" "Publicacao XADD a partir do outbox" "JdbcTemplate / Redis"
                auditor = component "Stream Audit Consumer" "XREADGROUP e XACK" "Spring Data Redis"
            }

            pg = container "PostgreSQL" "Veiculos e Outbox transacional" "PostgreSQL 17" "Database"
            redis = container "Redis" "Cache com TTL e Redis Streams" "Redis 7" "Database"
        }

        developer -> api "HTTP/JSON"
        api -> pg "SQL/JDBC"
        api -> redis "GET/SET/DEL, XADD, XREADGROUP, XACK"

        developer -> rest "POST/GET/PUT/DELETE"
        rest -> service "Invoca porta de entrada"

        service -> persistence "Portas VehicleRepository e EventOutbox"
        service -> cache "Consulta via VehicleCachePort"
        service -> cacheSync "Registra sincronizacao pos-commit"

        cacheSync -> cache "SET/DEL apos commit"
        persistence -> pg "Persistencia e outbox na mesma transacao"

        cache -> redis "GET/SET/DEL"
        relay -> pg "Le eventos pendentes e marca publicados"
        relay -> redis "XADD"
        auditor -> redis "XREADGROUP / XACK"
    }

    views {
        systemContext vehicleSystem "C1_Contexto" {
            include *
            autoLayout lr
        }

        container vehicleSystem "C2_Containers" {
            include *
            autoLayout lr
        }

        component api "C3_Componentes" {
            include *
            autoLayout lr
        }

        styles {
            element "Person" {
                background #08427b
                color #ffffff
                shape Person
            }

            element "Software System" {
                background #1168bd
                color #ffffff
            }

            element "Container" {
                background #438dd5
                color #ffffff
            }

            element "Component" {
                background #85bbf0
                color #000000
            }

            element "Database" {
                shape Cylinder
            }
        }
    }
}
