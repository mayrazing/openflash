import { useRef, useState } from 'react'
import { UnauthorizedError } from '../../db/database'
import { checkAiCacheStatus } from './api'
import { openAiCacheOrNotify } from './aiCacheStatus'
import i18n from '../../i18n.js'

const EMPTY_AI_DIALOG = { open: false, title: '', markdown: '' }

export default function useAiExplanationDialog() {
  const requestTokenRef = useRef(0)
  const [aiDialog, setAiDialog] = useState(EMPTY_AI_DIALOG)

  function closeAiDialog() {
    requestTokenRef.current += 1
    setAiDialog(EMPTY_AI_DIALOG)
  }

  async function openAiExplanation({
    cardId,
    side,
    title = i18n.t('aiCache.dialogTitle'),
    ignoreUnauthorized = false,
  }) {
    const requestToken = requestTokenRef.current + 1
    requestTokenRef.current = requestToken

    return openAiCacheOrNotify({
      cardId,
      side,
      title,
      checkAiCacheStatus,
      isIgnoredError: (error) => ignoreUnauthorized && error instanceof UnauthorizedError,
      isStale: () => requestTokenRef.current !== requestToken,
      onHit: ({ title, markdown }) => setAiDialog({ open: true, title, markdown }),
    })
  }

  return {
    aiDialog,
    openAiExplanation,
    closeAiDialog,
  }
}
