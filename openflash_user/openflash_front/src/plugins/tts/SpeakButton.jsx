import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from 'konsta/react'
import { isEnglish, ttsApi } from './api.js'
import { playGenericClick } from '../../lib/soundEngine'
import { createSpeakerPressController } from './speakerPressController.js'
import SpeakerIcon from './SpeakerIcon.jsx'
import TtsCandidateDialog from './TtsCandidateDialog.jsx'

const sizeClasses = {
  sm: 'h-8 w-8',
  md: 'h-10 w-10',
  lg: 'h-12 w-12',
}

const iconSizeClasses = {
  sm: 'w-4 h-4',
  md: 'w-5 h-5',
  lg: 'w-6 h-6',
}

export default function SpeakButton({
  text,
  deckId,
  size = 'sm',
  className = '',
  onClick,
}) {
  const { t } = useTranslation()
  const [candidateDialogOpen, setCandidateDialogOpen] = useState(false)
  const latestPropsRef = useRef({ text, deckId, onClick })
  latestPropsRef.current = { text, deckId, onClick }

  const pressControllerRef = useRef(null)
  if (!pressControllerRef.current) {
    pressControllerRef.current = createSpeakerPressController({
      onShortPress: () => {
        const current = latestPropsRef.current
        playGenericClick()
        current.onClick?.()
        ttsApi.speakText(current.text, { deckId: current.deckId }).catch(() => {})
      },
      onLongPress: () => {
        playGenericClick()
        setCandidateDialogOpen(true)
      },
    })
  }

  useEffect(() => () => pressControllerRef.current?.pointerCancel(), [])

  if (!isEnglish(text)) return null

  return (
    <>
      <Button
        inline
        rounded
        tonal
        type="button"
        onPointerDown={(event) => {
          event.stopPropagation()
          pressControllerRef.current.pointerDown({ x: event.clientX, y: event.clientY })
        }}
        onPointerMove={(event) => {
          pressControllerRef.current.pointerMove({ x: event.clientX, y: event.clientY })
        }}
        onPointerUp={(event) => {
          event.stopPropagation()
          pressControllerRef.current.pointerEnd()
        }}
        onPointerCancel={() => pressControllerRef.current.pointerCancel()}
        onClick={(event) => {
          event.stopPropagation()
          pressControllerRef.current.click()
        }}
        onContextMenu={(event) => {
          event.preventDefault()
          event.stopPropagation()
        }}
        className={[
          '!p-0 active:scale-95 touch-manipulation select-none',
          sizeClasses[size] ?? sizeClasses.sm,
          className,
        ].join(' ')}
        title={t('speakButton.title')}
        aria-label={t('speakButton.ariaLabel', { text })}
      >
        <SpeakerIcon className={iconSizeClasses[size] ?? iconSizeClasses.sm} />
      </Button>
      <TtsCandidateDialog
        open={candidateDialogOpen}
        text={text}
        onClose={() => setCandidateDialogOpen(false)}
      />
    </>
  )
}
