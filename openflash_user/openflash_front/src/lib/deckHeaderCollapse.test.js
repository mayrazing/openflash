import test from 'node:test'
import assert from 'node:assert/strict'
import { shouldAutoCollapseDeckHeader } from './deckHeaderCollapse.js'

test('short deck keeps header expanded when collapsing would remove the current scroll position', () => {
  assert.equal(shouldAutoCollapseDeckHeader({
    previousScrollTop: 40,
    nextScrollTop: 80,
    scrollHeight: 811,
    clientHeight: 649,
    collapsibleHeight: 244,
  }), false)
})

test('long deck auto collapses header when the current scroll position remains valid', () => {
  assert.equal(shouldAutoCollapseDeckHeader({
    previousScrollTop: 40,
    nextScrollTop: 80,
    scrollHeight: 1200,
    clientHeight: 649,
    collapsibleHeight: 244,
  }), true)
})

test('header does not auto collapse before the trigger distance or while scrolling upward', () => {
  assert.equal(shouldAutoCollapseDeckHeader({
    previousScrollTop: 40,
    nextScrollTop: 48,
    scrollHeight: 1200,
    clientHeight: 649,
    collapsibleHeight: 244,
  }), false)
  assert.equal(shouldAutoCollapseDeckHeader({
    previousScrollTop: 80,
    nextScrollTop: 70,
    scrollHeight: 1200,
    clientHeight: 649,
    collapsibleHeight: 244,
  }), false)
})
