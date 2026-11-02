SHELL := /bin/bash
.PHONY: up down reset logs smoke test docs
up:
	docker compose up --build -d
down:
	docker compose down
reset:
	docker compose down -v
logs:
	docker compose logs -f api
smoke:
	bash scripts/smoke.sh
test:
	bash scripts/run-tests.sh
loadtest:
	docker compose --profile loadtest run --rm k6
docs:
	docker compose --profile docs up -d structurizr
