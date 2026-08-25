import test from 'node:test'
import assert from 'node:assert/strict'
import { parseColloc } from '../src/lib/collocUtils.js'

test('标准格式：括号外是A面，括号内是B面', () => {
  const r = parseColloc('include...in...（把……包括在……里）')
  assert.deepEqual(r, { sideA: 'include...in...', sideB: '把……包括在……里' })
})

test('末尾有空格时正确 trim', () => {
  const r = parseColloc('be included in（被包括在……中）  ')
  assert.deepEqual(r, { sideA: 'be included in', sideB: '被包括在……中' })
})

test('没有括号时整行作 sideA，sideB 为空字符串', () => {
  const r = parseColloc('include everything')
  assert.deepEqual(r, { sideA: 'include everything', sideB: '' })
})

test('多个括号时取最后一对', () => {
  const r = parseColloc('A（注释）B（真正的B面）')
  assert.deepEqual(r, { sideA: 'A（注释）B', sideB: '真正的B面' })
})

test('半角括号：ought to do sth.(应该做某事)', () => {
  const r = parseColloc('ought to do sth.(应该做某事)')
  assert.deepEqual(r, { sideA: 'ought to do sth.', sideB: '应该做某事' })
})

test('混合括号：前面全角，末尾半角', () => {
  const r = parseColloc('ought not to / oughtn\'t to(不该做某事)')
  assert.deepEqual(r, { sideA: 'ought not to / oughtn\'t to', sideB: '不该做某事' })
})

test('中文冒号：冒号前是 sideA，冒号后是 sideB', () => {
  const r = parseColloc('take a break：休息一下')
  assert.deepEqual(r, { sideA: 'take a break', sideB: '休息一下' })
})

test('英文冒号：冒号前是 sideA，冒号后是 sideB', () => {
  const r = parseColloc('give it a shot: 试试看')
  assert.deepEqual(r, { sideA: 'give it a shot', sideB: '试试看' })
})

test('逗号：逗号前是 sideA，逗号后是 sideB', () => {
  const r = parseColloc('be into something, 对某事感兴趣')
  assert.deepEqual(r, { sideA: 'be into something', sideB: '对某事感兴趣' })
})

test('英文逗号但右侧无中文时不拆分', () => {
  const r = parseColloc('hello, how are you')
  assert.deepEqual(r, { sideA: 'hello, how are you', sideB: '' })
})
