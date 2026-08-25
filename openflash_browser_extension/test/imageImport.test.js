import assert from 'node:assert/strict'
import test from 'node:test'
import {
  MAX_IMAGE_SOURCES,
  MAX_LOCAL_IMAGE_BYTES,
  importImagesWithDeps,
  splitImageSources,
  summarizeImageImport,
} from '../src/imageImport.js'

test('splitImageSources separates remote and local sources', () => {
  const result = splitImageSources(['https://a.test/a.png', 'data:image/png;base64,abc', 'blob:https://site/id'])

  assert.deepEqual(result.remoteUrls, ['https://a.test/a.png'])
  assert.deepEqual(result.localSources, ['data:image/png;base64,abc', 'blob:https://site/id'])
})

test('summarizeImageImport counts failures', () => {
  const result = summarizeImageImport(['/uploads/a.jpg'], 2)

  assert.deepEqual(result, { sideAImage: ['/uploads/a.jpg'], failedCount: 2 })
})

test('importImages never fetches a remote URL after server transfer rejects it', async () => {
  const calls = []
  const result = await importImagesWithDeps('http://openflash.test', ['https://img.test/a.png'], {
    transferImages: async () => ({ results: [{ sourceUrl: 'https://img.test/a.png', success: false, code: 40092 }] }),
    fetchBlob: async (source) => {
      calls.push(source)
      return new Blob(['x'], { type: 'image/jpeg' })
    },
    uploadImageFile: async () => '/uploads/should-not-exist.jpg',
  })

  assert.deepEqual(calls, [])
  assert.deepEqual(result, { sideAImage: [], failedCount: 1 })
})

test('importImages never fetches remote URLs when transfer endpoint fails', async () => {
  const calls = []
  const result = await importImagesWithDeps('http://openflash.test', ['https://img.test/a.png'], {
    transferImages: async () => { throw new Error('backend unavailable') },
    fetchBlob: async (source) => {
      calls.push(source)
      return new Blob(['x'], { type: 'image/jpeg' })
    },
    uploadImageFile: async () => '/uploads/should-not-exist.jpg',
  })

  assert.deepEqual(calls, [])
  assert.deepEqual(result, { sideAImage: [], failedCount: 1 })
})

test('importImages preserves original order across local and remote sources', async () => {
  let localUploadCount = 0
  const result = await importImagesWithDeps('http://openflash.test', [
    'data:image/png;base64,abc',
    'https://img.test/a.png',
    'blob:https://site/id',
  ], {
    transferImages: async () => ({ results: [{ sourceUrl: 'https://img.test/a.png', success: true, url: '/uploads/remote.jpg' }] }),
    fetchBlob: async (source) => new Blob([source], { type: 'image/jpeg' }),
    uploadImageFile: async () => {
      localUploadCount += 1
      return localUploadCount === 1 ? '/uploads/data.jpg' : '/uploads/blob.jpg'
    },
  })

  assert.deepEqual(result, {
    sideAImage: ['/uploads/data.jpg', '/uploads/remote.jpg', '/uploads/blob.jpg'],
    failedCount: 0,
  })
})

test('importImages caps page-controlled image work', async () => {
  const sources = Array.from(
    { length: MAX_IMAGE_SOURCES + 5 },
    (_, index) => `https://img.test/${index}.png`,
  )
  let transferred = []
  const result = await importImagesWithDeps('http://openflash.test', sources, {
    transferImages: async (_baseUrl, urls) => {
      transferred = urls
      return { results: urls.map((sourceUrl, index) => ({ success: true, sourceUrl, url: `/u/${index}` })) }
    },
    fetchBlob: async () => { throw new Error('not expected') },
    uploadImageFile: async () => { throw new Error('not expected') },
  })

  assert.equal(transferred.length, MAX_IMAGE_SOURCES)
  assert.equal(result.sideAImage.length, MAX_IMAGE_SOURCES)
  assert.equal(result.failedCount, 5)
})

test('importImages rejects an oversized local blob before upload', async () => {
  let uploadCalls = 0
  const result = await importImagesWithDeps('http://openflash.test', ['blob:https://site/id'], {
    transferImages: async () => ({ results: [] }),
    fetchBlob: async () => new Blob([new Uint8Array(MAX_LOCAL_IMAGE_BYTES + 1)], { type: 'image/png' }),
    uploadImageFile: async () => {
      uploadCalls += 1
      return '/uploads/large.png'
    },
  })

  assert.equal(uploadCalls, 0)
  assert.deepEqual(result, { sideAImage: [], failedCount: 1 })
})
