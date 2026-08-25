import { useId } from 'react'
import { ListInput } from 'konsta/react'

/**
 * Konsta 5.2 的 ListInput 会把 null title 传给内部 class 合并器并在运行时崩溃.
 * 空字符串保持相同渲染结果, 同时绕开 null 分支.
 */
export default function AppListInput({ title = '', label, inputId, ...props }) {
  const generatedId = useId()
  const resolvedInputId = inputId ?? (label == null ? undefined : generatedId)
  const accessibleLabel = label == null
    ? undefined
    : <label htmlFor={resolvedInputId}>{label}</label>

  return (
    <ListInput
      {...props}
      title={title ?? ''}
      inputId={resolvedInputId}
      label={accessibleLabel}
    />
  )
}
