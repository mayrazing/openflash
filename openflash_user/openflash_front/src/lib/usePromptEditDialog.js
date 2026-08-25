import { useState } from 'react'

export default function usePromptEditDialog(committedValue, onSave) {
  const [isOpen, setIsOpen] = useState(false)
  const [draft, setDraft] = useState(committedValue)

  function open() {
    setDraft(committedValue)
    setIsOpen(true)
  }

  function confirm() {
    onSave(draft)
    setIsOpen(false)
  }

  function cancel() {
    setDraft(committedValue)
    setIsOpen(false)
  }

  return { isOpen, draft, open, setDraft, confirm, cancel }
}
