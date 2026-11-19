# Task Scheduling Service

A Java 21 / Spring Boot REST API for scheduling tasks. MySQL is the source of truth; Redis is an optional cache for terminal task records, and a transactional outbox retries RocketMQ publication without losing due tasks.

## Requirements and startup

- Java 21+
- Docker Compose

Start the provided MySQL, Redis, and RocketMQ services from this directory:

```shell
docker compose up -d
```

Then run the API:

```shell
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. MySQL defaults match the Compose file (`taskuser` / `taskpass`, database `taskdb`). The API listens on port 8080.

Configuration can be overridden with environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/taskdb?...` | MySQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `taskuser` / `taskpass` | MySQL credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `REDIS_CACHE_ENABLED` | `false` | Enable terminal-task Redis cache |
| `ROCKETMQ_NAMESRV_ADDR` | `localhost:9876` | RocketMQ name server |
| `ROCKETMQ_PRODUCER_GROUP` | `task-scheduling-producer` | Producer group |
| `ROCKETMQ_TOPIC` | `task-schedule-topic` | Trigger topic |
| `SERVER_PORT` | `8080` | HTTP port |
| `SCHEDULER_POLL_INTERVAL_MS` | `1000` | Due-task polling interval |
| `SCHEDULER_BATCH_SIZE` | `100` | Due-task batch size |
| `OUTBOX_POLL_INTERVAL_MS` | `1000` | Outbox polling interval |
| `OUTBOX_BATCH_SIZE` | `100` | Outbox batch size |
| `OUTBOX_MAX_BACKOFF_SECONDS` | `300` | Maximum retry delay |

Schema is created with `schema.sql` on application startup. `init.sql` contains the same tables for a fresh Compose MySQL volume.

## API

Create a task with a caller-provided unique ID and future execution time:

```shell
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskId":"abc-123","executeAt":"2030-07-21T15:00:00Z","payload":{"type":"email","target":"hello@example.com"}}'
```

- `GET /tasks/{taskId}` gets a task.
- `DELETE /tasks/{taskId}` cancels a pending task. Repeating cancellation is safe; tasks already dispatching or triggered return `409 Conflict`.
- `GET /tasks?status=PENDING&page=0&size=20` lists a status-filtered page. Status values are `PENDING`, `DISPATCHING`, `TRIGGERED`, and `CANCELLED`; page size is limited to 100.

Requests with missing fields, invalid JSON, a non-future `executeAt`, invalid status, or invalid pagination return `400`. Missing tasks return `404`; duplicate IDs return `409`. Errors use Spring's `ProblemDetail` response format.

## Delivery behavior

The scheduler selects due pending tasks in bounded batches under database row locks, changes each to `DISPATCHING`, and inserts its outbox row in the same MySQL transaction. The outbox dispatcher publishes to RocketMQ, then marks the outbox entry sent and task triggered. Failures are retried with capped exponential backoff.

Delivery is **at least once**: a process crash after a successful broker publish but before the MySQL commit can cause a repeat. The message body contains `taskId`, `executeAt`, and `payload`; messages also use the task ID as the RocketMQ key and `idempotencyKey` property. Consumers should deduplicate on that key. Cancellation is atomic against scheduling: only `PENDING` tasks can be cancelled.

Redis is disabled by default. When enabled with `REDIS_CACHE_ENABLED=true`, it caches only terminal (`TRIGGERED` / `CANCELLED`) task records, which are immutable. Redis failures fall back to MySQL and never affect task correctness.

## Tests

Run unit tests with `./mvnw test` (Windows: `mvnw.cmd test`). Tests do not require Docker services.
