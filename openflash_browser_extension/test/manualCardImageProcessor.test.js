import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'

function loadProcessor(extra = {}) {
  const context = vm.createContext({ console, globalThis: {}, ...extra })
  context.globalThis = context
  vm.runInContext(
    fs.readFileSync(new URL('../src/manualCardImageProcessor.js', import.meta.url), 'utf8'),
    context,
  )
  return context.OpenFlashManualCardImageProcessor
}

test('image processor enforces compressed single and aggregate sizes', async () => {
  const api = loadProcessor()
  const single = api.createProcessor({
    limits: { maxImageBytes: 2, maxTotalImageBytes: 10 },
    compressImage: async () => ({ size: 3 }),
  })
  await assert.rejects(
    single.prepareImages([{ id: 'a', file: { size: 4 } }]),
    /manualCard.imageTooLarge/,
  )

  const aggregate = api.createProcessor({
    limits: { maxImageBytes: 5, maxTotalImageBytes: 3 },
    compressImage: async () => ({ size: 2 }),
  })
  await assert.rejects(
    aggregate.prepareImages([
      { id: 'a', file: { size: 2 } },
      { id: 'b', file: { size: 2 } },
    ]),
    /manualCard.imagesTooLarge/,
  )
})

test('canvas compression keeps aspect ratio and caps the longest edge', async () => {
  const draws = []
  let closed = false
  const canvas = {
    width: 0,
    height: 0,
    getContext: () => ({
      fillRect: () => {},
      drawImage: (_bitmap, _x, _y, width, height) => draws.push({ width, height }),
    }),
    toBlob: (callback) => callback({ size: 800, type: 'image/jpeg' }),
  }
  const api = loadProcessor({
    document: { createElement: () => canvas },
    createImageBitmap: async () => ({
      width: 4000,
      height: 1000,
      close: () => { closed = true },
    }),
  })

  const prepared = await api.prepareImages([{ id: 'a', file: { size: 2 * 1024 * 1024 } }])

  assert.equal(prepared[0].file.size, 800)
  assert.deepEqual(draws[0], { width: 2000, height: 500 })
  assert.equal(closed, true)
})
