import { Button, Card } from 'konsta/react'
import { Fragment } from 'react'

function ImageChip({ image, sideKey }) {
  return (
    <span
      className="image relative mx-[3px] mb-[3px] inline-block align-middle"
      contentEditable={false}
      data-openflash-image-id={image.id}
    >
      <img
        alt=""
        className="block h-[72px] w-[72px] rounded-[6px] object-cover"
        src={image.previewUrl || ''}
      />
      <button
        className="absolute right-0.5 top-0.5 h-[18px] w-[18px] cursor-pointer rounded-[6px] border-0 bg-app-danger-fill p-0 text-xs leading-[18px] text-app-on-danger"
        data-remove-image={image.id}
        data-side-key={sideKey}
        type="button"
      >
        x
      </button>
    </span>
  )
}

function Editor({ label, onEditorInput, onEditorPaste, side, sideKey }) {
  const images = side.imageOrder
    .map((id) => side.images.find((image) => image.id === id))
    .filter(Boolean)
  const contentKey = `${side.text}\u0000${side.imageOrder.join('\u0000')}`

  return (
    <label className="grid gap-1 text-xs font-medium text-app-label-secondary">
      <span>{label}</span>
      <div
        className="editor min-h-[72px] whitespace-pre-wrap rounded-[6px] border border-app-separator bg-app-surface-secondary p-2 text-[13px] leading-5 text-app-label-primary outline-none focus:border-app-accent focus:ring-2 focus:ring-app-accent"
        contentEditable
        data-side={sideKey}
        key={contentKey}
        onInput={(event) => onEditorInput(sideKey, event.currentTarget)}
        onPaste={(event) => onEditorPaste(sideKey, event)}
        suppressContentEditableWarning
      >
        {side.text}
        {images.map((image) => (
          <Fragment key={image.id}>
            <ImageChip image={image} sideKey={sideKey} />
            <span className="caret-anchor inline leading-5">{'\u200B'}</span>
          </Fragment>
        ))}
      </div>
    </label>
  )
}

/** 渲染手动建卡窗口；所有浏览器副作用由外部控制器负责。 */
export default function ManualCardWindow({
  labels,
  mode,
  onCancel,
  onConfirmBack,
  onConfirmClose,
  onEditorInput,
  onEditorPaste,
  onRemoveImage,
  onSave,
  state,
}) {
  const t = (key) => labels[key] || key

  return (
    <Card
      className="overflow-hidden bg-app-surface-primary p-0 text-app-label-primary shadow-2xl"
      style={{ margin: 0, width: '100%' }}
    >
      <header
        className="cursor-move border-b border-app-separator px-3 py-2.5 text-[13px] font-semibold"
        data-role="drag"
      >
        {t('manualCard.title')}
      </header>

      {mode === 'confirm' ? (
        <div className="grid gap-3 p-3">
          <p className="m-0 text-sm text-app-label-primary">{t('manualCard.unsavedTitle')}</p>
          <div className="flex justify-end gap-2 border-t border-app-separator pt-2.5">
            <Button clear data-role="confirm-back" onClick={onConfirmBack}>
              {t('manualCard.unsavedBack')}
            </Button>
            <Button
              className="bg-app-danger-fill text-app-on-danger"
              data-role="confirm-close"
              onClick={onConfirmClose}
            >
              {t('manualCard.unsavedConfirm')}
            </Button>
          </div>
        </div>
      ) : (
        <>
          <div className="grid gap-2 p-3" onClick={onRemoveImage}>
            <Editor
              label={t('manualCard.sideA')}
              onEditorInput={onEditorInput}
              onEditorPaste={onEditorPaste}
              side={state.a}
              sideKey="a"
            />
            <Editor
              label={t('manualCard.sideB')}
              onEditorInput={onEditorInput}
              onEditorPaste={onEditorPaste}
              side={state.b}
              sideKey="b"
            />
            {state.error && <p className="m-0 text-sm text-app-danger" role="alert">{t(state.error)}</p>}
          </div>
          <div className="flex justify-end gap-2 border-t border-app-separator px-3 py-2.5">
            <Button clear data-role="cancel" disabled={state.saving} onClick={onCancel}>
              {t('manualCard.cancel')}
            </Button>
            <Button
              className="bg-app-accent-fill text-app-on-accent"
              data-role="save"
              disabled={state.saving}
              onClick={onSave}
            >
              {state.saving ? t('manualCard.saving') : t('manualCard.save')}
            </Button>
          </div>
        </>
      )}
    </Card>
  )
}
