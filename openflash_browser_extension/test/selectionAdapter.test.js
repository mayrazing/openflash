import assert from 'node:assert/strict'
import test from 'node:test'
import {
  MAX_DATA_IMAGE_SOURCE_LENGTH,
  MAX_REMOTE_IMAGE_SOURCE_LENGTH,
  MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH,
  MAX_SELECTION_IMAGES,
  MAX_SELECTION_TEXT_LENGTH,
  extractSelectionFromHtml,
} from '../src/selectionAdapter.js'

test('extractSelectionFromHtml extracts text and image sources', () => {
  const result = extractSelectionFromHtml('<p>Hello <img src="https://img.test/a.png"> world</p>')

  assert.equal(result.sideA, 'Hello world')
  assert.deepEqual(result.imageSources, ['https://img.test/a.png'])
})

test('extractSelectionFromHtml keeps all image sources in order', () => {
  const result = extractSelectionFromHtml('<img src="https://img.test/a.png"><span>x</span><img src="data:image/png;base64,abc">')

  assert.equal(result.sideA, 'x')
  assert.deepEqual(result.imageSources, ['https://img.test/a.png', 'data:image/png;base64,abc'])
})

test('extractSelectionFromHtml resolves relative image sources against page URL', () => {
  const result = extractSelectionFromHtml(
    '<p>x<img src="/img/a.png"><img src="../b.png"><img src="data:image/png;base64,abc"></p>',
    'https://site.test/articles/page.html',
  )

  assert.equal(result.sideA, 'x')
  assert.deepEqual(result.imageSources, [
    'https://site.test/img/a.png',
    'https://site.test/b.png',
    'data:image/png;base64,abc',
  ])
})

test('extractSelectionFromHtml caps retained page image sources', () => {
  const html = Array.from(
    { length: MAX_SELECTION_IMAGES + 3 },
    (_, index) => `<img src="https://img.test/${index}.png">`,
  ).join('')

  const result = extractSelectionFromHtml(html)

  assert.equal(result.imageSources.length, MAX_SELECTION_IMAGES)
  assert.equal(result.imageSources.at(-1), `https://img.test/${MAX_SELECTION_IMAGES - 1}.png`)
})

test('extractSelectionFromHtml rejects oversized image sources before import', () => {
  const oversizedRemote = `https://img.test/${'a'.repeat(MAX_REMOTE_IMAGE_SOURCE_LENGTH)}`
  const oversizedData = `data:image/png;base64,${'a'.repeat(MAX_DATA_IMAGE_SOURCE_LENGTH)}`

  const result = extractSelectionFromHtml(
    `<img src="${oversizedRemote}"><img src="${oversizedData}"><img src="https://img.test/ok.png">`,
  )

  assert.deepEqual(result.imageSources, ['https://img.test/ok.png'])
})

test('extractSelectionFromHtml caps retained text', () => {
  const result = extractSelectionFromHtml(`<p>${'a'.repeat(MAX_SELECTION_TEXT_LENGTH + 10)}</p>`)

  assert.equal(result.sideA.length, MAX_SELECTION_TEXT_LENGTH)
})

test('extractSelectionFromHtml caps aggregate data URL source strings', () => {
  const source = `data:image/png;base64,${'a'.repeat(10 * 1024 * 1024)}`
  assert.ok(source.length < MAX_SELECTION_IMAGE_SOURCE_TOTAL_LENGTH)

  const result = extractSelectionFromHtml(`<img src="${source}"><img src="${source}">`)

  assert.equal(result.imageSources.length, 1)
})
