import assert from 'node:assert/strict'
import test from 'node:test'
import { mergeContextMenuImageSource, readSelectionWithContextImage } from '../src/contextMenuSelection.js'
import { resetLanguage, setLanguage } from '../src/i18n.js'

test('mergeContextMenuImageSource adds right-clicked image source before selection images', () => {
  const selection = {
    sideA: 'caption',
    imageSources: ['https://img.test/from-selection.png'],
  }
  const info = {
    mediaType: 'image',
    srcUrl: 'https://img.test/right-clicked.png',
  }

  const result = mergeContextMenuImageSource(selection, info)

  assert.deepEqual(result, {
    sideA: 'caption',
    imageSources: [
      'https://img.test/right-clicked.png',
      'https://img.test/from-selection.png',
    ],
  })
})

test('mergeContextMenuImageSource does not duplicate image already found in selection', () => {
  const selection = {
    sideA: '',
    imageSources: ['https://img.test/a.png'],
  }
  const info = {
    mediaType: 'image',
    srcUrl: 'https://img.test/a.png',
  }

  const result = mergeContextMenuImageSource(selection, info)

  assert.deepEqual(result.imageSources, ['https://img.test/a.png'])
})

test('readSelectionWithContextImage keeps right-clicked image when selection reader has no receiver', async () => {
  const info = {
    mediaType: 'image',
    srcUrl: 'https://img.test/right-clicked.png',
  }

  const result = await readSelectionWithContextImage(info, async () => {
    throw new Error('Could not establish connection. Receiving end does not exist.')
  })

  assert.deepEqual(result, {
    sideA: '',
    imageSources: ['https://img.test/right-clicked.png'],
  })
})

test('readSelectionWithContextImage uses fallback selection reader before falling back to image only', async () => {
  const info = {
    mediaType: 'image',
    srcUrl: 'https://img.test/right-clicked.png',
  }

  const result = await readSelectionWithContextImage(
    info,
    async () => {
      throw new Error('Could not establish connection. Receiving end does not exist.')
    },
    async () => ({
      sideA: '商家谈演出服卖十件退回九件',
      imageSources: [],
    }),
  )

  assert.deepEqual(result, {
    sideA: '商家谈演出服卖十件退回九件',
    imageSources: ['https://img.test/right-clicked.png'],
  })
})

test('readSelectionWithContextImage uses fallback selection reader for text-only selection', async () => {
  const result = await readSelectionWithContextImage(
    {},
    async () => {
      throw new Error('Could not establish connection. Receiving end does not exist.')
    },
    async () => ({
      sideA: '纯文字选区',
      imageSources: [],
    }),
  )

  assert.deepEqual(result, {
    sideA: '纯文字选区',
    imageSources: [],
  })
})

test('readSelectionWithContextImage maps missing receiver errors without right-clicked image', async () => {
  resetLanguage()
  try {
    const expected = /This page doesn't support extension import/

    await assert.rejects(
      readSelectionWithContextImage({}, async () => {
        throw new Error('Could not establish connection. Receiving end does not exist.')
      }),
      expected,
    )
  } finally {
    resetLanguage()
  }
})

test('readSelectionWithContextImage maps missing receiver in current language', async () => {
  try {
    resetLanguage()
    setLanguage('zh')

    await assert.rejects(
      readSelectionWithContextImage({}, async () => {
        throw new Error('Could not establish connection. Receiving end does not exist.')
      }),
      /当前页面不支持插件导入/,
    )
  } finally {
    resetLanguage()
  }
})
