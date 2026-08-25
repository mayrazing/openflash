import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import test from 'node:test'

const moduleUrl = new URL('./card-id-migration.mjs', import.meta.url)

test('remaps every persisted practice-session card identity without changing FSRS data', async () => {
  assert.equal(existsSync(moduleUrl), true, 'card-id migration implementation is missing')
  const { remapPracticeSessionCardIds } = await import(moduleUrl)

  const oldCardId = 6_580_709_489
  const newCardId = 17
  const session = {
    queueItems: [{ cardId: oldCardId, itemKey: `${oldCardId}:a2b:base:0`, ordinal: 0 }],
    retryQueueItems: [{ cardId: oldCardId, itemKey: `${oldCardId}:b2a:retry:1` }],
    postRoundRetryCards: [{ cardId: oldCardId }],
    firstRatedIds: [`${oldCardId}:a2b`],
    masteredQueue: [{ id: oldCardId, deckId: 3, sideA: `${oldCardId}: literal text`, sideB: '请求' }],
    cardProgressState: {
      requiredDirectionsByCard: { [oldCardId]: ['a2b', 'b2a'] },
      completedDirectionsByCard: { [oldCardId]: ['a2b'] },
    },
    distributionState: { cards: [{ cardId: oldCardId, directions: { a2b: 'pending' } }] },
    pendingReplay: {
      sourceItemKey: `${oldCardId}:a2b:base:0`,
      target: { cardId: oldCardId, itemKey: `${oldCardId}:a2b:replay:0` },
    },
    history: [{
      cardId: oldCardId,
      queueItems: [{
        cardId: oldCardId,
        itemKey: `${oldCardId}:a2b:base:0`,
        card: {
          id: oldCardId,
          deckId: 3,
          sideA: 'request',
          sideB: '请求',
          directionProgresses: {
            a2b: { fsrs: { stability: 12.5, difficulty: 4.2, nextReviewDate: '2026-07-30' } },
          },
        },
      }],
      cardProgressState: {
        requiredDirectionsByCard: { [oldCardId]: ['a2b'] },
        completedDirectionsByCard: { [oldCardId]: [] },
      },
      distributionState: { cards: [{ cardId: oldCardId }] },
    }],
    stats: { reviewCountStat: oldCardId },
    metadata: { [oldCardId]: 'literal metadata key' },
    note: `do not rewrite embedded text ${oldCardId}`,
  }

  const remapped = remapPracticeSessionCardIds(session, new Map([[oldCardId, newCardId]]))

  assert.equal(remapped.queueItems[0].cardId, newCardId)
  assert.equal(remapped.queueItems[0].itemKey, `${newCardId}:a2b:base:0`)
  assert.equal(remapped.retryQueueItems[0].itemKey, `${newCardId}:b2a:retry:1`)
  assert.equal(remapped.postRoundRetryCards[0].cardId, newCardId)
  assert.deepEqual(remapped.firstRatedIds, [`${newCardId}:a2b`])
  assert.equal(remapped.masteredQueue[0].id, newCardId)
  assert.equal(remapped.masteredQueue[0].sideA, `${oldCardId}: literal text`)
  assert.deepEqual(Object.keys(remapped.cardProgressState.requiredDirectionsByCard), [String(newCardId)])
  assert.equal(remapped.distributionState.cards[0].cardId, newCardId)
  assert.equal(remapped.pendingReplay.sourceItemKey, `${newCardId}:a2b:base:0`)
  assert.equal(remapped.pendingReplay.target.itemKey, `${newCardId}:a2b:replay:0`)
  assert.equal(remapped.history[0].cardId, newCardId)
  assert.equal(remapped.history[0].queueItems[0].card.id, newCardId)
  assert.equal(remapped.history[0].queueItems[0].card.directionProgresses.a2b.fsrs.stability, 12.5)
  assert.equal(remapped.stats.reviewCountStat, oldCardId)
  assert.deepEqual(remapped.metadata, { [oldCardId]: 'literal metadata key' })
  assert.equal(remapped.note, `do not rewrite embedded text ${oldCardId}`)
  assert.equal(session.queueItems[0].cardId, oldCardId)
})

test('builds one guarded migration for the two preserved accounts and decks', async () => {
  assert.equal(existsSync(moduleUrl), true, 'card-id migration implementation is missing')
  const { buildCardIdMigrationSql } = await import(moduleUrl)
  const migration = {
    cards: [
      { oldId: 1_489, newId: 1, deckId: 3, userId: 1, updatedAt: '2026-07-20 10:00:00', cardFingerprint: 'a'.repeat(32) },
      { oldId: 1_499, newId: 2, deckId: 389, userId: 118, updatedAt: '2026-07-20 11:00:00', cardFingerprint: 'b'.repeat(32) },
    ],
    sessions: [{
      userId: 1,
      deckId: 3,
      data: JSON.stringify({ queueItems: [{ cardId: 1, itemKey: '1:a2b:base:0' }] }),
      sourceData: JSON.stringify({ queueItems: [{ cardId: 1_489, itemKey: '1489:a2b:base:0' }] }),
      updatedAt: '2026-07-21 19:21:40.123456',
    }],
    keepUsers: [1, 118],
    keepDecks: [3, 389],
    keepPairs: [{ userId: 1, deckId: 3 }, { userId: 118, deckId: 389 }],
    scopes: [
      {
        userId: 1,
        username: 'root',
        userDeleted: 0,
        userUpdatedAt: '2026-07-19 08:00:00.111111',
        deckId: 3,
        deckName: 'NewEastVocabulary',
        deckDeleted: 0,
        deckUpdatedAt: '2026-07-19 09:00:00.222222',
      },
      {
        userId: 118,
        username: 'Mara',
        userDeleted: 0,
        userUpdatedAt: '2026-07-19 10:00:00.333333',
        deckId: 389,
        deckName: 'Clippings',
        deckDeleted: 0,
        deckUpdatedAt: '2026-07-19 11:00:00.444444',
      },
    ],
  }
  const sql = buildCardIdMigrationSql({ ...migration, schema: 'openflash' })
  const relationNamedSchemaSql = buildCardIdMigrationSql({ ...migration, schema: 'pw_user' })

  assert.match(sql, /CREATE TEMPORARY TABLE card_id_migration_map/i)
  assert.match(sql, /START TRANSACTION/i)
  assert.match(sql, /LOCK TABLE[\s\S]*openflash\.pw_practice_session_store[\s\S]*IN ACCESS EXCLUSIVE MODE/i)
  assert.match(sql, /CREATE TEMPORARY TABLE card_id_migration_scope_snapshot/i)
  assert.match(sql, /card scope changed after migration snapshot/i)
  assert.match(sql, /UPDATE openflash\.pw_card_progress AS p[\s\S]*SET card_id = m\.temp_id[\s\S]*FROM card_id_migration_map AS m/i)
  assert.match(sql, /UPDATE openflash\.pw_card AS c[\s\S]*SET id = m\.new_id[\s\S]*updated_at = m\.card_updated_at[\s\S]*FROM card_id_migration_map AS m/i)
  assert.match(sql, /DELETE FROM openflash\.pw_user WHERE id NOT IN \(1, 118\)/i)
  assert.match(sql, /DELETE FROM openflash\.pw_deck WHERE id NOT IN \(3, 389\)/i)
  assert.match(sql, /DELETE FROM openflash\.SPRING_SESSION_ATTRIBUTES/i)
  assert.match(sql, /COMMIT/i)
  assert.match(sql, /ALTER SEQUENCE openflash\.pw_card_id_seq RESTART WITH 3/i)
  assert.match(sql, /ALTER TABLE openflash\.pw_practice_session_store DISABLE TRIGGER USER/i)
  assert.match(sql, /ALTER TABLE openflash\.pw_practice_session_store ENABLE TRIGGER USER/i)
  assert.match(sql, /2026-07-21 19:21:40\.123456/)
  assert.doesNotMatch(sql, /ENGINE=InnoDB|AUTO_INCREMENT|UPDATE\s+\w+\s+\w+\s+INNER JOIN/i)
  assert.match(relationNamedSchemaSql, /pw_user\.pw_deck/)
  assert.doesNotMatch(relationNamedSchemaSql, /pw_user\.pw_user\./)
})

test('strict session remap rejects every stale card identity', async () => {
  const { remapPracticeSessionCardIds } = await import(moduleUrl)

  assert.throws(
    () => remapPracticeSessionCardIds(
      { queueItems: [{ cardId: 999, itemKey: '999:a2b:base:0' }] },
      new Map([[1, 2]]),
      { strict: true },
    ),
    /unmapped card id 999/,
  )
  assert.throws(
    () => remapPracticeSessionCardIds(
      { firstRatedIds: ['999:a2b'] },
      new Map([[1, 2]]),
      { strict: true },
    ),
    /unmapped card id 999/,
  )
  assert.throws(
    () => remapPracticeSessionCardIds(
      { cardProgressState: { requiredDirectionsByCard: { 999: ['a2b'] } } },
      new Map([[1, 2]]),
      { strict: true },
    ),
    /unmapped card id 999/,
  )
})
