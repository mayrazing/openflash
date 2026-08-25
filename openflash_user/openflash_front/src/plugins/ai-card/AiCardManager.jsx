import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { UnauthorizedError } from '../../db/database'
import { appWarn } from '../../lib/appLog'
import { getKnownErrorMessage } from '../../lib/errorMessages.js'
import { useSse } from '../../sse/sseState'
import { checkAiCacheStatus, regenerateAiCache } from './api'
import {
  AI_ERROR_EVENT,
  AI_QUEUED_EVENT,
  AI_READY_EVENT,
  openAiCacheOrNotify,
  parseAiCacheReadyNotificationData,
} from './aiCacheStatus'
import AiCardDialog from './AiCardDialog'
import AiNotificationToast from './AiNotificationToast'

const EMPTY_DIALOG = {
  open: false,
  title: '',
  markdown: '',
  cardId: null,
  side: undefined,
  deckId: null,
}

/** 监听 'ai-card:open' 事件，管理 AI 解释对话框状态。 */
export default function AiCardManager() {
  const { t } = useTranslation()
  const [aiDialog, setAiDialog] = useState(EMPTY_DIALOG)
  const requestTokenRef = useRef(0)
  const { subscribe } = useSse()

  useEffect(() => {
    return subscribe(AI_READY_EVENT, (e) => {
      const payload = parseAiCacheReadyNotificationData(e.data)
      if (!payload) {
        appWarn(60002, 'Invalid ai-cache-ready event payload', e.data)
        return
      }

      window.dispatchEvent(new CustomEvent(AI_READY_EVENT, { detail: payload }))
    })
  }, [subscribe])

  useEffect(() => {
    async function handleOpen(event) {
      const { cardId, title = t('aiCache.dialogTitle'), side, deckId } = event.detail ?? {}
      if (!cardId) return

      const requestToken = ++requestTokenRef.current

      await openAiCacheOrNotify({
        cardId,
        side,
        title,
        checkAiCacheStatus,
        isIgnoredError: (error) => error instanceof UnauthorizedError,
        isStale: () => requestTokenRef.current !== requestToken,
        onHit: ({ title: hitTitle, markdown }) => setAiDialog({
          open: true,
          title: hitTitle,
          markdown,
          cardId,
          side,
          deckId,
        }),
      })
    }

    window.addEventListener('ai-card:open', handleOpen)
    return () => window.removeEventListener('ai-card:open', handleOpen)
  }, [t])

  function handleClose() {
    requestTokenRef.current++
    setAiDialog(EMPTY_DIALOG)
  }

  /** 强制重新生成当前弹框对应的 AI 解释，并复用现有生成中/错误提示。 */
  async function handleRegenerate() {
    if (!aiDialog.cardId) return
    try {
      await regenerateAiCache(aiDialog.cardId, aiDialog.side)
      window.dispatchEvent(new CustomEvent(AI_QUEUED_EVENT))
      handleClose()
    } catch (error) {
      const message = getKnownErrorMessage(error?.code) ?? t('aiCache.errorMessage')
      window.dispatchEvent(new CustomEvent(AI_ERROR_EVENT, { detail: { message } }))
    }
  }

  return (
    <>
      <AiNotificationToast />
      <AiCardDialog
        open={aiDialog.open}
        title={aiDialog.title}
        markdown={aiDialog.markdown}
        deckId={aiDialog.deckId}
        onRegenerate={aiDialog.cardId ? handleRegenerate : undefined}
        onClose={handleClose}
      />
    </>
  )
}
