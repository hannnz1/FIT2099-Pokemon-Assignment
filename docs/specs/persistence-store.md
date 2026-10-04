# World checkpoint storage

`game.agent.persistence.WorldStore` saves a trusted `Map<String,Object>` checkpoint for one opaque browser owner. `load(owner)` returns a detached JSON object or `null` when no checkpoint exists. `close()` prevents later work. Stores do not execute saved content. The room remains responsible for validating gameplay fields, restoring tasks paused with fresh identities, clearing approvals, and stopping further mutations after a storage error.

Both implementations wrap the supplied checkpoint in a version 1 JSON document containing a SHA-256 owner digest and the checkpoint. The original owner value is not stored. The document must be at most 1 MiB in UTF-8, no more than 8,000 traversed nodes and 24 nested levels. Unsupported Java types, cycles, invalid Unicode/numbers, duplicate JSON keys, unknown envelope versions and owner-digest mismatches are rejected. The body may include its own independent gameplay `schemaVersion`.

Credential fields are rejected recursively: `apiKey`, `approvalToken`, `csrfToken`, `password`, `authorization`, `secret`, `accessToken`, `refreshToken`, `privateKey`, including case/underscore/hyphen variants. Recognizable Bearer, OpenAI and Gemini credential strings are rejected too. This is an additional safeguard; the trusted checkpoint exporter must omit credentials, hidden reasoning and approval tokens before calling the repository.

## PostgreSQL

Supply the PostgreSQL JDBC driver on the runtime classpath; no driver or database service is installed by the repository. Instantiate `JdbcWorldStore(() -> DriverManager.getConnection(url, properties))`. Keep credentials in environment/configuration outside checkpoint data and public responses. Configure the driver's `connectTimeout` and `socketTimeout` (for example 5 seconds); statements have a 5-second timeout. The connection factory must return a fresh, exclusively owned connection because the store closes it after each operation.

Use a dedicated PostgreSQL database and role. The constructor initializes only `pokemon_agent_world_checkpoints`, equivalent to [pokemon-agent-storage.sql](../sql/pokemon-agent-storage.sql). For managed deployments, provision the table first and grant the application role SELECT/INSERT/UPDATE on it. No migrations of unrelated tables or schemas occur.

Each parameterized upsert runs in an explicit transaction. The row contains the complete world, NPC memories and trace, so readers cannot see a mixed checkpoint assembled from different turns. Statement or commit failure triggers rollback. A lost connection around commit can have an unknown outcome: the room must fail closed and reload before allowing further changes. Multiple store instances may read/write; PostgreSQL chooses the last committed complete checkpoint for an owner. This is a single-server world repository, not distributed world ownership arbitration.

Database errors expose only `DATABASE_ERROR`; driver messages, URLs and passwords are not attached to public exceptions. Use `StoreException.getCode()` for finite responses. Other codes are `INVALID_OWNER`, `INVALID_CHECKPOINT`, `TOO_LARGE`, `CORRUPT`, `IO_ERROR`, `CLOSED`.

## Local development fallback

`FileWorldStore(Path directory)` creates its directory. Owners use hashed filenames, so slashes/quotes in an opaque owner cannot escape that directory. The store validates and serializes before touching the previous checkpoint, forces the temporary file contents to disk, then atomically replaces the owner file. If the filesystem cannot perform atomic replacement, save fails with `IO_ERROR`; it does not fall back to a partial overwrite. Malformed, oversized or symlink checkpoint files fail closed. Temporary files are removed after ordinary failure. Use a private directory with application-only access. The file implementation is a development fallback and is not PostgreSQL; durability across machine power loss depends on the local filesystem's directory metadata guarantees.

## Verification

Focused Java 8 ECJ compilation plus JUnit tests verify roundtrip/reopen, detached data, nested memories/trace, owner isolation, traversal-safe filenames, malformed/oversized files, version and owner tampering, invalid-owner Unicode, credential rejection, previous-checkpoint preservation, closed stores, JDBC statement failure and transaction rollback.

`PostgresWorldStoreTest` runs only when `pokemon.test.jdbcUrl` is supplied. It needs the external JDBC driver and an isolated disposable database, and optionally `pokemon.test.jdbcUser` (default `pokemon_v1`). It creates unique test owners, performs actual PostgreSQL roundtrip/reopen/isolation, executes a real upsert then injects a failure before commit, and confirms rollback preserved the old memories/trace. This deliberately tests rollback before commit; it does not pretend to simulate an ambiguous lost response after a completed commit.

On 2026-09-30 the test ran against the task-owned localhost PostgreSQL cluster on port 55439 with the externally supplied PostgreSQL JDBC 42.7.11 driver. All 11 focused persistence tests passed, including the real database test. No existing database/service was modified.
