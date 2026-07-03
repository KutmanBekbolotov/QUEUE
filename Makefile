COMPOSE ?= docker compose

.PHONY: dev build test migrate logs down

dev:
	$(COMPOSE) up --build

build:
	$(COMPOSE) build

test:
	cd apps/middleware-nest && npm test
	$(COMPOSE) run --rm backend-test

migrate:
	$(COMPOSE) up -d postgres
	$(COMPOSE) run --rm flyway

logs:
	$(COMPOSE) logs -f

down:
	$(COMPOSE) down --remove-orphans
