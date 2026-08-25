import { useTranslation } from 'react-i18next'
import { DialogButton, List } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'
import KonstaDialogShell from './konsta/KonstaDialogShell'
import ListInput from './konsta/AppListInput'

/**
 * CSV 批量导入弹窗。open=false 时不渲染。
 * 输入态展示 textarea + 导入按钮；结果态展示成功/失败统计与逐行报错。
 */
export default function CsvImportModal({ open, csvText, csvResult, onTextChange, onImport, onClose }) {
  const { t } = useTranslation()
  return (
    <KonstaDialogShell
      open={open}
      onClose={onClose}
      title={t('deckDetail.batchImport')}
      className="!w-[min(28rem,calc(100vw-2rem))]"
      buttons={csvResult === null ? (
        <>
          <DialogButton onClick={withGenericClick(onClose)}>{t('common.cancel')}</DialogButton>
          <DialogButton strong onClick={withGenericClick(onImport)}>{t('deckDetail.importConfirmButton')}</DialogButton>
        </>
      ) : (
        <DialogButton strong onClick={withGenericClick(onClose)}>{t('common.done')}</DialogButton>
      )}
    >
        <div className="mb-3 space-y-1 text-sm text-app-label-tertiary">
          <p>{t('deckDetail.batchImportDescLine1')} <code className="rounded bg-app-surface-tertiary px-1 text-app-label-primary">A,B</code></p>
          <p>{t('deckDetail.batchImportDescLine2')}</p>
          <p>{t('deckDetail.batchImportDescLine3')} <code className="rounded bg-app-surface-tertiary px-1 text-app-label-primary">\n</code> {t('deckDetail.batchImportDescLine3Suffix')}</p>
        </div>
        {csvResult === null ? (
          <List inset strong className="!mx-0 !my-0">
            <ListInput
              outline
              type="textarea"
              autoFocus
              inputClassName="!min-h-32 !max-h-[35dvh] resize-none font-mono"
              placeholder={t('deckDetail.batchImportPlaceholder')}
              value={csvText}
              onChange={(e) => onTextChange(e.target.value)}
            />
          </List>
        ) : (
          <div className="text-center">
            <div className="text-4xl mb-3">✅</div>
            <p className="mb-1 text-base font-medium text-app-label-primary">{t('deckDetail.importCreated', { count: csvResult.createdCount })}</p>
            <p className="text-sm text-app-label-tertiary">{t('deckDetail.importDuplicateSkipped', { count: csvResult.duplicateCount })}</p>
            <p className="mb-5 text-sm text-app-label-tertiary">{t('deckDetail.importInvalidSkipped', { count: csvResult.invalidCount })}</p>
            {csvResult.failures?.length > 0 && (
              <div className="text-left mb-5 max-h-56 overflow-y-auto space-y-2">
                {csvResult.failures.map((failure, index) => (
                  <div key={`${failure.sideA}-${failure.sideB}-${index}`} className="rounded-lg border border-app-danger bg-app-danger-tonal px-3 py-2">
                    <p className="break-words text-sm font-medium text-app-danger">{failure.sideA || t('deckDetail.importEmptySideA')}{failure.sideB ? `, ${failure.sideB}` : ''}</p>
                    <p className="mt-1 text-xs text-app-danger">{failure.reason || t('deckDetail.importFailed')}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
    </KonstaDialogShell>
  )
}
