import { useEffect, useState } from 'react'
import { Button, Dialog, DialogButton, List, Preloader } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import AppListInput from '../components/AppListInput.jsx'
import { connectionCreatePayload } from './state.js'

export default function PlatformConnectionDialog({ opened, disabled, onClose, onCreate }) {
  const { t } = useTranslation()
  const [displayName, setDisplayName] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [modelKey, setModelKey] = useState('')
  const [discovering, setDiscovering] = useState(false)
  const [discoveryError, setDiscoveryError] = useState(false)

  useEffect(() => {
    if (opened) {
      setDisplayName('')
      setBaseUrl('')
      setApiKey('')
      setModelKey('')
      setDiscoveryError(false)
    }
  }, [opened])

  return (
    <Dialog
      opened={opened}
      onBackdropClick={disabled ? undefined : onClose}
      title={t('pages.platformAi.addConnection')}
      content={(
        <>
          <List inset strong outline className="!m-0">
            <AppListInput
              inputId="platform-ai-display-name"
              label={t('pages.platformAi.providerName')}
              value={displayName}
              disabled={disabled}
              onChange={event => setDisplayName(event.target.value)}
            />
            <AppListInput
              inputId="platform-ai-base-url"
              label={t('pages.platformAi.requestUrl')}
              value={baseUrl}
              disabled={disabled}
              onChange={event => setBaseUrl(event.target.value)}
            />
            <AppListInput
              inputId="platform-ai-api-key"
              label={t('pages.platformAi.apiKey')}
              type="password"
              value={apiKey}
              disabled={disabled}
              onChange={event => setApiKey(event.target.value)}
            />
            <AppListInput
              inputId="platform-ai-model-key"
              label={t('pages.platformAi.modelName')}
              value={modelKey}
              disabled={disabled}
              onChange={event => setModelKey(event.target.value)}
            />
          </List>
          <Button
            rounded
            outline
            className="mt-3"
            disabled={disabled || discovering || !baseUrl.trim() || !apiKey.trim()}
            onClick={async () => {
              setDiscovering(true)
              setDiscoveryError(false)
              try {
                const models = await onCreate.discover(baseUrl.trim(), apiKey)
                if (models[0]?.modelKey) setModelKey(models[0].modelKey)
              } catch {
                setDiscoveryError(true)
              } finally {
                setDiscovering(false)
              }
            }}
          >
            {discovering && <Preloader className="mr-2" />}
            {t('pages.platformAi.discoverModels')}
          </Button>
          {discoveryError && <p className="mt-2 text-sm text-app-danger">{t('pages.platformAi.discoveryUnavailable')}</p>}
          <p className="mt-3 text-sm text-app-label-secondary">{t('pages.platformAi.defaultDeny')}</p>
        </>
      )}
      buttons={(
        <>
          <DialogButton disabled={disabled} onClick={onClose}>{t('pages.platformAi.cancel')}</DialogButton>
          <DialogButton
            strong
            disabled={disabled || discovering || !displayName.trim() || !baseUrl.trim() || !apiKey.trim() || !modelKey.trim()}
            onClick={() => onCreate.save({
              connection: connectionCreatePayload('API', baseUrl.trim(), 0, displayName.trim()),
              apiKey,
              modelKey: modelKey.trim(),
            })}
          >
            {t('pages.platformAi.saveApiConfiguration')}
          </DialogButton>
        </>
      )}
    />
  )
}
