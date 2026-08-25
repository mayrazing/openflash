import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Checkbox, DialogButton, List, ListItem } from 'konsta/react'
import KonstaDialogShell from './konsta/KonstaDialogShell'

/**
 * 插件选卡包安装弹窗：勾选要应用该插件的卡包，预勾已装卡包。
 * 确定时把「现勾选」与「预装」对比，拆成新增安装（installDeckIds）与取消安装（uninstallDeckIds）两组回调给父组件。
 */
export default function PluginInstallDialog({ plugin, decks, preinstalledDeckIds, onConfirm, onCancel }) {
  const { t } = useTranslation()
  const [checked, setChecked] = useState(() => new Set(preinstalledDeckIds))

  function toggle(deckId) {
    setChecked(prev => {
      const next = new Set(prev)
      if (next.has(deckId)) next.delete(deckId)
      else next.add(deckId)
      return next
    })
  }

  const { installDeckIds, uninstallDeckIds, changed } = useMemo(() => {
    const pre = new Set(preinstalledDeckIds)
    const install = [...checked].filter(id => !pre.has(id))
    const uninstall = [...pre].filter(id => !checked.has(id))
    return {
      installDeckIds: install,
      uninstallDeckIds: uninstall,
      changed: install.length > 0 || uninstall.length > 0,
    }
  }, [checked, preinstalledDeckIds])

  return (
    <KonstaDialogShell
      open
      onClose={onCancel}
      title={t(`plugins.${plugin?.pluginId}.name`, { defaultValue: plugin?.name })}
      className="!w-[min(20rem,calc(100vw-2rem))]"
      buttons={(
        <>
          <DialogButton onClick={onCancel}>
            {t('common.cancel')}
          </DialogButton>
          <DialogButton
            strong
            disabled={!changed}
            onClick={() => onConfirm(installDeckIds, uninstallDeckIds)}
          >
            {t('marketplace.confirmInstall')}
          </DialogButton>
        </>
      )}
    >
      <p className="mb-3 text-sm text-app-label-secondary">{t('marketplace.applyToDecks')}</p>

      <List strong outline className="!my-0">
        {decks.map(deck => (
          <ListItem
            key={deck.id}
            label
            title={deck.name}
            media={(
              <Checkbox
                component="span"
                checked={checked.has(deck.id)}
                onChange={() => toggle(deck.id)}
              />
            )}
          />
        ))}
      </List>
    </KonstaDialogShell>
  )
}
