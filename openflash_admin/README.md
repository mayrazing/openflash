# OpenFlash Admin

OpenFlash Admin is a separate administration application. `admin_back` shares the `openflash_db` database and `openflash` schema with `openflash_back`, `admin_front` talks only to `admin_back`, and platform AI operations go through `openflash_ai_runtime`.

## Service secrets

The services use these non-empty values:

- `OPENFLASH_ADMIN_INTERNAL_TOKEN` is shared only by `openflash_back` and `admin_back` for core administration calls.
- `OPENFLASH_AI_RUNTIME_ADMIN_TOKEN` authenticates `admin_back` to `openflash_ai_runtime`.
- `OPENFLASH_AI_RUNTIME_CORE_TOKEN` authenticates `openflash_back` to `openflash_ai_runtime`.
- `OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD` is standard Base64 whose decoded value is at least 32 bytes.
- `OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT` is standard Base64 whose decoded value is at least 16 bytes.

OPENFLASH_AI_RUNTIME_ADMIN_TOKEN and OPENFLASH_AI_RUNTIME_CORE_TOKEN must use different non-empty values. Never print any token or encryption value. Do not pass them to either frontend.

For local development, `openflash_user/start-dev.sh`, `admin_start.sh`, and `openflash_ai_runtime/openflash_ai_runtime.sh` generate all service and personal-AI encryption values on first use. They save them in `${XDG_STATE_HOME:-$HOME/.local/state}/openflash/dev-secrets.env` with file mode `600` and reuse them. Explicit environment variables take priority without replacing saved values. Manual Gradle starts still require the relevant values to be exported.

The default database is `jdbc:postgresql://localhost:5432/openflash_db?currentSchema=openflash` with username `postgres` and password `root`. `OPENFLASH_DB_URL`, `OPENFLASH_DB_USERNAME`, and `OPENFLASH_DB_PASSWORD` override it. The runtime defaults to `http://127.0.0.1:8082`, and the core defaults to `http://127.0.0.1:8080`. A browser may open the runtime root status page, but application code must never call its internal APIs directly.

## Database initialization and degraded behavior

openflash_back is the only Flyway owner. A fresh database needs one successful openflash_back startup before admin_back or openflash_ai_runtime can use it.

After that initialization, admin_back and openflash_ai_runtime can run while openflash_back is offline. Permanent user deletion remains unavailable while openflash_back is offline; admin_back reports "User service is not running". Personal AI remains available when openflash_ai_runtime is offline because it stays in pw_user_ai_config and is still handled by openflash_back.

Both `admin_back` and `openflash_ai_runtime` set `spring.flyway.enabled=false`. They never create or migrate tables.

For a fresh database, or whenever migrations must run, start the core independently:

```bash
cd openflash_user/openflash_back
export OPENFLASH_ADMIN_INTERNAL_TOKEN=replace-with-admin-core-secret
export OPENFLASH_AI_RUNTIME_CORE_TOKEN=replace-with-runtime-core-secret
export AI_ENCRYPTOR_PASSWORD=replace-with-personal-ai-password
./gradlew bootRun
```

Once one core startup has completed the migrations, the core may be stopped if permanent user deletion and the user application are not needed.

## Independent manual starts

Start PostgreSQL first. Then start the runtime:

```bash
cd openflash_ai_runtime
./openflash_ai_runtime.sh
```

The runtime is optional at admin startup. Start the admin backend independently:

```bash
cd openflash_admin/admin_back
export OPENFLASH_ADMIN_INTERNAL_TOKEN=replace-with-admin-core-secret
export OPENFLASH_AI_RUNTIME_ADMIN_TOKEN=replace-with-runtime-admin-secret
./gradlew bootRun
```

Start the admin frontend:

```bash
cd openflash_admin/admin_front
npm install
npm run dev
```

Open `http://127.0.0.1:5174` and log in with existing OpenFlash `root` credentials.

## One-command admin launcher

Start PostgreSQL, then run:

```bash
cd openflash_admin
./admin_start.sh
```

admin_start.sh starts only admin_back and admin_front. It does not start PostgreSQL, openflash_back, or openflash_ai_runtime. It waits for admin_back before starting admin_front. Runtime-backed AI features report an unavailable state while the runtime is offline and recover after the independent runtime starts.

Press `Ctrl+C` to stop only the two admin child process groups created by this launcher. Independent services are not stopped.

The admin application has no registration flow and uses existing OpenFlash credentials. An
`ADMIN` role alone is not sufficient: migration V65 requires a verified operator to set the
account's explicit admin approval before the first admin login. Never approve an account merely
because its username is `root`.

Global Codex switch changes can take up to 60 seconds to become effective because the runtime caches feature flags briefly.
