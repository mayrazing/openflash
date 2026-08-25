import assert from 'node:assert'
import test from 'node:test'
import fs from 'node:fs'
import vm from 'node:vm'

function loadSave(runtime) {
  class FileReader {
    readAsDataURL(file) {
      this.result = `data:${file.type || 'image/jpeg'};base64,${Buffer.from(file.name).toString('base64')}`
      this.onload()
    }
  }
  const context = vm.createContext({
    console,
    globalThis: {},
    chrome: { runtime },
    FileReader,
    Buffer,
    OpenFlashManualCardEditor: {
      hasAnyContent: (state) => Boolean(state.a.text || state.a.images.length || state.b.text || state.b.images.length),
      buildPayload: (state, uploadedByImageId) => ({
        sideA: state.a.text,
        sideAImage: state.a.imageOrder.map((id) => uploadedByImageId[id]).filter(Boolean),
        sideB: state.b.text,
        sideBImage: state.b.imageOrder.map((id) => uploadedByImageId[id]).filter(Boolean),
      }),
    },
  })
  context.globalThis = context
  vm.runInContext(fs.readFileSync(new URL('../src/manualCardImageProcessor.js', import.meta.url), 'utf8'), context)
  vm.runInContext(fs.readFileSync(new URL('../src/manualCardSave.js', import.meta.url), 'utf8'), context)
  return context.OpenFlashManualCardSave
}

test('manual card save sends only non-uploaded images and caches returned urls', async () => {
  const messages = []
  const save = loadSave({
    sendMessage: async (message) => {
      messages.push(message)
      if (message.type === 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES') {
        return { ok: true, uploadedByImageId: { a1: '/uploads/a.jpg' } }
      }
      return { ok: true, card: { id: 1 } }
    },
  })
  const saver = save.createSaver()
  const state = {
    a: { text: 'front', images: [{ id: 'a1', file: { name: 'a.jpg' } }], imageOrder: ['a1'] },
    b: { text: '', images: [], imageOrder: [] },
  }

  await saver.save({ baseUrl: 'http://localhost:5173', deckId: '7', state })
  await saver.save({ baseUrl: 'http://localhost:5173', deckId: '7', state })

  assert.equal(messages.length, 3)
  assert.equal(messages[0].sideAImages[0].dataUrl, 'data:image/jpeg;base64,YS5qcGc=')
  assert.equal(messages[1].type, 'OPENFLASH_MANUAL_CARD_CREATE')
  assert.deepEqual(messages[1].payload.sideAImage, ['/uploads/a.jpg'])
  assert.equal(messages[2].type, 'OPENFLASH_MANUAL_CARD_CREATE')
  assert.deepEqual(saver.getUploadedByImageId(), { a1: '/uploads/a.jpg' })
})

test('manual card save rejects empty content before runtime message', async () => {
  const save = loadSave({
    sendMessage: async () => {
      throw new Error('must not send')
    },
  })
  const saver = save.createSaver()

  await assert.rejects(
    saver.save({ baseUrl: 'http://localhost:5173', deckId: '7', state: { a: { text: '', images: [], imageOrder: [] }, b: { text: '', images: [], imageOrder: [] } } }),
    /manualCard.emptyContent/,
  )
})

test('manual card save rejects more than ten images before runtime message', async () => {
  const save = loadSave({
    sendMessage: async () => {
      throw new Error('must not send')
    },
  })
  const saver = save.createSaver()
  const images = Array.from({ length: 11 }, (_, index) => ({
    id: `a${index}`,
    file: { name: `a${index}.jpg`, size: 1, type: 'image/jpeg' },
  }))

  await assert.rejects(
    saver.save({
      baseUrl: 'http://localhost:5173',
      deckId: '7',
      state: {
        a: { text: '', images, imageOrder: images.map((image) => image.id) },
        b: { text: '', images: [], imageOrder: [] },
      },
    }),
    /manualCard.tooManyImages/,
  )
})
