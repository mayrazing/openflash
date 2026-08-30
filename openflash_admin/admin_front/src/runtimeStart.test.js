import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { env } from 'node:process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const SCRIPT_PATH = fileURLToPath(new URL('../../../openflash_ai_runtime/openflash_ai_runtime.sh', import.meta.url))
const DEV_SECRETS_HELPER_PATH = fileURLToPath(new URL('../../../scripts/dev-secrets.sh', import.meta.url))
const REQUIRED_ENV = {
  OPENFLASH_ADMIN_INTERNAL_TOKEN: 'admin-core-secret',
  OPENFLASH_AI_RUNTIME_ADMIN_TOKEN: 'runtime-admin-secret',
  OPENFLASH_AI_RUNTIME_CORE_TOKEN: 'runtime-core-secret',
  OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD: 'platform-password-secret',
  OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT: 'platform-salt-secret',
  AI_ENCRYPTOR_PASSWORD: 'personal-ai-password-secret',
  AI_ENCRYPTOR_SALT: 'personal-ai-salt-secret',
}

async function takeAvailablePort() {
  const server = createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  assert.notEqual(address, null)
  assert.equal(typeof address, 'object')
  await new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve())
  })
  return address.port
}

async function occupyPort(t) {
  const server = createServer(socket => socket.end())
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  assert.notEqual(address, null)
  assert.equal(typeof address, 'object')
  t.after(() => new Promise((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve())
  }))
  return address.port
}

function runToExit(command, childEnv) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, [], { env: childEnv })
    let output = ''
    child.stdout.on('data', chunk => { output += chunk })
    child.stderr.on('data', chunk => { output += chunk })
    child.once('error', reject)
    child.once('exit', (code, signal) => resolve({ code, signal, output }))
  })
}

async function createLauncherFixture(t, requestedPort) {
  const port = requestedPort ?? await takeAvailablePort()
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'openflash-runtime-start-'))
  const runtimeRoot = join(fixtureRoot, 'openflash_ai_runtime')
  const scriptsDir = join(fixtureRoot, 'scripts')
  const logPath = join(fixtureRoot, 'runtime.log')
  const fixtureScript = join(runtimeRoot, 'openflash_ai_runtime.sh')

  await Promise.all([
    mkdir(runtimeRoot),
    mkdir(scriptsDir),
  ])
  await writeFile(fixtureScript, (await readFile(SCRIPT_PATH, 'utf8')).replaceAll('8082', String(port)), { mode: 0o755 })
  await writeFile(join(scriptsDir, 'dev-secrets.sh'), await readFile(DEV_SECRETS_HELPER_PATH, 'utf8'), { mode: 0o755 })
  await writeFile(join(runtimeRoot, 'gradlew'), [
    '#!/usr/bin/env bash',
    'printf "runtime-start|%s|%s\\n" "$PWD" "$*" >> "$RUNTIME_START_TEST_LOG"',
    'for name in OPENFLASH_ADMIN_INTERNAL_TOKEN OPENFLASH_AI_RUNTIME_ADMIN_TOKEN OPENFLASH_AI_RUNTIME_CORE_TOKEN OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT AI_ENCRYPTOR_PASSWORD AI_ENCRYPTOR_SALT; do',
    '  if [[ -n "${!name:-}" ]]; then printf "runtime-env|%s|set\\n" "$name" >> "$RUNTIME_START_TEST_LOG"; else printf "runtime-env|%s|unset\\n" "$name" >> "$RUNTIME_START_TEST_LOG"; fi',
    'done',
    'printf "runtime-bind|%s|%s\\n" "$OPENFLASH_AI_RUNTIME_ADDRESS" "$OPENFLASH_AI_RUNTIME_PORT" >> "$RUNTIME_START_TEST_LOG"',
    '',
  ].join('\n'), { mode: 0o755 })

  t.after(() => rm(fixtureRoot, { recursive: true, force: true }))
  return { fixtureRoot, fixtureScript, logPath, port, runtimeRoot }
}

function launcherEnv(fixture, extraEnv = {}) {
  return {
    ...env,
    ...REQUIRED_ENV,
    RUNTIME_START_TEST_LOG: fixture.logPath,
    XDG_STATE_HOME: join(fixture.fixtureRoot, 'state'),
    ...extraEnv,
  }
}

test('starts only the runtime with its scoped environment', async t => {
  const fixture = await createLauncherFixture(t)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture))
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 0)
  assert.match(log, new RegExp(`runtime-start\\|${fixture.runtimeRoot}\\|--console=plain bootRun`))
  assert.match(log, new RegExp(`runtime-bind\\|127\\.0\\.0\\.1\\|${fixture.port}`))
  for (const name of [
    'OPENFLASH_AI_RUNTIME_ADMIN_TOKEN',
    'OPENFLASH_AI_RUNTIME_CORE_TOKEN',
    'OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD',
    'OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT',
  ]) {
    assert.match(log, new RegExp(`runtime-env\\|${name}\\|set`))
  }
  for (const name of ['OPENFLASH_ADMIN_INTERNAL_TOKEN', 'AI_ENCRYPTOR_PASSWORD', 'AI_ENCRYPTOR_SALT']) {
    assert.match(log, new RegExp(`runtime-env\\|${name}\\|unset`))
  }
})

test('rejects one token reused for both runtime scopes', async t => {
  const fixture = await createLauncherFixture(t)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    OPENFLASH_AI_RUNTIME_CORE_TOKEN: REQUIRED_ENV.OPENFLASH_AI_RUNTIME_ADMIN_TOKEN,
  }))

  assert.equal(result.code, 1)
  assert.match(result.output, /must use different values/)
  await assert.rejects(readFile(fixture.logPath, 'utf8'), error => error.code === 'ENOENT')
})

test('refuses to replace an existing runtime listener', async t => {
  const occupiedPort = await occupyPort(t)
  const fixture = await createLauncherFixture(t, occupiedPort)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture))

  assert.equal(result.code, 1)
  assert.match(result.output, new RegExp(`port ${occupiedPort} is already in use`, 'i'))
  await assert.rejects(readFile(fixture.logPath, 'utf8'), error => error.code === 'ENOENT')
})
