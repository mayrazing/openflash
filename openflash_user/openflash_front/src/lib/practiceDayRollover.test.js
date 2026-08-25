import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import {
  claimPracticeDayRollover,
  hasPracticeDayChanged,
  millisecondsUntilNextLocalDay,
} from './practiceDayRollover.js'

const __dirname = dirname(fileURLToPath(import.meta.url))

test('practice day changes only when session date differs from local today', () => {
  assert.equal(hasPracticeDayChanged('2026-07-11', '2026-07-11'), false)
  assert.equal(hasPracticeDayChanged('2026-07-10', '2026-07-11'), true)
})

test('practice rollover lock can only be claimed once after day changes', () => {
  const lock = { current: false }

  assert.equal(claimPracticeDayRollover(lock, '2026-07-10', '2026-07-11'), true)
  assert.equal(claimPracticeDayRollover(lock, '2026-07-10', '2026-07-11'), false)
})

test('next local day delay reaches the following midnight', () => {
  const now = new Date(2026, 6, 11, 23, 59, 30, 0)

  assert.equal(millisecondsUntilNextLocalDay(now), 30_000)
})

test('practice rollover hook serializes cleanup and always replaces route after delete failure', () => {
  const source = readFileSync(join(__dirname, '..', 'hooks', 'usePracticeDayRollover.js'), 'utf8')

  assert.match(source, /visibilitychange/)
  assert.match(source, /millisecondsUntilNextLocalDay/)
  assert.match(source, /invalidateQueuedPracticeSessionSaves\(\)/)
  assert.match(source, /sessionMutationChainRef\.current/)
  assert.match(source, /await sessionSaveChainRef\.current\.catch/)
  assert.match(source, /await clearPracticeSession\(id\)/)
  assert.match(source, /appError\(error\?\.code \?\? 50000, t\('practice\.dayRolloverClearError'\), error\)/)
  assert.match(source, /finally\s*\{[\s\S]*navigate\(`\/deck\/\$\{id\}`,[\s\S]*replace:\s*true/)
})

test('practice mutations stop once day rollover owns the session', () => {
  const source = readFileSync(join(__dirname, '..', 'hooks', 'usePracticePersistence.js'), 'utf8')

  assert.match(source, /practiceDayRolloverRef\.current[\s\S]*Promise\.resolve\(\)/)
})
