import test from 'node:test'
import assert from 'node:assert/strict'
import { getCardCreateFailureMessage } from './cardCreateMessages.js'
import i18n from '../i18n.js'

test('重复卡片创建失败时显示已存在文案', () => {
  const err = new Error()
  err.code = 40010
  assert.equal(getCardCreateFailureMessage(err), i18n.t('errors.40010'))
})

test('没有明确错误码时显示创建失败兜底', () => {
  assert.equal(getCardCreateFailureMessage(new Error()), i18n.t('errors.default'))
})
