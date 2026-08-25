import { Toggle } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'

const DISABLED_TOGGLE_COLORS = {
  bgIos: 'bg-app-disabled-fill',
  checkedBgIos: 'bg-app-disabled-fill',
  thumbBgIos: 'bg-app-disabled-label',
  checkedThumbBgIos: 'bg-app-disabled-label',
  bgMaterial: 'bg-app-disabled-fill',
  checkedBgMaterial: 'bg-app-disabled-fill',
  borderMaterial: 'border-app-control',
  checkedBorderMaterial: 'border-app-control',
  thumbBgMaterial: 'bg-app-disabled-label',
  checkedThumbBgMaterial: 'bg-app-disabled-label',
}

export default function SettingSwitch({ checked, onChange, disabled = false, label }) {
  return (
    <Toggle
      checked={checked}
      disabled={disabled}
      colors={disabled ? DISABLED_TOGGLE_COLORS : undefined}
      onChange={withGenericClick((event) => onChange(event.target.checked))}
      className={`ml-4 shrink-0 ${disabled ? 'cursor-default' : ''}`}
    >
      {label ? <span className="sr-only">{label}</span> : null}
    </Toggle>
  )
}
