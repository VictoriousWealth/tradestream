# Devlog

## 2025-08-02 07:50:49 +0100 — commit `b14a6bf313c0be3e85b9b4f6b7b336c8ec7d920f`

This chunk contains a single infrastructure-focused commit that did two related things:

- added `market-data-consumer` as a runnable service in [`docker-compose.yml`](docker-compose.yml)
- removed an empty Maven placeholder file at [`market-data-consumer/pom.xml`](market-data-consumer/pom.xml)

## Compose topology expanded to include market data as its own runtime path

Before this commit, the compose file only described the authentication and registration stack plus the shared auth database. After the commit, the service graph gained a dedicated market-data branch.

### `market-data-consumer` service was introduced

The new compose stanza for `market-data-consumer` defines:

- build context: `./market-data-consumer`
- Dockerfile-based image build
- image tag: `market-data-consumer:dev`
- port mapping: `8083:8083`
- dependency on `market_postgres`

and injects environment variables for:

- `SERVER_PORT=8083`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://market_postgres:5432/marketdb`
- `SPRING_DATASOURCE_USERNAME=marketuser`
- `SPRING_DATASOURCE_PASSWORD=marketpass`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- Flyway enablement and migration location

That means the change was not just “add a service name to compose.” It fully declared the service as a Spring Boot container with its own port, its own database credentials, and Flyway-driven schema management.

The before-to-after evolution here is clear:

- before: market-data-consumer may have existed in the repo, but it was not part of the compose runtime described by this file
- after: it became a first-class compose service with explicit startup dependency and DB wiring

## Dedicated market-data PostgreSQL instance was added

The same commit also introduced a second database container:

- `market_postgres`

with:

- image `postgres:15-alpine`
- container name `market_postgres`
- port mapping `5433:5432`
- database `marketdb`
- user `marketuser`
- password `marketpass`
- persistent volume `market_postgres_data`

This is an important architectural signal. Before the change, the only database declared in compose was:

- `postgres` serving `authdb`

After the change, market data received its own isolated database container rather than being colocated in the auth database. That matches the broader microservice-per-datastore direction already visible elsewhere in the repo.

The new top-level volume:

- `market_postgres_data`

completed that separation by giving the new database its own persistence boundary.

## Service/database coupling changed from single-DB compose to multi-DB compose

Taken together, the compose modifications changed the local runtime model from:

- auth-service
- user-registration-service
- one shared auth database

to:

- auth-service
- user-registration-service
- market-data-consumer
- auth database
- market-data database

That is the main system evolution in this chunk. It is an infrastructure expansion, not a code-path change, but it materially alters what a local deployment can run.

## Empty Maven placeholder was removed from `market-data-consumer`

The second file in the chunk, [`market-data-consumer/pom.xml`](market-data-consumer/pom.xml), was deleted. The diff shows it had been an empty file (`index e69de29`, zero-content placeholder).

Because the file had no content, the deletion did not remove an actual Maven build configuration. What it did remove was ambiguity. Before the commit, the service directory still carried an empty `pom.xml`, which implied either an unfinished Maven setup or a leftover scaffold. After the commit, that ambiguity was gone.

The strongest evidence-based interpretation is:

- compose now expects `market-data-consumer` to be built via its Dockerfile from the service directory,
- the repo no longer carries an empty Maven placeholder in that directory.

The patch does not expose the service’s actual build tool from within this chunk alone, so it would be unsafe to claim more than that. What is certain is that an empty Maven marker was removed at the same moment the service was added to compose, which suggests the service definition was being cleaned up as it became runnable.
