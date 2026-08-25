import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { env, execPath } from 'node:process'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { buildCardIdMigrationSql } from './card-id-migration.mjs'

const ENABLED = env.OPENFLASH_CARD_ID_POSTGRESQL_CONTRACT_TEST === 'true'
const HOST = env.OPENFLASH_POSTGRESQL_CONTRACT_HOST ?? '127.0.0.1'
const PORT = env.OPENFLASH_POSTGRESQL_CONTRACT_PORT ?? '5432'
const USER = env.OPENFLASH_POSTGRESQL_CONTRACT_USER ?? 'postgres'
const DATABASE = env.OPENFLASH_POSTGRESQL_CONTRACT_DATABASE ?? 'openflash_db'
const PASSWORD = env.OPENFLASH_POSTGRESQL_CONTRACT_PASSWORD ?? 'root'
const RUNNER = fileURLToPath(new URL('./run-card-id-migration.mjs', import.meta.url))

function runPsql(schema, sql, expectedStatus = 0) {
  const result = spawnSync('psql', [
    `--host=${HOST}`,
    `--port=${PORT}`,
    `--username=${USER}`,
    `--dbname=${DATABASE}`,
    '--no-align',
    '--tuples-only',
    '--quiet',
    '--no-psqlrc',
    '--set=ON_ERROR_STOP=1',
  ], {
    input: sql,
    encoding: 'utf8',
    env: {
      ...env,
      PGPASSWORD: PASSWORD,
      ...(schema ? { PGOPTIONS: `-c search_path=${schema}` } : {}),
    },
  })
  assert.equal(result.status, expectedStatus, result.stderr || result.stdout)
  return result.stdout.trim()
}

function createFixture(schema) {
  runPsql(null, `
CREATE SCHEMA ${schema};
CREATE TABLE ${schema}.pw_user (
  id BIGINT PRIMARY KEY, username TEXT NOT NULL, deleted SMALLINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE TABLE ${schema}.pw_deck (
  id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES ${schema}.pw_user(id),
  name TEXT NOT NULL, deleted SMALLINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE SEQUENCE ${schema}.pw_card_id_seq AS INTEGER;
CREATE TABLE ${schema}.pw_card (
  id INTEGER PRIMARY KEY DEFAULT nextval('${schema}.pw_card_id_seq'),
  deck_id BIGINT NOT NULL,
  side_a TEXT,
  side_b TEXT,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0,
  FOREIGN KEY (deck_id) REFERENCES ${schema}.pw_deck(id)
);
ALTER SEQUENCE ${schema}.pw_card_id_seq OWNED BY ${schema}.pw_card.id;
CREATE TABLE ${schema}.pw_card_progress (
  id BIGINT PRIMARY KEY, card_id INTEGER NOT NULL, user_id BIGINT NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  FOREIGN KEY (card_id) REFERENCES ${schema}.pw_card(id)
);
CREATE TABLE ${schema}.pw_card_media (
  id BIGINT PRIMARY KEY, card_id INTEGER NOT NULL REFERENCES ${schema}.pw_card(id)
);
CREATE TABLE ${schema}.pw_deck_ai_settings (deck_id BIGINT);
CREATE TABLE ${schema}.pw_deck_settings (deck_id BIGINT);
CREATE TABLE ${schema}.pw_mask_mode_deck_settings (deck_id BIGINT);
CREATE TABLE ${schema}.pw_tts_deck_settings (deck_id BIGINT);
CREATE TABLE ${schema}.pw_plugin_install (user_id BIGINT, deck_id BIGINT);
CREATE TABLE ${schema}.pw_practice_session_store (
  user_id BIGINT NOT NULL, deck_id BIGINT NOT NULL, data TEXT NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  PRIMARY KEY (user_id, deck_id)
);
CREATE FUNCTION ${schema}.force_new_updated_at() RETURNS trigger LANGUAGE plpgsql AS
\$\$ BEGIN NEW.updated_at = now(); RETURN NEW; END \$\$;
CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON ${schema}.pw_card
FOR EACH ROW EXECUTE FUNCTION ${schema}.force_new_updated_at();
CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON ${schema}.pw_card_progress
FOR EACH ROW EXECUTE FUNCTION ${schema}.force_new_updated_at();
CREATE TRIGGER on_update_current_timestamp BEFORE UPDATE ON ${schema}.pw_practice_session_store
FOR EACH ROW EXECUTE FUNCTION ${schema}.force_new_updated_at();
CREATE TABLE ${schema}.pw_user_ai_config (user_id BIGINT);
CREATE TABLE ${schema}.pw_user_settings (user_id BIGINT);
CREATE TABLE ${schema}.pw_async_task (id BIGINT);
CREATE TABLE ${schema}.pw_platform_ai_user_access (
  user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.pw_card_ai_cache (
  owner_user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.pw_user_active_ai_selection (
  user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.pw_user_feature_flag (
  user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.pw_user_platform_ai_preference (
  user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.pw_user_upload (
  user_id BIGINT REFERENCES ${schema}.pw_user(id) ON DELETE CASCADE
);
CREATE TABLE ${schema}.spring_session_attributes (session_primary_id TEXT);
CREATE TABLE ${schema}.spring_session (primary_id TEXT);
INSERT INTO ${schema}.pw_user VALUES
  (1, 'root', 0, '2026-08-19 10:00:00.111111'),
  (118, 'Mara', 0, '2026-08-19 11:00:00.222222'),
  (999, 'Removed', 0, '2026-08-19 14:00:00.555555');
INSERT INTO ${schema}.pw_deck VALUES
  (3, 1, 'NewEastVocabulary', 0, '2026-08-19 12:00:00.333333'),
  (389, 118, 'Clippings', 0, '2026-08-19 13:00:00.444444');
INSERT INTO ${schema}.pw_card VALUES
  (1001, 3, 'one', '一', '2026-08-20 10:00:00.111111', '2026-08-21 10:00:00.222222', 0),
  (1002, 389, 'two', '二', '2026-08-20 11:00:00.333333', '2026-08-21 11:00:00.444444', 0);
INSERT INTO ${schema}.pw_card_progress VALUES (9001, 1001, 1, '2026-08-22 10:00:00.555555');
INSERT INTO ${schema}.pw_card_media VALUES (9001, 1002);
INSERT INTO ${schema}.pw_practice_session_store VALUES
  (1, 3, '{ "queueItems": [ { "cardId": 1001, "itemKey": "1001:a2b:base:0" } ] }', '2026-08-23 10:00:00.666666');
INSERT INTO ${schema}.pw_platform_ai_user_access VALUES (999);
INSERT INTO ${schema}.pw_card_ai_cache VALUES (999);
INSERT INTO ${schema}.pw_user_active_ai_selection VALUES (999);
INSERT INTO ${schema}.pw_user_feature_flag VALUES (999);
INSERT INTO ${schema}.pw_user_platform_ai_preference VALUES (999);
INSERT INTO ${schema}.pw_user_upload VALUES (999);
`)
}

function loadCardFingerprints(schema) {
  return new Map(runPsql(schema, `
SELECT id, md5(row_to_json(c)::text) FROM pw_card AS c ORDER BY id;
`).split('\n').map(line => {
    const [id, fingerprint] = line.split('|')
    return [Number(id), fingerprint]
  }))
}

function dropFixture(schema) {
  assert.match(schema, /^openflash_card_contract_[a-f0-9]{12}$/)
  runPsql(null, `DROP SCHEMA IF EXISTS ${schema} CASCADE;`)
}

function newSchema() {
  return `openflash_card_contract_${randomUUID().replaceAll('-', '').slice(0, 12)}`
}

function runnerArgs(schema) {
  return [
    RUNNER,
    '--host', HOST,
    '--port', PORT,
    '--user', USER,
    '--database', DATABASE,
    '--schema', schema,
    '--execute',
  ]
}

test('runs the destructive card-id migration only inside the selected PostgreSQL schema', {
  skip: !ENABLED,
}, () => {
  const schema = newSchema()
  try {
    createFixture(schema)
    const result = spawnSync(execPath, runnerArgs(schema), {
      encoding: 'utf8',
      env: { ...env, OPENFLASH_DB_PASSWORD: PASSWORD },
    })
    assert.equal(result.status, 0, result.stderr || result.stdout)
    assert.deepEqual(JSON.parse(result.stdout), {
      execute: true,
      cards: 2,
      sessions: 1,
      nextCardId: 3,
      schema,
      scopes: ['Mara/Clippings', 'root/NewEastVocabulary'],
      sqlBytes: JSON.parse(result.stdout).sqlBytes,
    })

    const state = runPsql(schema, `
SELECT string_agg(id::text || ':' || deck_id::text, ',' ORDER BY id) FROM pw_card;
SELECT card_id || ':' || to_char(updated_at, 'YYYY-MM-DD HH24:MI:SS.US') FROM pw_card_progress;
SELECT card_id FROM pw_card_media;
SELECT data || ':' || to_char(updated_at, 'YYYY-MM-DD HH24:MI:SS.US') FROM pw_practice_session_store;
SELECT last_value || ':' || is_called FROM pw_card_id_seq;
SELECT bool_and(tgenabled = 'O')
FROM pg_trigger
WHERE tgrelid IN ('pw_card'::regclass, 'pw_card_progress'::regclass,
                  'pw_card_media'::regclass, 'pw_practice_session_store'::regclass);
SELECT (SELECT COUNT(*) FROM pw_platform_ai_user_access)
     + (SELECT COUNT(*) FROM pw_card_ai_cache)
     + (SELECT COUNT(*) FROM pw_user_active_ai_selection)
     + (SELECT COUNT(*) FROM pw_user_feature_flag)
     + (SELECT COUNT(*) FROM pw_user_platform_ai_preference)
     + (SELECT COUNT(*) FROM pw_user_upload);
`)
    assert.equal(state, [
      '1:389,2:3',
      '2:2026-08-22 10:00:00.555555',
      '1',
      '{"queueItems":[{"cardId":2,"itemKey":"2:a2b:base:0"}]}:2026-08-23 10:00:00.666666',
      '3:false',
      't',
      '0',
    ].join('\n'))
  } finally {
    dropFixture(schema)
  }
})

test('rejects a missing schema before reading or deleting data', { skip: !ENABLED }, () => {
  const schema = newSchema()
  const result = spawnSync(execPath, runnerArgs(schema), {
    encoding: 'utf8',
    env: { ...env, OPENFLASH_DB_PASSWORD: PASSWORD },
  })
  assert.equal(result.status, 1)
  assert.match(result.stderr, new RegExp(`schema does not exist or is not active: ${schema}`))
})

test('rejects a nonstandard trigger state without changing it', { skip: !ENABLED }, () => {
  const schema = newSchema()
  try {
    createFixture(schema)
    runPsql(schema, 'ALTER TABLE pw_practice_session_store DISABLE TRIGGER USER;')
    const result = spawnSync(execPath, runnerArgs(schema), {
      encoding: 'utf8',
      env: { ...env, OPENFLASH_DB_PASSWORD: PASSWORD },
    })
    assert.equal(result.status, 1)
    assert.match(result.stderr, /requires all affected triggers to be normally enabled/)
    assert.equal(runPsql(schema, "SELECT tgenabled FROM pg_trigger WHERE tgrelid = 'pw_practice_session_store'::regclass AND NOT tgisinternal;"), 'D')
    assert.equal(runPsql(schema, "SELECT string_agg(id::text, ',' ORDER BY id) FROM pw_card;"), '1001,1002')
  } finally {
    dropFixture(schema)
  }
})

test('rejects disabled user cascade triggers before deleting users', { skip: !ENABLED }, () => {
  const schema = newSchema()
  try {
    createFixture(schema)
    runPsql(schema, 'ALTER TABLE pw_user DISABLE TRIGGER ALL;')
    const result = spawnSync(execPath, runnerArgs(schema), {
      encoding: 'utf8',
      env: { ...env, OPENFLASH_DB_PASSWORD: PASSWORD },
    })
    assert.equal(result.status, 1)
    assert.match(result.stderr, /requires all affected triggers to be normally enabled/)
    assert.equal(runPsql(schema, 'SELECT COUNT(*) FROM pw_user WHERE id = 999;'), '1')
    assert.equal(runPsql(schema, `
SELECT (SELECT COUNT(*) FROM pw_platform_ai_user_access)
     + (SELECT COUNT(*) FROM pw_card_ai_cache)
     + (SELECT COUNT(*) FROM pw_user_active_ai_selection)
     + (SELECT COUNT(*) FROM pw_user_feature_flag)
     + (SELECT COUNT(*) FROM pw_user_platform_ai_preference)
     + (SELECT COUNT(*) FROM pw_user_upload);
`), '6')
  } finally {
    dropFixture(schema)
  }
})

test('rolls back card changes and sequence restart when the transaction fails', {
  skip: !ENABLED,
}, () => {
  const schema = newSchema()
  try {
    createFixture(schema)
    const fingerprints = loadCardFingerprints(schema)
    const sourceData = '{ "queueItems": [ { "cardId": 1001, "itemKey": "1001:a2b:base:0" } ] }'
    const sql = buildCardIdMigrationSql({
      cards: [
        { oldId: 1002, newId: 1, deckId: 389, userId: 118, updatedAt: '2026-08-21 11:00:00.444444', cardFingerprint: fingerprints.get(1002) },
        { oldId: 1001, newId: 2, deckId: 3, userId: 1, updatedAt: '2026-08-21 10:00:00.222222', cardFingerprint: fingerprints.get(1001) },
      ],
      sessions: [{
        userId: 1,
        deckId: 3,
        sourceData,
        data: '{"queueItems":[{"cardId":2,"itemKey":"2:a2b:base:0"}]}',
        updatedAt: '2026-08-23 10:00:00.666666',
      }],
      keepUsers: [118, 1],
      keepDecks: [389, 3],
      keepPairs: [{ userId: 118, deckId: 389 }, { userId: 1, deckId: 3 }],
      scopes: [
        {
          userId: 118,
          username: 'Mara',
          userDeleted: 0,
          userUpdatedAt: '2026-08-19 11:00:00.222222',
          deckId: 389,
          deckName: 'Clippings',
          deckDeleted: 0,
          deckUpdatedAt: '2026-08-19 13:00:00.444444',
        },
        {
          userId: 1,
          username: 'root',
          userDeleted: 0,
          userUpdatedAt: '2026-08-19 10:00:00.111111',
          deckId: 3,
          deckName: 'NewEastVocabulary',
          deckDeleted: 0,
          deckUpdatedAt: '2026-08-19 12:00:00.333333',
        },
      ],
      schema,
    }).replace('COMMIT;', 'SELECT 1 / 0;\nCOMMIT;')

    runPsql(schema, sql, 3)
    const state = runPsql(schema, `
SELECT string_agg(id::text, ',' ORDER BY id) FROM pw_card;
SELECT card_id FROM pw_card_progress;
SELECT data FROM pw_practice_session_store;
SELECT last_value || ':' || is_called FROM pw_card_id_seq;
SELECT bool_and(tgenabled = 'O') FROM pg_trigger
WHERE tgrelid IN ('pw_card'::regclass, 'pw_card_progress'::regclass,
                  'pw_card_media'::regclass, 'pw_practice_session_store'::regclass);
`)
    assert.equal(state, [
      '1001,1002',
      '1001',
      sourceData,
      '1:false',
      't',
    ].join('\n'))
  } finally {
    dropFixture(schema)
  }
})

test('rejects a renamed preserved deck before deleting anything', { skip: !ENABLED }, () => {
  const schema = newSchema()
  try {
    createFixture(schema)
    const fingerprints = loadCardFingerprints(schema)
    runPsql(schema, "UPDATE pw_deck SET name = 'Renamed' WHERE id = 3;")
    const sql = buildCardIdMigrationSql({
      cards: [
        { oldId: 1002, newId: 1, deckId: 389, userId: 118, updatedAt: '2026-08-21 11:00:00.444444', cardFingerprint: fingerprints.get(1002) },
        { oldId: 1001, newId: 2, deckId: 3, userId: 1, updatedAt: '2026-08-21 10:00:00.222222', cardFingerprint: fingerprints.get(1001) },
      ],
      sessions: [{
        userId: 1,
        deckId: 3,
        sourceData: '{ "queueItems": [ { "cardId": 1001, "itemKey": "1001:a2b:base:0" } ] }',
        data: '{"queueItems":[{"cardId":2,"itemKey":"2:a2b:base:0"}]}',
        updatedAt: '2026-08-23 10:00:00.666666',
      }],
      keepUsers: [118, 1],
      keepDecks: [389, 3],
      keepPairs: [{ userId: 118, deckId: 389 }, { userId: 1, deckId: 3 }],
      scopes: [
        {
          userId: 118,
          username: 'Mara',
          userDeleted: 0,
          userUpdatedAt: '2026-08-19 11:00:00.222222',
          deckId: 389,
          deckName: 'Clippings',
          deckDeleted: 0,
          deckUpdatedAt: '2026-08-19 13:00:00.444444',
        },
        {
          userId: 1,
          username: 'root',
          userDeleted: 0,
          userUpdatedAt: '2026-08-19 10:00:00.111111',
          deckId: 3,
          deckName: 'NewEastVocabulary',
          deckDeleted: 0,
          deckUpdatedAt: '2026-08-19 12:00:00.333333',
        },
      ],
      schema,
    })

    runPsql(schema, sql, 3)
    assert.equal(runPsql(schema, 'SELECT string_agg(id::text, \',\' ORDER BY id) FROM pw_card;'), '1001,1002')
    assert.equal(runPsql(schema, 'SELECT last_value || \':\' || is_called FROM pw_card_id_seq;'), '1:false')
  } finally {
    dropFixture(schema)
  }
})
