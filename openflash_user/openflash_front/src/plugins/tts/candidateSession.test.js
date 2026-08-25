import test from 'node:test'
import assert from 'node:assert/strict'
import { createCandidateSession } from './candidateSession.js'

test('confirm stores the exact candidate audio the user last heard and selected', async () => {
  const piperBlob = new Blob(['piper'], { type: 'audio/wav' })
  const replacements = []
  const session = createCandidateSession({
    text: 'difficult',
    previewText: async (_text, engine) => ({ engine, blob: piperBlob }),
    replaceCachedAudio: async (text, blob) => {
      replacements.push({ text, blob })
      return true
    },
  })

  await session.preview('piper')
  const confirmed = await session.confirm()

  assert.equal(confirmed, true)
  assert.deepEqual(replacements, [{ text: 'difficult', blob: piperBlob }])
  assert.equal(session.getState().selected.engine, 'piper')
})

test('slower obsolete preview cannot replace the latest selected candidate', async () => {
  const pending = new Map()
  const session = createCandidateSession({
    text: 'difficult',
    previewText: (_text, engine) => new Promise(resolve => pending.set(engine, resolve)),
    replaceCachedAudio: async () => true,
  })

  const cosyRequest = session.preview('cosyvoice3')
  const piperRequest = session.preview('piper')
  const piperBlob = new Blob(['piper'])
  pending.get('piper')({ engine: 'piper', blob: piperBlob })
  await piperRequest
  pending.get('cosyvoice3')({ engine: 'cosyvoice3', blob: new Blob(['cosy']) })
  await cosyRequest

  assert.equal(session.getState().selected.engine, 'piper')
  assert.equal(session.getState().selected.blob, piperBlob)
})

test('switching models retains and replays each generated candidate', async () => {
  const generated = []
  const replayed = []
  const blobs = {
    cosyvoice3: new Blob(['cosy']),
    piper: new Blob(['piper']),
  }
  const session = createCandidateSession({
    text: 'difficult',
    previewText: async (_text, engine, options = {}) => {
      if (options.candidateBlob) {
        replayed.push(options.candidateBlob)
        return { engine, blob: options.candidateBlob }
      }
      generated.push(engine)
      return { engine, blob: blobs[engine] }
    },
    replaceCachedAudio: async () => true,
  })

  await session.preview('cosyvoice3')
  await session.preview('piper')
  await session.preview('cosyvoice3')

  assert.deepEqual(generated, ['cosyvoice3', 'piper'])
  assert.deepEqual(replayed, [blobs.cosyvoice3])
  assert.equal(session.getState().selected.engine, 'cosyvoice3')
  assert.equal(session.getState().selected.blob, blobs.cosyvoice3)
})

test('confirm without a selected preview leaves cache unchanged', async () => {
  let replaceCount = 0
  const session = createCandidateSession({
    text: 'difficult',
    previewText: async () => null,
    replaceCachedAudio: async () => {
      replaceCount++
      return true
    },
  })

  assert.equal(await session.confirm(), false)
  assert.equal(replaceCount, 0)
})
