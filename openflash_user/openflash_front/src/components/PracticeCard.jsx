import { useTranslation } from 'react-i18next'
import { Button } from 'konsta/react'
import TextWithBreaks from './TextWithBreaks'
import RichFaceContent from './RichFaceContent'
import PluginSlot from '../plugins/pluginSlot'
import { usePluginActionSlotState } from '../plugins/usePluginActionSlot'
import { hasImageTokens, stripImageTokens } from '../lib/richFaceOrder'

// 渲染卡片单面内容，并让长文本在当前宽度内自动换行。
function CardFace({ text, images }) {
  const imgs = Array.isArray(images) ? images : (images ? [images] : [])
  if (hasImageTokens(text)) {
    return (
      <div className="flex w-full min-w-0 flex-col items-center gap-3">
        <RichFaceContent
          text={text}
          images={imgs}
          className="inline-flex min-w-0 max-w-full flex-wrap items-center justify-center gap-3 text-center"
          textClassName="text-2xl font-medium leading-relaxed text-app-label-primary break-words [overflow-wrap:anywhere]"
          imageClassName="max-h-60 rounded-xl object-contain border border-app-separator max-w-full"
        />
      </div>
    )
  }
  return (
    <div className="flex w-full min-w-0 flex-col items-center gap-3">
      {text && (
        <p className="w-full min-w-0 max-w-full break-words text-center text-2xl font-medium leading-relaxed text-app-label-primary [overflow-wrap:anywhere]">
          <TextWithBreaks text={text} className="block min-w-0 max-w-full" />
        </p>
      )}
      {imgs.map((src, i) => (
        <img key={i} src={src} alt=""
          className="max-h-60 rounded-xl object-contain border border-app-separator w-full" />
      ))}
    </div>
  )
}

// 渲染卡片单面和右侧朗读按钮，按钮保持独立空间不被文字挤压。
// overlaySlot 可选：渲染到中间文字列上的覆盖层（如题目遮蔽），
// 用 relative 容器让 overlay 绝对定位仅覆盖文字+图片，不遮喇叭列。
function FaceWithSpeakButton({ text, images, showSpeakButton = false, deckId = null, overlaySlot = null }) {
  const actionText = stripImageTokens(text)
  return (
    <div className="grid w-full grid-cols-[3rem_1fr_3rem] items-center gap-3">
      <div />
      <div className="relative min-w-0 w-full max-w-full">
        <CardFace text={text} images={images} />
        {overlaySlot}
      </div>
      <div className="flex shrink-0 justify-end self-center">
        {showSpeakButton && <PluginSlot slotName="practice.card.actions" props={{ text: actionText, size: 'lg', deckId }} deckId={deckId} />}
      </div>
    </div>
  )
}

/**
 * props:
 *   card          - 卡片对象
 *   questionSide  - 'a' | 'b'，题目面
 *   answerSide    - 'a' | 'b'，答案面
 *   isRetry       - 是否为重练模式
 *   retryCount    - 当前已通过次数（0/1/2），用于显示进度
 *   retryTotal    - 当前重练总次数，用于显示真实分母
 *   revealed      - 答案是否已显示
 *   itemKey       - 当前题目项稳定键，供插件覆盖层定位/缓存用
 *   onReveal      - 点击"显示答案"的回调
 *   onFamiliar    - 点击"熟悉"的回调（重练模式下不传）
 */
export default function PracticeCard({
  card, questionSide, answerSide,
  isRetry = false, retryCount = 0, retryTotal = 3,
  revealed, itemKey, onReveal, onFamiliar,
  deckId = null,
}) {
  const { t } = useTranslation()
  const questionText = card[questionSide === 'a' ? 'sideA' : 'sideB']
  const questionImages = card[questionSide === 'a' ? 'sideAImage' : 'sideBImage']
  const answerText = card[answerSide === 'a' ? 'sideA' : 'sideB']
  const answerImages = card[answerSide === 'a' ? 'sideAImage' : 'sideBImage']
  const questionActionText = stripImageTokens(questionText)
  const answerActionText = stripImageTokens(answerText)
  const questionLabel = questionSide === 'a' ? t('common.sideA') : t('common.sideB')
  const answerLabel = answerSide === 'a' ? t('common.sideA') : t('common.sideB')
  const { loaded: questionActionsLoaded, actions: questionActions } = usePluginActionSlotState('practice.card.open-actions', {
    card,
    side: questionSide,
    text: questionActionText,
    deckId,
  }, deckId)
  const { loaded: answerActionsLoaded, actions: answerActions } = usePluginActionSlotState('practice.card.open-actions', {
    card,
    side: answerSide,
    text: answerActionText,
    deckId,
  }, deckId)
  const questionOpen = questionActions[0]?.onOpen
  const answerOpen = answerActions[0]?.onOpen
  const canOpenQuestion = questionActionsLoaded && Boolean(questionOpen)
  const canOpenAnswer = answerActionsLoaded && Boolean(answerOpen)

  return (
    <div className="flex flex-1 flex-col px-6 py-6">
      {/* 题目面，始终在上方；有插件入口时整块可点击触发扩展动作。
          题目面覆盖层通过 overlaySlot 注入到 FaceWithSpeakButton 的中间文字列内部，
          只覆盖文字+图片，不遮喇叭列。 */}
      <div
        className={`flex flex-col items-center${canOpenQuestion ? ' cursor-pointer' : ''}`}
        data-pointer-activation={canOpenQuestion ? '' : undefined}
        onClick={canOpenQuestion ? questionOpen : undefined}
      >
        {isRetry && (
          <span className="mb-3 rounded-full bg-app-warning-tonal px-3 py-1 text-xs text-app-warning">
            {t('practice.retryBadge', { current: retryCount, total: retryTotal })}
          </span>
        )}
        <p className="mb-4 text-xs uppercase tracking-wider text-app-label-tertiary">
          {questionLabel}
        </p>
        <FaceWithSpeakButton
          text={questionText}
          images={questionImages}
          showSpeakButton
          deckId={deckId}
          overlaySlot={
            // 题目面覆盖层插槽：按卡包已安装插件过滤，渲染在题目文字+图片之上、喇叭列之外。
            // 只有覆盖层插槽需要 pointer-events-none，避免插件返 null 时吞掉
            // 父容器 onClick={questionOpen}；真正接管 pointer 的 overlay 组件自行加
            // pointer-events-auto。
            <PluginSlot
              slotName="practice.question-face.overlay"
              props={{ card, questionSide, text: questionText, images: questionImages, revealed, itemKey, deckId }}
              className="absolute inset-0 z-10 pointer-events-none"
              blockClassName="contents"
              deckId={deckId}
            />
          }
        />
      </div>

      {/* 分隔线 */}
      <div className="my-6 border-t border-app-separator" />

      {/* 答案面，flex-1 撑满剩余高度；未显示时点击揭示，揭示后交给插件动作。 */}
      <div
        className={`flex min-h-24 flex-col items-center w-full flex-1${!revealed ? ' cursor-pointer' : (revealed && canOpenAnswer ? ' cursor-pointer' : '')}`}
        data-pointer-activation={(!revealed || canOpenAnswer) ? '' : undefined}
        onClick={!revealed ? onReveal : (canOpenAnswer ? answerOpen : undefined)}
      >
        {revealed && (
          <>
            <p className="mb-4 text-xs uppercase tracking-wider text-app-label-tertiary">
              {answerLabel}
            </p>
            <FaceWithSpeakButton text={answerText} images={answerImages} showSpeakButton deckId={deckId} />

            {/* 熟悉按钮：正常记忆模式下显示，重练模式下隐藏 */}
            {!isRetry && onFamiliar && (
              <Button
                inline
                rounded
                tonal
                className="mt-6"
                onClick={(e) => { e.stopPropagation(); onFamiliar() }}
              >
                {t('practice.familiar')}
              </Button>
            )}
          </>
        )}
      </div>
    </div>
  )
}
