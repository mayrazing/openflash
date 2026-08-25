import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

const source = readFileSync(join(__dirname, 'Practice.jsx'), 'utf8')
const activeViewSource = readFileSync(join(__dirname, '..', 'components', 'practice', 'PracticeActiveView.jsx'), 'utf8')

test('Practice page no longer shows mastered confirmation dialog after normal scoring', () => {
  const source = readFileSync(join(__dirname, 'Practice.jsx'), 'utf8')

  assert.doesNotMatch(source, /是否移入会了收集本/)
  assert.doesNotMatch(source, /handleMasteredConfirm/)
  assert.doesNotMatch(source, /handleMasteredContinue/)
})

test('PracticeCard waits for plugin action loading before exposing AI open affordance', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /usePluginActionSlotState\('practice\.card\.open-actions'/)
  assert.match(source, /const\s+canOpenQuestion\s*=\s*questionActionsLoaded\s*&&\s*Boolean\(questionOpen\)/)
  assert.match(source, /const\s+canOpenAnswer\s*=\s*answerActionsLoaded\s*&&\s*Boolean\(answerOpen\)/)
  assert.match(source, /onClick=\{canOpenQuestion\s*\?\s*questionOpen\s*:\s*undefined\}/)
  assert.match(source, /onClick=\{!revealed\s*\?\s*onReveal\s*:\s*\(canOpenAnswer\s*\?\s*answerOpen\s*:\s*undefined\)\}/)
})

test('PracticeCard marks non-button clickable faces for pen activation bridge', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /data-pointer-activation=\{canOpenQuestion \? '' : undefined\}/)
  assert.match(source, /data-pointer-activation=\{\(!revealed \|\| canOpenAnswer\) \? '' : undefined\}/)
})

test('PracticeCard uses a Konsta button for the familiar action', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /import \{ Button \} from 'konsta\/react'/)
  assert.match(source, /<Button[\s\S]*t\('practice\.familiar'\)/)
})

test('PracticeCard重练徽标显示已完成次数并允许0作为分子', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /current:\s*retryCount,\s*total:\s*retryTotal/)
  assert.doesNotMatch(source, /current:\s*retryCount\s*\+\s*1/)
})

test('PracticeCard renders question-face overlay plugin slot', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /slotName="practice\.question-face\.overlay"/)
})

test('PracticeCard overlay slot defers pointer capture to overlay plugin itself', () => {
  // 新设计：只有 overlay 调用点显式 pointer-events-none，overlay 组件自带 pointer-events-auto
  // 才接管 pointer。PracticeCard 不再在 containerProps 上 stopPropagation，空 overlay
  // 不会吞掉父容器 onClick={questionOpen}。
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  assert.match(source, /className="absolute inset-0 z-10 pointer-events-none"/)
  assert.doesNotMatch(source, /stopOverlayPropagation/)
  assert.doesNotMatch(source, /containerProps=\{\{[\s\S]*?onPointerDown/)
})

test('Practice passes itemKey down to PracticeActiveView', () => {
  const source = readFileSync(join(__dirname, 'Practice.jsx'), 'utf8')

  assert.match(source, /itemKey=\{item\?\.itemKey\}/)
})

test('Practice按同一卡另一方向的首次评分限制评分按钮', () => {
  assert.match(source, /ratingsForPracticeItem/)
  assert.match(source, /ratings=\{availableRatings\}/)
  assert.match(activeViewSource, /gridTemplateColumns:\s*`repeat\(\$\{ratings\.length\}/)
})

test('PracticeActiveView forwards itemKey to PracticeCard', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'practice', 'PracticeActiveView.jsx'), 'utf8')

  assert.match(source, /itemKey=\{itemKey\}/)
})

test('PracticeCard overlay slot receives card, questionSide, text, images, revealed, itemKey', () => {
  const source = readFileSync(join(__dirname, '..', 'components', 'PracticeCard.jsx'), 'utf8')

  // 拆成每字段一条独立断言：单条长正则对字段顺序/空白重排过度敏感，
  // 拆后每字段独立匹配 props={{ }} 块内对应字段（[^}]* 跨整个对象内容），顺序无关。
  assert.match(source, /props=\{\{[^}]*\bcard\b/)
  assert.match(source, /props=\{\{[^}]*\bquestionSide\b/)
  assert.match(source, /props=\{\{[^}]*\btext:\s*questionText\b/)
  assert.match(source, /props=\{\{[^}]*\bimages:\s*questionImages\b/)
  assert.match(source, /props=\{\{[^}]*\brevealed\b/)
  assert.match(source, /props=\{\{[^}]*\bitemKey\b/)
})

test('PracticeActiveView uses BottomActionBar for rating controls', () => {
  assert.match(activeViewSource, /BottomActionBar/)
  assert.match(activeViewSource, /<BottomActionBar tone="surface" dialogBoundary>/)
  assert.doesNotMatch(activeViewSource, /fixed bottom-0 left-0 right-0 px-4/)
})

test('PracticeActiveView gives both header actions the Konsta navbar glass appearance', () => {
  assert.match(activeViewSource, /import \{ Button, Glass, Progressbar \} from 'konsta\/react'/)
  assert.equal(
    (activeViewSource.match(/<Glass className="shrink-0 transform transform-gpu rounded-full">/g) ?? []).length,
    2
  )
  assert.match(activeViewSource, /<Button inline small clear rounded onClick=\{onExit\}>/)
  assert.match(activeViewSource, /<Button inline small clear rounded onClick=\{onGoBack\}>/)
})

test('PracticeActiveView keeps rating controls as native buttons for app-wide pen bridge', () => {
  assert.doesNotMatch(activeViewSource, /PracticeActionButton/)
  assert.match(activeViewSource, /<button onClick=\{\(\) => onRetry\(false\)\}/)
  assert.match(activeViewSource, /<button onClick=\{onRetrySlowRecall\}/)
  assert.match(activeViewSource, /<button onClick=\{\(\) => onRetry\(true\)\}/)
  assert.match(activeViewSource, /<button key=\{r\.value\} onClick=\{\(\) => onRate\(r\.value\)\}/)
})

test('PracticeActiveView uses hard tokens for slow retry recall', () => {
  const slowRetryButton = activeViewSource.match(/<button onClick=\{onRetrySlowRecall\}[\s\S]*?<\/button>/)?.[0] ?? ''

  assert.match(slowRetryButton, /bg-app-practice-hard-tonal/)
  assert.match(slowRetryButton, /text-app-practice-hard/)
  assert.match(slowRetryButton, /active:bg-app-practice-hard-pressed/)
  assert.doesNotMatch(slowRetryButton, /practice-good/)
})

test('Practice page computes bottom padding from safe bottom bar token', () => {
  assert.doesNotMatch(source, /APP_BOTTOM_BAR/)
  assert.match(source, /--app-bottom-bar-height/)
  assert.match(source, /viewport/)
  assert.match(source, /showSideDistribution/)
  assert.match(source, /bottomDistributionHeight/)
  assert.match(source, /distributionBottomPadding/)
})

test('Practice distribution avoids impossible negative side width', () => {
  assert.match(source, /Math\.max\(0/)
  assert.match(source, /distributionSideWidth/)
  assert.match(activeViewSource, /showSideDistribution/)
})
