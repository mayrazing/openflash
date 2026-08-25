import { useEffect, useRef, useState } from 'react'
import { Button, Dialog, DialogButton, List } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import AppListInput from '../components/AppListInput.jsx'

export default function PlatformOfferingDialog({
  opened, connectionKey, disabled, onClose, onCreate, onDiscover,
}) {
  const { t } = useTranslation()
  const [modelKey, setModelKey] = useState('')
  const [models, setModels] = useState([])
  const [discoveryFailed, setDiscoveryFailed] = useState(false)
  const [discovering, setDiscovering] = useState(false)
  const discoveryGeneration = useRef(0)

  useEffect(() => {
    discoveryGeneration.current += 1
    if (opened) {
      setModelKey('')
      setModels([])
      setDiscoveryFailed(false)
      setDiscovering(false)
    }
  }, [connectionKey, opened])

  async function discover() {
    const generation = ++discoveryGeneration.current
    setDiscovering(true)
    try {
      const result = await onDiscover()
      if (generation !== discoveryGeneration.current) return
      setModels(Array.isArray(result) ? result : [])
      setDiscoveryFailed(false)
    } catch {
      if (generation !== discoveryGeneration.current) return
      setModels([])
      setDiscoveryFailed(true)
    } finally {
      if (generation === discoveryGeneration.current) setDiscovering(false)
    }
  }

  return (
    <Dialog
      opened={opened}
      onBackdropClick={disabled ? undefined : onClose}
      title={t('pages.platformAi.addOffering')}
      content={(
        <>
          <List inset strong outline className="!m-0">
            <AppListInput
              inputId="platform-ai-model-key"
              label={t('pages.platformAi.modelKey')}
              value={modelKey}
              disabled={disabled}
              onChange={event => setModelKey(event.target.value)}
            />
          </List>
          <Button className="mt-3 w-full" rounded outline disabled={disabled || discovering} onClick={discover}>
            {t('pages.platformAi.discoverModels')}
          </Button>
          {discoveryFailed && (
            <p className="mt-3 text-sm text-app-warning">{t('pages.platformAi.discoveryUnavailable')}</p>
          )}
          {models.length > 0 && (
            <div className="mt-3 grid gap-2">
              {models.map(model => (
                <Button key={model.modelKey} rounded tonal onClick={() => setModelKey(model.modelKey)}>
                  {model.modelKey}
                </Button>
              ))}
            </div>
          )}
        </>
      )}
      buttons={(
        <>
          <DialogButton disabled={disabled} onClick={onClose}>{t('pages.platformAi.cancel')}</DialogButton>
          <DialogButton
            strong
            disabled={disabled || !modelKey.trim()}
            onClick={() => onCreate(modelKey.trim(), 0)}
          >
            {t('pages.platformAi.createOffering')}
          </DialogButton>
        </>
      )}
    />
  )
}
