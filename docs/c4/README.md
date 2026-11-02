# Diagramas C4

- Fonte editável: `workspace.dsl` (Structurizr DSL).
- Para visualizar, na raiz do repositório rode: `docker compose --profile docs up -d structurizr`.
- Abra **http://localhost:8081** no navegador.
- Diagramas disponíveis: C1 (contexto), C2 (contêineres) e C3 (componentes).
- `architecture.md` contém sequências Mermaid para os fluxos temporais.

Importante: o modelo representa um laboratório com uma única instância da API. Para escalar o relay horizontalmente, é necessária coordenação entre workers (leasings/locks).
