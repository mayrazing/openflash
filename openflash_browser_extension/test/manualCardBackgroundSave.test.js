import assert from 'node:assert/strict'
import test from 'node:test'
import { createManualCardBackgroundSaveHandler } from '../src/manualCardBackgroundSave.js'

test('background save uploads images and returns urls by image id', async () => {
  const uploaded = []
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async (baseUrl, imageFile) => {
      uploaded.push({ baseUrl, imageFile })
      return `/uploads/${imageFile.name}`
    },
    createImportedCard: async () => ({ id: 9 }),
    isTrustedSender: () => true,
  })

  const response = await handler({
    type: 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES',
    baseUrl: 'http://localhost:5173',
    sideAImages: [
      { id: 'a1', name: 'a1.jpg', type: 'image/jpeg', dataUrl: 'data:image/jpeg;base64,YTE=' },
      { id: 'a2', name: 'a2.jpg', type: 'image/jpeg', dataUrl: 'data:image/jpeg;base64,YTI=' },
    ],
    sideBImages: [
      { id: 'b1', name: 'b1.jpg', type: 'image/jpeg', dataUrl: 'data:image/jpeg;base64,YjE=' },
    ],
  })

  assert.deepEqual(response, { ok: true, uploadedByImageId: {
    a1: '/uploads/a1.jpg',
    a2: '/uploads/a2.jpg',
    b1: '/uploads/b1.jpg',
  }})
  assert.equal(uploaded.length, 3)
})

test('background save creates imported card with complete payload', async () => {
  const created = []
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async () => '/uploads/a.jpg',
    createImportedCard: async (baseUrl, deckId, payload) => {
      created.push({ baseUrl, deckId, payload })
      return { id: 9 }
    },
    isTrustedSender: () => true,
  })

  const response = await handler({
    type: 'OPENFLASH_MANUAL_CARD_CREATE',
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    payload: {
      sideA: 'front',
      sideAImage: ['/uploads/a1.jpg', '/uploads/a2.jpg'],
      sideB: 'back',
      sideBImage: ['/uploads/b1.jpg'],
    },
  })

  assert.deepEqual(response, { ok: true, card: { id: 9 } })
  assert.deepEqual(created[0].payload, {
    sideA: 'front',
    sideAImage: ['/uploads/a1.jpg', '/uploads/a2.jpg'],
    sideB: 'back',
    sideBImage: ['/uploads/b1.jpg'],
  })
})

test('background save ignores unrelated messages', async () => {
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async () => '/uploads/a.jpg',
    createImportedCard: async () => ({ id: 1 }),
    isTrustedSender: () => true,
  })

  assert.equal(await handler({ type: 'OTHER' }), false)
})

test('background save rejects manual-card writes from an untrusted sender', async () => {
  let creates = 0
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async () => '/uploads/a.jpg',
    createImportedCard: async () => { creates += 1 },
    isTrustedSender: () => false,
  })

  await assert.rejects(
    handler({
      type: 'OPENFLASH_MANUAL_CARD_CREATE',
      baseUrl: 'http://localhost:5173',
      deckId: '7',
      payload: { sideA: 'front' },
    }, { url: 'https://evil.test/' }),
    /untrusted manual card sender/,
  )
  assert.equal(creates, 0)
})

test('background save rejects too many images before uploading any file', async () => {
  let uploads = 0
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async () => { uploads += 1 },
    createImportedCard: async () => ({ id: 1 }),
    isTrustedSender: () => true,
    imageLimits: { maxImageCount: 2 },
  })

  await assert.rejects(
    handler({
      type: 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES',
      sideAImages: [
        { id: 'a', dataUrl: 'data:image/jpeg;base64,YQ==' },
        { id: 'b', dataUrl: 'data:image/jpeg;base64,Yg==' },
        { id: 'c', dataUrl: 'data:image/jpeg;base64,Yw==' },
      ],
    }),
    /manualCard.tooManyImages/,
  )
  assert.equal(uploads, 0)
})

test('background save checks single and aggregate decoded sizes before uploading', async () => {
  let uploads = 0
  const handler = createManualCardBackgroundSaveHandler({
    uploadImageFile: async () => { uploads += 1 },
    createImportedCard: async () => ({ id: 1 }),
    isTrustedSender: () => true,
    imageLimits: { maxImageBytes: 2, maxTotalImageBytes: 3 },
  })

  await assert.rejects(
    handler({
      type: 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES',
      sideAImages: [{ id: 'a', dataUrl: 'data:image/jpeg;base64,YWJj' }],
    }),
    /manualCard.imageTooLarge/,
  )
  await assert.rejects(
    handler({
      type: 'OPENFLASH_MANUAL_CARD_UPLOAD_IMAGES',
      sideAImages: [
        { id: 'a', dataUrl: 'data:image/jpeg;base64,YWI=' },
        { id: 'b', dataUrl: 'data:image/jpeg;base64,Y2Q=' },
      ],
    }),
    /manualCard.imagesTooLarge/,
  )
  assert.equal(uploads, 0)
})
