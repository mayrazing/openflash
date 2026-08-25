import test from 'node:test'
import assert from 'node:assert/strict'
import { imageToken, splitFaceContent, stripImageTokens } from './richFaceOrder.js'

test('splitFaceContent keeps text and images in token order', () => {
  assert.deepEqual(
    splitFaceContent(`1${imageToken(0)}2${imageToken(1)}3`, ['/a.jpg', '/b.jpg']),
    [
      { type: 'text', text: '1' },
      { type: 'image', src: '/a.jpg', index: 0 },
      { type: 'text', text: '2' },
      { type: 'image', src: '/b.jpg', index: 1 },
      { type: 'text', text: '3' },
    ],
  )
})

test('splitFaceContent appends legacy images when text has no tokens', () => {
  assert.deepEqual(
    splitFaceContent('front', ['/a.jpg']),
    [
      { type: 'text', text: 'front' },
      { type: 'image', src: '/a.jpg', index: 0 },
    ],
  )
})

test('stripImageTokens removes internal image placeholders from plain text', () => {
  assert.equal(stripImageTokens(`1${imageToken(0)}2`), '12')
})
