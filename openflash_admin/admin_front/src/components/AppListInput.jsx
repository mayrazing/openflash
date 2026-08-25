import { useId } from 'react'
import { ListInput } from 'konsta/react'

export default function AppListInput({ inputId, label, title, ...props }) {
  const generatedInputId = useId()
  const resolvedInputId = inputId ?? generatedInputId

  return (
    <ListInput
      {...props}
      inputId={resolvedInputId}
      label={<label htmlFor={resolvedInputId}>{label}</label>}
      title={title ?? ''}
    />
  )
}
