# OpenFlash AI Runtime

`openflash_ai_runtime` is the private backend for platform-provided AI connections, encrypted platform credentials, live model catalogs, CLI authorization, and platform generation. `admin_back` uses the admin scope; `openflash_back` uses the core scope.

openflash_ai_runtime listens on 127.0.0.1:8082 by default. Its safe startup probe is GET http://127.0.0.1:8082/health. A browser may open http://127.0.0.1:8082/ to see the public started page; all internal APIs remain token-protected.

Browsers must not connect to openflash_ai_runtime directly.

## Required environment

- `OPENFLASH_AI_RUNTIME_ADMIN_TOKEN`: non-empty admin-scope token used by `admin_back`.
- `OPENFLASH_AI_RUNTIME_CORE_TOKEN`: non-empty core-scope token used by `openflash_back`.
- `OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD`: standard Base64 whose decoded value is at least 32 bytes.
- `OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT`: standard Base64 whose decoded value is at least 16 bytes.

OPENFLASH_AI_RUNTIME_ADMIN_TOKEN and OPENFLASH_AI_RUNTIME_CORE_TOKEN must use different non-empty values. Never print any token or encryption value. Store the encryption values securely and keep them stable so existing platform credentials remain decryptable.

The default database connection is `jdbc:postgresql://localhost:5432/openflash_db?currentSchema=openflash` with username `postgres` and password `root`. Override it with `OPENFLASH_DB_URL`, `OPENFLASH_DB_USERNAME`, and `OPENFLASH_DB_PASSWORD`.

## Independent launcher

For local development, run:

```bash
./openflash_ai_runtime.sh
```

The launcher loads or creates the shared local development secrets, binds the runtime to `127.0.0.1:8082`, and runs it in the foreground. It does not start the user or admin applications.

## Manual Maven start

Start PostgreSQL, export the four required values in the process environment, then run from the repository root:

```bash
cd openflash_ai_runtime
export OPENFLASH_AI_RUNTIME_ADMIN_TOKEN=replace-with-runtime-admin-secret
export OPENFLASH_AI_RUNTIME_CORE_TOKEN=replace-with-different-runtime-core-secret
export OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD=replace-with-standard-base64-32-byte-minimum
export OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT=replace-with-standard-base64-16-byte-minimum
./mvnw spring-boot:run
```

The default bind can be changed for an isolated deployment with `OPENFLASH_AI_RUNTIME_ADDRESS` and `OPENFLASH_AI_RUNTIME_PORT`. Keep the runtime private and update `OPENFLASH_AI_RUNTIME_BASE_URL` in callers together. The project launchers intentionally use `127.0.0.1:8082`.

## Database ownership and offline behavior

openflash_back is the only Flyway owner. A fresh database needs one successful openflash_back startup before admin_back or openflash_ai_runtime can use it.

After that initialization, admin_back and openflash_ai_runtime can run while openflash_back is offline. `openflash_ai_runtime` sets `spring.flyway.enabled=false` and never creates or migrates tables.

Personal AI remains available when openflash_ai_runtime is offline because it stays in pw_user_ai_config and is still handled by openflash_back. Platform-provided AI is unavailable until the runtime returns; callers must report that degraded state and must not silently switch the user's active AI.
