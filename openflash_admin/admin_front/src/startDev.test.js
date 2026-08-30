import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { env, execPath, kill as killProcess, umask as processUmask } from 'node:process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const SCRIPT_PATH = fileURLToPath(new URL('../../../openflash_user/start-dev.sh', import.meta.url))
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
  for (let attempt = 0; attempt < 180; attempt += 1) {
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

async function waitForProcessExit(pid) {
  for (let attempt = 0; attempt < 180; attempt += 1) {
    try {
      killProcess(pid, 0)
      const processStat = await readFile(`/proc/${pid}/stat`, 'utf8')
      if (/^\d+ \(.*\) Z /.test(processStat)) return
    } catch (error) {
      if (error.code === 'ESRCH' || error.code === 'ENOENT') return
      throw error
    }
    await new Promise(resolve => setTimeout(resolve, 25))
  }
  throw new Error(`Timed out waiting for process ${pid} to exit`)
}

async function createLauncherFixture(t) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'openflash-start-dev-'))
  const userRoot = join(fixtureRoot, 'openflash_user')
  const binDir = join(fixtureRoot, 'bin')
  const cosyvoice3Dir = join(userRoot, 'cosyvoice3_tts_service')
  const cosyvoice3RuntimeDir = join(fixtureRoot, 'cosyvoice3-runtime')
  const cosyvoice3PromptAudio = join(cosyvoice3RuntimeDir, 'asset', 'english_reference_okay.wav')
  const piperDir = join(userRoot, 'piper_tts_service')
  const piperDataDir = join(fixtureRoot, 'piper-data')
  const piperReadyPath = join(fixtureRoot, 'piper.ready')
  const backendDir = join(userRoot, 'openflash_back')
  const frontendDir = join(userRoot, 'openflash_front')
  const scriptsDir = join(fixtureRoot, 'scripts')
  const stateHome = join(fixtureRoot, 'state')
  const secretsPath = join(stateHome, 'openflash', 'dev-secrets.env')
  const logPath = join(fixtureRoot, 'processes.log')
  const cosyvoice3ReadyPath = join(fixtureRoot, 'cosyvoice3.ready')
  const backendReadyPath = join(fixtureRoot, 'backend.ready')
  const lanIpPath = join(fixtureRoot, 'lan-ip')
  const fixtureScript = join(userRoot, 'start-dev.sh')

  await Promise.all([
    mkdir(binDir),
    mkdir(cosyvoice3Dir, { recursive: true }),
    mkdir(join(cosyvoice3RuntimeDir, 'asset'), { recursive: true }),
    mkdir(join(cosyvoice3RuntimeDir, 'cosyvoice', 'cli'), { recursive: true }),
    mkdir(join(cosyvoice3RuntimeDir, 'pretrained_models', 'Fun-CosyVoice3-0.5B'), { recursive: true }),
    mkdir(piperDir, { recursive: true }),
    mkdir(piperDataDir, { recursive: true }),
    mkdir(backendDir, { recursive: true }),
    mkdir(frontendDir, { recursive: true }),
    mkdir(scriptsDir),
  ])
  await Promise.all([
    writeFile(join(cosyvoice3RuntimeDir, 'cosyvoice', 'cli', 'cosyvoice.py'), 'fixture'),
    writeFile(join(cosyvoice3RuntimeDir, 'pretrained_models', 'Fun-CosyVoice3-0.5B', 'llm.rl.pt'), 'fixture'),
    writeFile(cosyvoice3PromptAudio, 'fixture'),
    writeFile(join(piperDataDir, 'en_US-libritts_r-medium.onnx'), 'fixture'),
    writeFile(join(piperDataDir, 'en_US-libritts_r-medium.onnx.json'), 'fixture'),
    writeFile(lanIpPath, '192.168.3.28'),
  ])
  const [cosyvoice3Port, piperPort, backendPort, frontendPort] = await takeAvailablePorts(4)
  const script = await readFile(SCRIPT_PATH, 'utf8')
  await writeFile(fixtureScript, script
    .replaceAll('8888', String(cosyvoice3Port))
    .replaceAll('8889', String(piperPort))
    .replaceAll('8080', String(backendPort))
    .replaceAll('5173', String(frontendPort)), { mode: 0o755 })
  try {
    await writeFile(join(scriptsDir, 'dev-secrets.sh'), await readFile(DEV_SECRETS_HELPER_PATH, 'utf8'), { mode: 0o755 })
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }

  await writeFile(join(binDir, 'cosyvoice3-python'), [
    '#!/usr/bin/env bash',
    'if [[ "$1" == "-c" ]]; then exit 0; fi',
    'printf "cosyvoice3-wrapper-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
    'printf "cosyvoice3-start\\n" >> "$START_DEV_TEST_LOG"',
    'printf "cosyvoice3-umask|%s\\n" "$(umask)" >> "$START_DEV_TEST_LOG"',
    'if [[ "${START_DEV_TEST_CHILD_EXIT:-0}" == "1" ]]; then exit 7; fi',
    'if [[ -n "${START_DEV_TEST_COSYVOICE3_EXIT_DELAY:-}" ]]; then',
    '  sleep "$START_DEV_TEST_COSYVOICE3_EXIT_DELAY"',
    '  printf "cosyvoice3-fail\\n" >> "$START_DEV_TEST_LOG"',
    '  exit 7',
    'fi',
    'trap \'printf "cosyvoice3-stop\\n" >> "$START_DEV_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'piper-python'), [
    '#!/usr/bin/env bash',
    'if [[ "$1" == "-c" ]]; then exit 0; fi',
    'printf "piper-wrapper-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
    'printf "piper-start|%s|%s\\n" "$PWD" "$*" >> "$START_DEV_TEST_LOG"',
    'if [[ -n "${START_DEV_TEST_PIPER_EXIT_DELAY:-}" ]]; then',
    '  sleep "$START_DEV_TEST_PIPER_EXIT_DELAY"',
    '  printf "piper-fail\\n" >> "$START_DEV_TEST_LOG"',
    '  exit 7',
    'fi',
    'trap \'printf "piper-stop\\n" >> "$START_DEV_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(backendDir, 'gradlew'), [
    '#!/usr/bin/env bash',
    'printf "backend-wrapper-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
    'printf "backend-start|%s|%s\\n" "$PWD" "$*" >> "$START_DEV_TEST_LOG"',
    'if [[ -n "${START_DEV_TEST_BACKEND_EXIT_DELAY:-}" ]]; then',
    '  sleep "$START_DEV_TEST_BACKEND_EXIT_DELAY"',
    '  printf "backend-fail\\n" >> "$START_DEV_TEST_LOG"',
    '  exit 7',
    'fi',
    'for name in OPENFLASH_ADMIN_INTERNAL_TOKEN OPENFLASH_AI_RUNTIME_ADMIN_TOKEN OPENFLASH_AI_RUNTIME_CORE_TOKEN OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT AI_ENCRYPTOR_PASSWORD AI_ENCRYPTOR_SALT; do',
    '  if [[ -n "${!name:-}" ]]; then printf "backend-env|%s|set\\n" "$name" >> "$START_DEV_TEST_LOG"; else printf "backend-env|%s|unset\\n" "$name" >> "$START_DEV_TEST_LOG"; fi',
    'done',
    'trap \'printf "backend-stop\\n" >> "$START_DEV_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'npm'), [
    '#!/usr/bin/env bash',
    'printf "frontend-wrapper-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
    'printf "frontend-start|%s|%s\\n" "$PWD" "$*" >> "$START_DEV_TEST_LOG"',
    'for name in OPENFLASH_ADMIN_INTERNAL_TOKEN OPENFLASH_AI_RUNTIME_ADMIN_TOKEN OPENFLASH_AI_RUNTIME_CORE_TOKEN OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT AI_ENCRYPTOR_PASSWORD AI_ENCRYPTOR_SALT; do',
    '  if [[ -n "${!name:-}" ]]; then printf "frontend-env|%s|set\\n" "$name" >> "$START_DEV_TEST_LOG"; else printf "frontend-env|%s|unset\\n" "$name" >> "$START_DEV_TEST_LOG"; fi',
    'done',
    'trap \'printf "frontend-stop\\n" >> "$START_DEV_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'ip'), [
    '#!/usr/bin/env bash',
    'lan_ip="$(cat "$START_DEV_TEST_LAN_IP_FILE")"',
    'printf "multicast 224.0.0.251 dev fixture0 src %s uid 1000\\n" "$lan_ip"',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'avahi-publish-address'), [
    '#!/usr/bin/env bash',
    'printf "mdns-wrapper-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
    'printf "mdns-start|%s\\n" "$*" >> "$START_DEV_TEST_LOG"',
    'trap \'printf "mdns-stop\\n" >> "$START_DEV_TEST_LOG"; exit 0\' TERM INT',
    'while true; do sleep 0.05; done',
    '',
  ].join('\n'), { mode: 0o755 })

  await writeFile(join(binDir, 'curl'), [
    '#!/usr/bin/env bash',
    'printf "probe|%s\\n" "$*" >> "$START_DEV_TEST_LOG"',
    'printf "curl-pid|%s\\n" "$$" >> "$START_DEV_TEST_LOG"',
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
    'url="${!#}"',
    'target=unknown',
    'if [[ "$url" == *":$START_DEV_TEST_COSYVOICE3_PORT/health"* ]]; then target=cosyvoice3; fi',
    'if [[ "$url" == *":$START_DEV_TEST_PIPER_PORT/health"* ]]; then target=piper; fi',
    'if [[ "$url" == *":$START_DEV_TEST_BACKEND_PORT"* ]]; then target=core; fi',
    'if [[ "${START_DEV_TEST_STALL_PROBE:-}" == "$target" ]]; then',
    '  if [[ -z "$max_time" ]]; then while true; do sleep 1; done; fi',
    '  sleep "$max_time"',
    '  printf "probe-timeout|%s|%s\\n" "$target" "$max_time" >> "$START_DEV_TEST_LOG"',
    '  if [[ "$write_out" == "true" ]]; then printf "200"; fi',
    '  exit 28',
    'fi',
    'if [[ "$target" == "cosyvoice3" ]]; then',
    '  if [[ -e "$START_DEV_TEST_COSYVOICE3_READY_FILE" ]]; then status="${START_DEV_TEST_COSYVOICE3_STATUS:-200}"; else status=000; fi',
    'elif [[ "$target" == "piper" ]]; then',
    '  if [[ -e "$START_DEV_TEST_PIPER_READY_FILE" ]]; then status="${START_DEV_TEST_PIPER_STATUS:-200}"; else status=000; fi',
    'elif [[ "$target" == "core" ]]; then',
    '  if [[ -e "$START_DEV_TEST_BACKEND_READY_FILE" ]]; then status="${START_DEV_TEST_BACKEND_STATUS:-200}"; else status=000; fi',
    'else',
    '  exit 9',
    'fi',
    'printf "probe-status|%s|%s\\n" "$target" "$status" >> "$START_DEV_TEST_LOG"',
    'if [[ "$write_out" == "true" ]]; then printf "%s" "$status"; fi',
    'if [[ "$status" == "000" ]]; then exit 7; fi',
    'if [[ "$fail_status" == "true" && "$status" -ge 400 ]]; then exit 22; fi',
    'exit 0',
    '',
  ].join('\n'), { mode: 0o755 })

  t.after(async () => {
    try {
      const content = await readFile(logPath, 'utf8')
      for (const match of content.matchAll(/(?:cosyvoice3-wrapper|piper-wrapper|backend-wrapper|frontend-wrapper|mdns-wrapper|curl)-pid\|(\d+)/g)) {
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
    backendPort,
    backendReadyPath,
    binDir,
    fixtureScript,
    frontendDir,
    cosyvoice3Port,
    cosyvoice3PromptAudio,
    cosyvoice3ReadyPath,
    cosyvoice3RuntimeDir,
    piperDataDir,
    piperDir,
    piperPort,
    piperReadyPath,
    logPath,
    lanIpPath,
    secretsPath,
    stateHome,
  }
}

function launcherEnv(fixture, extraEnv = {}) {
  return {
    ...env,
    ...REQUIRED_ENV,
    COSYVOICE3_PYTHON: join(fixture.binDir, 'cosyvoice3-python'),
    COSYVOICE3_RUNTIME_DIR: fixture.cosyvoice3RuntimeDir,
    PIPER_DATA_DIR: fixture.piperDataDir,
    PIPER_PYTHON: join(fixture.binDir, 'piper-python'),
    PATH: `${fixture.binDir}:${env.PATH}`,
    START_DEV_TEST_BACKEND_PORT: String(fixture.backendPort),
    START_DEV_TEST_BACKEND_READY_FILE: fixture.backendReadyPath,
    START_DEV_TEST_COSYVOICE3_PORT: String(fixture.cosyvoice3Port),
    START_DEV_TEST_COSYVOICE3_READY_FILE: fixture.cosyvoice3ReadyPath,
    START_DEV_TEST_PIPER_PORT: String(fixture.piperPort),
    START_DEV_TEST_PIPER_READY_FILE: fixture.piperReadyPath,
    START_DEV_TEST_LOG: fixture.logPath,
    START_DEV_TEST_LAN_IP_FILE: fixture.lanIpPath,
    XDG_STATE_HOME: fixture.stateHome,
    ...extraEnv,
  }
}

function startLauncher(fixture, extraEnv = {}) {
  const child = spawn(fixture.fixtureScript, [], { env: launcherEnv(fixture, extraEnv) })
  let output = ''
  child.stdout.on('data', chunk => { output += chunk })
  child.stderr.on('data', chunk => { output += chunk })
  const exited = new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', (code, signal) => resolve({ code, signal, output: () => output }))
  })
  return { child, exited, output: () => output }
}

async function terminateLauncher(launcher) {
  if (launcher.child.exitCode === null) launcher.child.kill('SIGTERM')
  return launcher.exited
}

test('generates every missing local secret once with private permissions', async t => {
  const fixture = await createLauncherFixture(t)
  const emptySecrets = Object.fromEntries(Object.keys(REQUIRED_ENV).map(name => [name, '']))

  const first = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ...emptySecrets,
    START_DEV_TEST_CHILD_EXIT: '1',
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
  assert.match(log, /cosyvoice3-start/)
  assert.match(log, new RegExp(`cosyvoice3-umask\\|${EXPECTED_UMASK}`))
  assert.deepEqual(storedNames, Object.keys(REQUIRED_ENV).sort())
  assert.equal(Object.fromEntries(storedEntries).AI_ENCRYPTOR_SALT, 'openflash-dev-salt')
  assert.equal((await stat(fixture.secretsPath)).mode & 0o777, 0o600)

  const second = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    ...emptySecrets,
    START_DEV_TEST_CHILD_EXIT: '1',
  }))
  assert.equal(second.code, 1)
  assert.equal(await readFile(fixture.secretsPath, 'utf8'), firstFile)
})

test('does not apply admin-only runtime token validation', async t => {
  const fixture = await createLauncherFixture(t)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    OPENFLASH_AI_RUNTIME_ADMIN_TOKEN: 'explicit-shared-value',
    OPENFLASH_AI_RUNTIME_CORE_TOKEN: 'explicit-shared-value',
    START_DEV_TEST_CHILD_EXIT: '1',
  }))

  assert.equal(result.code, 1)
  assert.doesNotMatch(result.output, /must use different values|explicit-shared-value/)
  const log = await readFile(fixture.logPath, 'utf8')
  assert.match(log, /cosyvoice3-start/)
  assert.doesNotMatch(log, /runtime-start|127\.0\.0\.1:8082/)
})

test('starts without probing the independent runtime', async t => {
  const fixture = await createLauncherFixture(t)
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    START_DEV_TEST_CHILD_EXIT: '1',
  }))

  assert.equal(result.code, 1)
  const log = await readFile(fixture.logPath, 'utf8')
  assert.match(log, /cosyvoice3-start/)
  assert.doesNotMatch(log, /runtime-start|127\.0\.0\.1:8082/)
})

test('starts CosyVoice3, Piper, core, and frontend in dependency order without starting runtime', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
    writeFile(fixture.backendReadyPath, 'ready'),
  ])
  const launcher = startLauncher(fixture)
  try {
    const ready = await waitForLog(fixture.logPath, content => content.includes('mdns-start'))
    assert.ok(ready.indexOf('cosyvoice3-start') < ready.indexOf('piper-start'))
    assert.ok(ready.indexOf('piper-start') < ready.indexOf('backend-start'))
    assert.ok(ready.indexOf('backend-start') < ready.indexOf('frontend-start'))
    assert.match(ready, new RegExp(`piper-start\\|${fixture.piperDir}\\|-m uvicorn app:app --host 127\\.0\\.0\\.1 --port ${fixture.piperPort}`))
    assert.match(ready, new RegExp(`backend-start\\|${fixture.backendDir}\\|--console=plain clean bootRun`))
    assert.match(ready, new RegExp(`frontend-start\\|${fixture.frontendDir}\\|run dev -- --host`))
    assert.match(ready, /mdns-start\|--no-reverse openflash\.local 192\.168\.3\.28/)
    assert.doesNotMatch(ready, /runtime-start/)
  } finally {
    await terminateLauncher(launcher)
  }
})

test('keeps openflash.local while the LAN IPv4 address changes', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
    writeFile(fixture.backendReadyPath, 'ready'),
  ])
  const launcher = startLauncher(fixture)
  try {
    await waitForLog(fixture.logPath, content => (
      content.includes('mdns-start|--no-reverse openflash.local 192.168.3.28')
    ))
    await writeFile(fixture.lanIpPath, '192.168.3.55')
    const updated = await waitForLog(fixture.logPath, content => (
      content.includes('mdns-start|--no-reverse openflash.local 192.168.3.55')
    ))
    assert.match(updated, /mdns-stop/)
  } finally {
    await terminateLauncher(launcher)
  }
})

test('CosyVoice3 readiness rejects non-2xx and exits after its child fails without starting runtime', async t => {
  const fixture = await createLauncherFixture(t)
  await writeFile(fixture.cosyvoice3ReadyPath, 'ready')
  const startedAt = Date.now()
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    START_DEV_TEST_COSYVOICE3_EXIT_DELAY: '0.05',
    START_DEV_TEST_COSYVOICE3_STATUS: '503',
  }), 2500)
  const elapsedMs = Date.now() - startedAt
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 1)
  assert.ok(elapsedMs < 2000, `launcher did not exit after CosyVoice3 failed: ${elapsedMs}ms`)
  assert.match(log, /probe\|--silent --output \/dev\/null --connect-timeout 1 --max-time 1 --write-out %\{http_code\}/)
  assert.match(log, /probe-status\|cosyvoice3\|503/)
  assert.match(log, /cosyvoice3-fail/)
  assert.doesNotMatch(log, /runtime-start|backend-start|frontend-start/)
  const cosyvoice3Pid = Number(log.match(/cosyvoice3-wrapper-pid\|(\d+)/)?.[1])
  assert.throws(() => killProcess(cosyvoice3Pid, 0), error => error.code === 'ESRCH')
})

test('CosyVoice3 stalled partial 200 times out, detects child failure, and cleans up without starting runtime', async t => {
  const fixture = await createLauncherFixture(t)
  const startedAt = Date.now()
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    START_DEV_TEST_COSYVOICE3_EXIT_DELAY: '0.05',
    START_DEV_TEST_STALL_PROBE: 'cosyvoice3',
  }), 2500)
  const elapsedMs = Date.now() - startedAt
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 1)
  assert.ok(elapsedMs >= 800, `probe returned before max-time: ${elapsedMs}ms`)
  assert.ok(elapsedMs < 2000, `launcher did not exit after CosyVoice3 failed: ${elapsedMs}ms`)
  assert.match(log, /probe-timeout\|cosyvoice3\|1/)
  assert.match(log, /cosyvoice3-fail/)
  assert.doesNotMatch(log, /runtime-start|backend-start|frontend-start/)
  const cosyvoice3Pid = Number(log.match(/cosyvoice3-wrapper-pid\|(\d+)/)?.[1])
  assert.throws(() => killProcess(cosyvoice3Pid, 0), error => error.code === 'ESRCH')
})

test('Piper readiness rejects non-2xx and cleans the earlier CosyVoice3 child', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
  ])
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    START_DEV_TEST_PIPER_EXIT_DELAY: '0.05',
    START_DEV_TEST_PIPER_STATUS: '503',
  }), 2500)
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 1)
  assert.match(log, /probe-status\|piper\|503/)
  assert.match(log, /piper-fail/)
  assert.doesNotMatch(log, /backend-start|frontend-start/)
  const cosyvoice3Pid = Number(log.match(/cosyvoice3-wrapper-pid\|(\d+)/)?.[1])
  await waitForProcessExit(cosyvoice3Pid)
})

test('core readiness preserves its existing any-HTTP-response contract', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
    writeFile(fixture.backendReadyPath, 'ready'),
  ])
  const launcher = startLauncher(fixture, {
    START_DEV_TEST_BACKEND_STATUS: '500',
  })
  try {
    const log = await waitForLog(fixture.logPath, content => content.includes('frontend-start'))
    assert.match(log, /probe-status\|core\|500/)
  } finally {
    await terminateLauncher(launcher)
  }
})

test('times out a stalled core probe, detects core failure, and cleans earlier children', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
  ])
  const result = await runToExit(fixture.fixtureScript, launcherEnv(fixture, {
    START_DEV_TEST_BACKEND_EXIT_DELAY: '0.05',
    START_DEV_TEST_STALL_PROBE: 'core',
  }), 2500)
  const log = await readFile(fixture.logPath, 'utf8')

  assert.equal(result.code, 1)
  assert.match(log, /probe-timeout\|core\|1/)
  assert.match(log, /backend-fail/)
  assert.doesNotMatch(log, /frontend-start/)
  assert.doesNotMatch(log, /runtime-stop/)
  const cosyvoice3Pid = Number(log.match(/cosyvoice3-wrapper-pid\|(\d+)/)?.[1])
  await waitForProcessExit(cosyvoice3Pid)
})

test('passes each secret only to the backend that needs it', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
    writeFile(fixture.backendReadyPath, 'ready'),
  ])
  const launcher = startLauncher(fixture)
  try {
    const log = await waitForLog(fixture.logPath, content => (
      content.includes('frontend-env|AI_ENCRYPTOR_PASSWORD|unset')
    ))
    assert.doesNotMatch(log, /runtime-start|runtime-env/)
    assert.match(log, /backend-env\|OPENFLASH_ADMIN_INTERNAL_TOKEN\|set/)
    assert.match(log, /backend-env\|OPENFLASH_AI_RUNTIME_CORE_TOKEN\|set/)
    assert.match(log, /backend-env\|AI_ENCRYPTOR_PASSWORD\|set/)
    assert.match(log, /backend-env\|AI_ENCRYPTOR_SALT\|set/)
    assert.match(log, /backend-env\|OPENFLASH_AI_RUNTIME_ADMIN_TOKEN\|unset/)
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

test('stops owned children but leaves the shared runtime and unrelated processes alone', async t => {
  const fixture = await createLauncherFixture(t)
  await Promise.all([
    writeFile(fixture.cosyvoice3ReadyPath, 'ready'),
    writeFile(fixture.piperReadyPath, 'ready'),
    writeFile(fixture.backendReadyPath, 'ready'),
  ])
  const unrelated = spawn(execPath, ['-e', 'setInterval(() => {}, 1000)'])
  t.after(() => {
    if (unrelated.exitCode === null) unrelated.kill('SIGKILL')
  })
  const launcher = startLauncher(fixture)

  await waitForLog(fixture.logPath, content => content.includes('mdns-start'))
  await terminateLauncher(launcher)

  const log = await readFile(fixture.logPath, 'utf8')
  assert.equal(unrelated.exitCode, null)
  const cosyvoice3Pid = Number(log.match(/cosyvoice3-wrapper-pid\|(\d+)/)?.[1])
  const piperPid = Number(log.match(/piper-wrapper-pid\|(\d+)/)?.[1])
  const backendPid = Number(log.match(/backend-wrapper-pid\|(\d+)/)?.[1])
  const frontendPid = Number(log.match(/frontend-wrapper-pid\|(\d+)/)?.[1])
  const mdnsPid = Number(log.match(/mdns-wrapper-pid\|(\d+)/)?.[1])
  await Promise.all([
    waitForProcessExit(cosyvoice3Pid),
    waitForProcessExit(piperPid),
    waitForProcessExit(backendPid),
    waitForProcessExit(frontendPid),
    waitForProcessExit(mdnsPid),
  ])
  assert.match(log, /mdns-stop/)
  assert.doesNotMatch(log, /runtime-stop|runtime-grandchild-stop/)
})
