# Vigil

A self-hosted uptime monitor. Register URLs, they get checked every minute, and a status page shows which sites
are up, down or slow.

The application lives here. Its infrastructure (CI, Terraform, Ansible, Helm, monitoring) lives in
[`vigil_infra`](../vigil_infra).

## Architecture

```
browser ◀──── live updates (SSE) ────┐
   └── REST ──▶ api (Spring Boot) ──────────────▶ PostgreSQL
                  │ every 60s: one job per site        ▲
                  ▼                                    │ results
               Redis list "vigil:checks" ──▶ worker(s) (Spring Boot)
                                                       │ PUBLISH
               Redis channel "vigil:results" ◀─────────┘
                  └── SUBSCRIBE ──▶ every api replica ──▶ browsers
```

| Service    | Role | Port |
|------------|------|------|
| `api`      | REST API and status page. Owns the database schema (Flyway), schedules checks, pushes live results to browsers | 8080 |
| `worker`   | Takes jobs from Redis, performs the HTTP checks, stores and publishes the results. Stateless: run as many as needed | 8081 |
| `postgres` | Sites and check results | 5432 |
| `redis`    | Job queue (list) and result broadcast (Pub/Sub) | 6379 |

The api never calls a worker directly: it drops jobs into the queue and any free worker takes one. Workers can be
added or removed without touching the api, and jobs wait in the queue while no worker is running.

## Getting started

Requirements: Java 25 and Docker.

```bash
cp .env.example .env          # then set real passwords
docker compose up -d          # PostgreSQL and Redis, bound to localhost
./run.sh api                  # http://localhost:8080
./run.sh worker               # in another terminal
```

`run.sh` loads `.env` and starts the service with the Maven wrapper. Extra arguments are passed to Maven.

Flyway creates the schema when the api starts. To fill the database with example sites and checks:

```bash
docker compose exec -T postgres psql -U admin -d vigil < dev-seed.sql
```

To start over with an empty database: `docker compose down -v`.

## Testing

`test-api.sh` runs HTTP tests against a running api and exits non-zero if any fail. Tests that depend on the
example data are skipped when `dev-seed.sql` has not been loaded.

```bash
./test-api.sh
BASE=http://other-host:8080 ./test-api.sh
```

## API

| Method   | Path | Description |
|----------|------|-------------|
| `GET`    | `/api/sites` | Every site with its current status: `UP`, `DOWN`, `SLOW` or `PENDING` (never checked) |
| `POST`   | `/api/sites` | Register a site: `{"name": "Example", "url": "https://example.com"}` |
| `DELETE` | `/api/sites/{id}` | Remove a site and its check history |
| `GET`    | `/api/sites/{id}/checks?limit=60` | Most recent checks, newest first. `limit` from 1 to 200 |
| `GET`    | `/api/sites/{id}/history?hours=24` | One entry per 10-minute period: average and max response time, number of checks and failures. `hours` from 1 to 168 |

Errors: `400` invalid input, `404` unknown site, `409` URL already registered.

Example response from `GET /api/sites`:

```json
[
  {
    "id": 1,
    "name": "Example",
    "url": "https://example.com",
    "status": "UP",
    "responseTimeMs": 143,
    "checkedAt": "2026-10-07T09:00:00Z"
  }
]
```

A site is `SLOW` when it is up but its last response took more than 1000 ms.

## Configuration

Both services read their settings from environment variables. `run.sh` and Docker Compose load them from `.env`.

| Variable | Default | |
|----------|---------|-|
| `DB_HOST` | `localhost` | |
| `DB_PORT` | `5432` | |
| `DB_NAME` | `vigil` | |
| `DB_USER` | `admin` | |
| `DB_PASSWORD` | | required |
| `REDIS_HOST` | `localhost` | |
| `REDIS_PORT` | `6379` | |
| `REDIS_PASSWORD` | | required |

## Project layout

```
api/           Spring Boot api: REST endpoints, scheduler, Flyway migrations (src/main/resources/db/migration)
worker/        Spring Boot worker: HTTP checks
compose.yaml   PostgreSQL and Redis for local development
run.sh         starts api or worker with .env loaded
dev-seed.sql   example sites and checks
test-api.sh    HTTP tests for the api
```
