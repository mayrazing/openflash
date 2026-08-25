import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { env, execPath, kill as killProcess, umask as processUmask } from 'node:process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const SCRIPT_PATH = fileURLToPath(new URL('../../admin_start.sh', import.meta.url))
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
const EXPECTED_UMASK = processUmask().toString(8).padStart(4, '0')

async function takeAvailablePorts(count) {
  const servers = []
  try {
    for (let index = 0; index < count; index += 1) {
      const server = createServer()
      await new Promise((resolve, reject) => {
        server.once('error', reject)
        server.listen(0, '127.0.0.1', resolve)
      })
      servers.push(server)
    }
    return servers.map(server => {
      const address = server.address()
      assert.notEqual(address, null)
      assert.equal(typeof address, 'object')
      return address.port
    })
  } finally {
    await Promise.all(servers.map(server => new Promise((resolve, reject) => {
      server.close(error => error ? reject(error) : resolve())
    })))
  }
}

function runToExit(command, childEnv, timeoutMs = 1500) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, [], { env: childEnv })
    let output = ''
    const timeout = setTimeout(() => {
      child.kill('SIGKILL')
      reject(new Error(`Timed out waiting for command to exit: ${command}`))
    }, timeoutMs)

    child.stdout.on('data', chunk => { output += chunk })
    child.stderr.on('data', chunk => { output += chunk })
    child.once('error', error => {
      clearTimeout(timeout)
      reject(error)
    })
    child.once('exit', (code, signal) => {
      clearTimeout(timeout)
      resolve({ code, signal, output })
    })
  })
}

async function waitForLog(logPath, predicate) {
  let content = ''
  for (let attempt = 0; attempt < 160; attempt += 1) {
    try {
      content = await readFile(logPath, 'utf8')
    } catch (error) {
      if (error.code !== 'ENOENT') throw error
    }
    if (predicate(content)) return content
    await new Promise(resolve => setTimeout(resolve, 25))
  }
  throw new Error(`Timed out waiting for launcher log: ${logPath}\n${content}`)
}

async function createLauncherFixture(t, options = {}) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'openflash-admin-start-'))
  const adminRoot = join(fixtureRoot, 'openflash_admin')
  const binDir = join(fixtureRoot, 'bin')
  const coreDir = join(fixtureRoot, 'openflash_user', 'openflash_back')
  const backendDir = join(adminRoot, 'admin_back')
  const frontendDir = join(adminRoot, 'admin_front')
  const scriptsDir = join(fixtureRoot, 'scripts')
  const stateHome = join(fixtureRoot, 'state')
  const secretsPath = join(stateHome, 'openflash', 'dev-secrets.env')
  const logPath = join(fixtureRoot, 'processes.log')
  const backendReadyPath = join(fixtureRoot, 'backend.ready')
  const fixtureScript = join(adminRoot, 'admin_start.sh')

  await Promise.all([
    mkdir(binDir),
    mkdir(coreDir, { recursive: true }),
    mkdir(scriptsDir),
    mkdir(backendDir, { recursive: true }),
    mkdir(join(frontendDir, 'node_modules'), { recursive: true }),
  ])
  const [availableBackendPort, availableFrontendPort] = await takeAvailablePorts(2)
  const backendPort = options.backendPort ?? availableBackendPort
  const frontendPort = options.frontendPort ?? availableFrontendPort
  const script = await readFile(SCRIPT_PATH, 'utf8')
  await writeFile(fixtureScript, script
    .replaceAll('8081', String(backendPort))
    .replaceAll('5174', String(frontendPort)), { mode: 0o755 })
  try {
    await writeFile(join(scriptsDir, 'dev-secrets.sh'), await readFile(DEV_SECRETS_HELPER_PATH, 'utf8'), { mode: 0o755 })
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }

  await writeFile(join(backendDir, 'mvnw'), [
    '#!/usr/bin/env bash',
    'printf "backend-wrapper-pid|%s\\n" "$$" >> "$ADMIN_START_TEST_LOG"',
    'printf "backend-start|%s|%s\\n" "$PWD" "$*" >> "$ADMIN_START_TEST_LOG"',
    'printf "backend-umask|%s\\n" "$(umask)" >> "$ADMIN_START_TEST_LOG"',
    'if [[ "${ADMIN_START_TEST_CHILD_EXIT:-0}" == "1" ]]; then exit 7; fi',
    'if [[ -n "${ADMIN_START_TEST_BACKEND_EXIT_DELAY:-}" ]]; then',
    '  sleep "$ADMIN_START_TEST_BACKEND_EXIT_DELAY"',
    '  printf "backend-fail\\n" >> "$ADMIN_START_TEST_LOG"',
    '  exit 7',
    'fi',
    'for name in OPENFLASH_ADMIN_INTERNAL_TOKEN OPENFLASH_AI_RUNTIME_ADMIN_TOKEN OPENFLASH_AI_RUNTIME_CORE_TOKEN OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT AI_ENCRYPTOR_PASSWORD AI_ENCRYPTOR_SALT; do',
    '  if [[ -n "${!name:-}" ]]; then printf "backend-env|%s|set\\n" "$name" >> "$ADMIN_START_TEST_LOG"; else printf "backend-env|%s|unset\\n" "$name" >> "$ADMIN_START_TEST_LOG"; fi',
    'done',
    'trap \'printf "backend-stop\\n" >> "$ADMIN_START_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(coreDir, 'mvnw'), [
    '#!/usr/bin/env bash',
    'printf "forbidden-core-start\\n" >> "$ADMIN_START_TEST_LOG"',
    'exit 9',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'psql'), [
    '#!/usr/bin/env bash',
    'printf "forbidden-postgresql-start\\n" >> "$ADMIN_START_TEST_LOG"',
    'exit 9',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'mysql'), [
    '#!/usr/bin/env bash',
    'printf "forbidden-mysql-start\\n" >> "$ADMIN_START_TEST_LOG"',
    'exit 9',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'npm'), [
    '#!/usr/bin/env bash',
    'printf "frontend-wrapper-pid|%s\\n" "$$" >> "$ADMIN_START_TEST_LOG"',
    'printf "frontend-start|%s|%s\\n" "$PWD" "$*" >> "$ADMIN_START_TEST_LOG"',
    'for name in OPENFLASH_ADMIN_INTERNAL_TOKEN OPENFLASH_AI_RUNTIME_ADMIN_TOKEN OPENFLASH_AI_RUNTIME_CORE_TOKEN OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT AI_ENCRYPTOR_PASSWORD AI_ENCRYPTOR_SALT; do',
    '  if [[ -n "${!name:-}" ]]; then printf "frontend-env|%s|set\\n" "$name" >> "$ADMIN_START_TEST_LOG"; else printf "frontend-env|%s|unset\\n" "$name" >> "$ADMIN_START_TEST_LOG"; fi',
    'done',
    'trap \'printf "frontend-stop\\n" >> "$ADMIN_START_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'curl'), [
    '#!/usr/bin/env bash',
    'printf "probe|%s\\n" "$*" >> "$ADMIN_START_TEST_LOG"',
    'printf "curl-pid|%s\\n" "$$" >> "$ADMIN_START_TEST_LOG"',
    'max_time=""',
    'write_out=false',
    'fail_status=false',
    'expect_max_time=false',
    'for argument in "$@"; do',
    '  if [[ "$expect_max_time" == "true" ]]; then max_time="$argument"; expect_max_time=false; continue; fi',
    '  if [[ "$argument" == "--max-time" ]]; then expect_max_time=true; fi',
    '  if [[ "$argument" == "--write-out" ]]; then write_out=true; fi',
    '  if [[ "$argument" == "--fail" ]]; then fail_status=true; fi',
    'done',
    'if [[ "${ADMIN_START_TEST_STALL_PROBE:-}" == "backend" ]]; then',
    '  if [[ -z "$max_time" ]]; then while true; do sleep 1; done; fi',
    '  sleep "$max_time"',
    '  printf "probe-timeout|backend|%s\\n" "$max_time" >> "$ADMIN_START_TEST_LOG"',
    '  if [[ "$write_out" == "true" ]]; then printf "200"; fi',
    '  exit 28',
    'fi',
    'if [[ -e "$ADMIN_START_TEST_BACKEND_READY_FILE" ]]; then status="${ADMIN_START_TEST_BACKEND_STATUS:-401}"; else status=000; fi',
    'printf "probe-status|backend|%s\\n" "$status" >> "$ADMIN_START_TEST_LOG"',
    'if [[ "$write_out" == "true" ]]; then printf "%s" "$status"; fi',
    'if [[ "$status" == "000" ]]; then exit 7; fi',
    'if [[ "$fail_status" == "true" && "$status" -ge 400 ]]; then exit 22; fi',
    'exit 0',
    '',
  ].join('\n'), { mode: 0o755 })

  t.after(async () => {
    try {
      const content = await readFile(logPath, 'utf8')
      for (const match of content.matchAll(/(?:backend-wrapper|frontend-wrapper|curl)-pid\|(\d+)/g)) {
        try {
          killProcess(Number(match[1]), 'SIGTERM')
        } catch (error) {
          if (error.code !== 'ESRCH') throw error
        }
      }
    } catch (error) {
      if (error.code !== 'ENOENT') throw error
    }
    await new Promise(resolve => setTimeout(resolve, 50))
    await rm(fixtureRoot, { recursive: true, force: true, maxRetries: 5, retryDelay: 20 })
  })

  return {
    backendDir,
    backendReadyPath,
    binDir,
    fixtureScript,
    frontendDir,
    logPath,
    secretsPath,
    stateHome,
  }
}

function launcherEnv(fixture, extraEnv = {}) {
  return {
    ...env,
    ...REQUIRED_ENV,
    ADMIN_START_TEST_BACKEND_READY_FILE: fixture.backendReadyPath,
    ADMIN_START_TEST_LOG: fixture.logPath,
    PATH: `${fixture.binDir}:${env.PATH}`,
    XDG_STATE_HOME: fixture.stateHome,
    ...extraEnv,
  }
}

function startLauncher(fixture, extraEnv = {}) {
  const child = spawn(fixture.fixtureScript, [], {
    cwd: tmpdir(),
    env: launcherEnv(fixture, extraEnv),
  })
  let output = ''
  child.stdout.on('data', chunk => { output += chunk })
  child.stderr.on('data', chunk => { output += chunk })

  const exited = new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', (code, signal) => resolve({ code, signal, output: () => output }))
  })
  return { child, exited, output: () => output }
}

async function terminateLauncher(launcher, signal = 'SIGTERM') {
  if (launcher.child.exitCode === null) launcher.child.kill(signal)
  return launcher.exited
}

test('generates every missing local secret once with private permissions', async t => {
  const fixture = await createLauncherFixture(t)
  const emptySecrets = Object.fromEntries(Object.keys(REQUIRED_ENV).map(name => [name, '']))

  const first = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ...emptySecrets,
    ADMIN_START_TEST_CHILD_EXIT: '1',
  }))
  const firstFile = await readFile(fixture.secretsPath, 'utf8').catch(error => {
    if (error.code === 'ENOENT') assert.fail('launcher did not create the local secrets file')
    throw error
  })
  const storedEntries = firstFile.trim().split('\n').map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)])
  const storedNames = storedEntries.map(([name]) => name).sort()

  assert.equal(first.code, 1)
  assert.doesNotMatch(first.output, /is not set/)
  const log = await readFile(fixture.logPath, 'utf8')
  assert.match(log, /backend-start/)
  assert.match(log, new RegExp(`backend-umask\\|${EXPECTED_UMASK}`))
  assert.doesNotMatch(log, /runtime-start/)
  assert.deepEqual(storedNames, Object.keys(REQUIRED_ENV).sort())
  assert.equal(Object.fromEntries(storedEntries).AI_ENCRYPTOR_SALT, 'openflash-dev-salt')
  assert.equal((await stat(fixture.secretsPath)).mode & 0o777, 0o600)

  const second = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ...emptySecrets,
    ADMIN_START_TEST_CHILD_EXIT: '1',
  }))
  assert.equal(second.code, 1)
  assert.equal(await readFile(fixture.secretsPath, 'utf8'), firstFile)
})

test('does not apply runtime-only token validation', async t => {
  const fixture = await createLauncherFixture(t)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ADMIN_START_TEST_CHILD_EXIT: '1',
    OPENFLASH_AI_RUNTIME_ADMIN_TOKEN: 'explicit-shared-value',
    OPENFLASH_AI_RUNTIME_CORE_TOKEN: 'explicit-shared-value',
  }))

  assert.equal(result.code, 1)
  assert.doesNotMatch(result.output, /must use different values|explicit-shared-value/)
  const log = await readFile(fixture.logPath, 'utf8')
  assert.match(log, /backend-start/)
  assert.doesNotMatch(log, /runtime-start|\/health/)
})

test('starts without runtime and accepts expected unauthenticated 401', async t => {
  const fixture = await createLauncherFixture(t)
  const launcher = startLauncher(fixture)
  try {
    const backendWaiting = await waitForLog(fixture.logPath, content => content.includes('backend-start'))
    assert.match(backendWaiting, new RegExp(`backend-start\\|${fixture.backendDir}\\|spring-boot:run`))
    assert.doesNotMatch(backendWaiting, /runtime-start|\/health/)
    assert.doesNotMatch(backendWaiting, /frontend-start/)

    await writeFile(fixture.backendReadyPath, 'ready')
    const ready = await waitForLog(fixture.logPath, content => content.includes('frontend-start'))
    assert.match(ready, new RegExp(`frontend-start\\|${fixture.frontendDir}\\|run dev`))
    assert.match(ready, /probe-status\|backend\|401/)
    assert.doesNotMatch(ready, /runtime-start/)
    assert.doesNotMatch(ready, /forbidden-core-start|forbidden-postgresql-start|forbidden-mysql-start/)
  } finally {
    await terminateLauncher(launcher)
  }
})

test('accepts a normal admin 2xx response and starts admin frontend', async t => {
  const fixture = await createLauncherFixture(t)
  await writeFile(fixture.backendReadyPath, 'ready')
  const launcher = startLauncher(fixture, {
    ADMIN_START_TEST_BACKEND_STATUS: '204',
  })
  try {
    const log = await waitForLog(fixture.logPath, content => content.includes('frontend-start'))
    assert.match(log, /probe-status\|backend\|204/)
  } finally {
    await terminateLauncher(launcher)
  }
})

test('rejects admin redirects and errors until the backend child fails', async t => {
  for (const status of ['302', '403', '404', '500']) {
    const fixture = await createLauncherFixture(t)
    await writeFile(fixture.backendReadyPath, 'ready')
    const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
      ADMIN_START_TEST_BACKEND_EXIT_DELAY: '0.05',
      ADMIN_START_TEST_BACKEND_STATUS: status,
    }), 2500)
    const log = await readFile(fixture.logPath, 'utf8')

    assert.equal(result.code, 1, `admin status ${status} must not be ready`)
    assert.match(log, new RegExp(`probe-status\\|backend\\|${status}`))
    assert.match(log, /backend-fail/)
    assert.doesNotMatch(log, /frontend-start/)
    assert.doesNotMatch(log, /runtime-start|runtime-stop/)
  }
})

test('times out a stalled admin probe and detects child failure', async t => {
  const fixture = await createLauncherFixture(t)
  await writeFile(fixture.backendReadyPath, 'ready')
  const startedAt = Date.now()
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ADMIN_START_TEST_BACKEND_EXIT_DELAY: '0.05',
    ADMIN_START_TEST_STALL_PROBE: 'backend',
  }), 2500)
  const elapsedMs = Date.now() - startedAt
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 1)
  assert.ok(elapsedMs >= 800, `probe returned before max-time: ${elapsedMs}ms`)
  assert.match(log, /probe-timeout\|backend\|1/)
  assert.match(log, /backend-fail/)
  assert.doesNotMatch(log, /frontend-start/)
  assert.doesNotMatch(log, /runtime-start|runtime-stop/)
})

test('limits secrets to the backend that needs each value', async t => {
  const fixture = await createLauncherFixture(t)
  await writeFile(fixture.backendReadyPath, 'ready')
  const launcher = startLauncher(fixture)
  try {
    const log = await waitForLog(fixture.logPath, content => (
      content.includes('frontend-env|OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT|unset')
    ))
    assert.doesNotMatch(log, /runtime-start|runtime-env/)
    assert.match(log, /backend-env\|OPENFLASH_ADMIN_INTERNAL_TOKEN\|set/)
    assert.match(log, /backend-env\|OPENFLASH_AI_RUNTIME_ADMIN_TOKEN\|set/)
    assert.match(log, /backend-env\|OPENFLASH_AI_RUNTIME_CORE_TOKEN\|unset/)
    assert.match(log, /backend-env\|OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD\|unset/)
    for (const name of Object.keys(REQUIRED_ENV)) {
      assert.match(log, new RegExp(`frontend-env\\|${name}\\|unset`))
    }
    for (const secret of Object.values(REQUIRED_ENV)) {
      assert.doesNotMatch(launcher.output(), new RegExp(secret))
    }
  } finally {
    await terminateLauncher(launcher)
  }
})

test('stops admin children but leaves independent services and unrelated processes alive', async t => {
  const fixture = await createLauncherFixture(t)
  await writeFile(fixture.backendReadyPath, 'ready')
  const unrelated = spawn(execPath, ['-e', 'setInterval(() => {}, 1000)'])
  t.after(() => {
    if (unrelated.exitCode === null) unrelated.kill('SIGKILL')
  })
  const launcher = startLauncher(fixture)

  await waitForLog(fixture.logPath, content => content.includes('frontend-start'))
  await terminateLauncher(launcher)

  const log = await waitForLog(fixture.logPath, content => (
    content.includes('backend-stop') &&
    content.includes('frontend-stop')
  ))
  assert.doesNotMatch(log, /runtime-start|runtime-stop|runtime-grandchild-stop/)
  assert.equal(unrelated.exitCode, null)
})
