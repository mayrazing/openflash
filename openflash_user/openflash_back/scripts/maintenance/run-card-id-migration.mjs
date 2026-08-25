import { spawnSync } from 'node:child_process'

import {
  buildCardIdMigrationSql,
  remapPracticeSessionCardIds,
} from './card-id-migration.mjs'

function parseArgs(argv) {
  const options = {
    host: '127.0.0.1',
    port: '5432',
    user: 'postgres',
    database: 'openflash_db',
    schema: 'openflash',
    password: process.env.OPENFLASH_DB_PASSWORD,
    execute: false,
  }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--execute') {
      options.execute = true
      continue
    }
    if (!arg.startsWith('--')) throw new Error(`unknown argument: ${arg}`)
    const key = arg.slice(2)
    if (!['host', 'port', 'user', 'database', 'schema', 'password'].includes(key)) {
      throw new Error(`unknown option: ${arg}`)
    }
    const value = argv[index + 1]
    if (!value) throw new Error(`missing value for ${arg}`)
    options[key] = value
    index += 1
  }
  if (!options.password) throw new Error('database password is required')
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(options.schema)) {
    throw new Error('database schema must be a simple PostgreSQL identifier')
  }
  return options
}

function runPostgresql(options, sql) {
  const result = spawnSync('psql', [
    `--host=${options.host}`,
    `--port=${options.port}`,
    `--username=${options.user}`,
    `--dbname=${options.database}`,
    '--no-align',
    '--tuples-only',
    '--quiet',
    '--no-psqlrc',
    '--field-separator=\t',
    '--set=ON_ERROR_STOP=1',
  ], {
    input: sql,
    encoding: 'utf8',
    env: {
      ...process.env,
      PGPASSWORD: options.password,
      PGOPTIONS: `${process.env.PGOPTIONS ?? ''} -c search_path=${options.schema}`.trim(),
    },
    maxBuffer: 32 * 1024 * 1024,
  })
  if (result.error) {
    throw new Error(`failed to start psql: ${result.error.message}`)
  }
  if (result.status !== 0) {
    throw new Error(result.stderr?.trim() || `psql exited with status ${result.status}`)
  }
  return result.stdout.trim()
}

function rows(output) {
  if (!output) return []
  return output.split('\n').map(line => line.split('\t'))
}

function assertTargetDatabase(options) {
  const [target] = rows(runPostgresql(options, `
SELECT current_schema(), current_setting('is_superuser'), current_schemas(false)::text;
`))
  if (!target || target[0] !== options.schema) {
    throw new Error(`PostgreSQL schema does not exist or is not active: ${options.schema}`)
  }
  if (target[1] !== 'on') {
    throw new Error('card-id migration requires a PostgreSQL superuser')
  }
  if (target[2] !== `{${options.schema}}`) {
    throw new Error(`PostgreSQL search_path must contain only the target schema: ${options.schema}`)
  }
}

function loadPreservedScopes(options) {
  const output = runPostgresql(options, `
SELECT u.id, u.username, u.deleted, to_char(u.updated_at, 'YYYY-MM-DD HH24:MI:SS.US'),
       d.id, d.name, d.deleted, to_char(d.updated_at, 'YYYY-MM-DD HH24:MI:SS.US')
FROM ${options.schema}.pw_user u
INNER JOIN ${options.schema}.pw_deck d ON d.user_id = u.id
WHERE u.deleted = 0
  AND ((u.username = 'root' AND d.name = 'NewEastVocabulary' AND d.deleted = 0)
    OR (u.username = 'Mara' AND d.name = 'Clippings' AND d.deleted = 0))
ORDER BY u.username, d.name;
`)
  const scopes = rows(output).map(([
    userId, username, userDeleted, userUpdatedAt,
    deckId, deckName, deckDeleted, deckUpdatedAt,
  ]) => ({
    userId: Number(userId),
    username,
    userDeleted: Number(userDeleted),
    userUpdatedAt,
    deckId: Number(deckId),
    deckName,
    deckDeleted: Number(deckDeleted),
    deckUpdatedAt,
  }))
  const expected = new Set(['root/NewEastVocabulary', 'Mara/Clippings'])
  for (const scope of scopes) expected.delete(`${scope.username}/${scope.deckName}`)
  if (scopes.length !== 2 || expected.size !== 0) {
    throw new Error(`preserved account/deck lookup mismatch: ${[...expected].join(', ')}`)
  }
  return scopes
}

function loadCards(options, scopes) {
  const pairSql = scopes.map(scope => (
    `(u.id = ${scope.userId} AND d.id = ${scope.deckId})`
  )).join(' OR ')
  const orderSql = scopes.map((scope, index) => (
    `WHEN u.id = ${scope.userId} AND d.id = ${scope.deckId} THEN ${index}`
  )).join(' ')
  const output = runPostgresql(options, `
SELECT c.id, c.deck_id, u.id, to_char(c.updated_at, 'YYYY-MM-DD HH24:MI:SS.US'),
       md5(row_to_json(c)::text)
FROM ${options.schema}.pw_card c
INNER JOIN ${options.schema}.pw_deck d ON d.id = c.deck_id
INNER JOIN ${options.schema}.pw_user u ON u.id = d.user_id
WHERE c.deleted = 0 AND (${pairSql})
ORDER BY CASE ${orderSql} ELSE 999 END, c.created_at, c.id;
`)
  return rows(output).map(([oldId, deckId, userId, updatedAt, cardFingerprint], index) => ({
    oldId,
    newId: index + 1,
    deckId: Number(deckId),
    userId: Number(userId),
    updatedAt,
    cardFingerprint,
  }))
}

function loadRemappedSessions(options, scopes, cards) {
  const pairSql = scopes.map(scope => (
    `(user_id = ${scope.userId} AND deck_id = ${scope.deckId})`
  )).join(' OR ')
  const output = runPostgresql(options, `
SELECT user_id, deck_id, encode(convert_to(data, 'UTF8'), 'hex'), to_char(updated_at, 'YYYY-MM-DD HH24:MI:SS.US')
FROM ${options.schema}.pw_practice_session_store
WHERE ${pairSql}
ORDER BY user_id, deck_id;
`)
  const mapping = new Map(cards.map(card => [card.oldId, card.newId]))
  return rows(output).map(([userId, deckId, dataHex, updatedAt]) => {
    const sourceData = Buffer.from(dataHex, 'hex').toString('utf8')
    const session = JSON.parse(sourceData)
    const remapped = remapPracticeSessionCardIds(session, mapping, { strict: true })
    return {
      userId: Number(userId),
      deckId: Number(deckId),
      sourceData,
      data: JSON.stringify(remapped),
      updatedAt,
    }
  })
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  assertTargetDatabase(options)
  const scopes = loadPreservedScopes(options)
  const cards = loadCards(options, scopes)
  if (cards.length === 0) throw new Error('no cards found in preserved decks')
  const sessions = loadRemappedSessions(options, scopes, cards)
  const sql = buildCardIdMigrationSql({
    cards,
    sessions,
    keepUsers: scopes.map(scope => scope.userId),
    keepDecks: scopes.map(scope => scope.deckId),
    keepPairs: scopes.map(scope => ({ userId: scope.userId, deckId: scope.deckId })),
    scopes,
    schema: options.schema,
  })
  const summary = {
    execute: options.execute,
    cards: cards.length,
    sessions: sessions.length,
    nextCardId: cards.length + 1,
    schema: options.schema,
    scopes: scopes.map(scope => `${scope.username}/${scope.deckName}`),
    sqlBytes: Buffer.byteLength(sql),
  }
  if (options.execute) runPostgresql(options, sql)
  process.stdout.write(`${JSON.stringify(summary)}\n`)
}

try {
  main()
} catch (error) {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 1
}
