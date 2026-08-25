import { useEffect, useState } from 'react'
import { Button, Card, Dialog, DialogButton, List, ListItem, Preloader, Toggle } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import CodexAdminPage from '../codex/CodexAdminPage.jsx'
import AppListInput from '../components/AppListInput.jsx'
import PlatformConnectionDialog from './PlatformConnectionDialog.jsx'
import PlatformOfferingDialog from './PlatformOfferingDialog.jsx'
import {
  createPlatformConnection,
  createPlatformOffering,
  deletePlatformConnection,
  deletePlatformOffering,
  discoverPlatformModels,
  discoverPlatformModelsForConfiguration,
  getPlatformAiPage,
  replacePlatformCredentials,
  setPlatformOfferingDefaultAccess,
  updatePlatformConnection,
  updatePlatformOffering,
} from './api.js'
import { normalizePlatformAiPage } from './state.js'

const defaultApi = {
  createConnection: createPlatformConnection,
  createOffering: createPlatformOffering,
  deleteConnection: deletePlatformConnection,
  deleteOffering: deletePlatformOffering,
  discoverModels: discoverPlatformModels,
  discoverModelsForConfiguration: discoverPlatformModelsForConfiguration,
  getPage: getPlatformAiPage,
  replaceCredentials: replacePlatformCredentials,
  setDefaultAccess: setPlatformOfferingDefaultAccess,
  updateConnection: updatePlatformConnection,
  updateOffering: updatePlatformOffering,
}

export default function PlatformAiPage({ api = defaultApi, cliApi }) {
  const { t } = useTranslation()
  const [page, setPage] = useState(() => normalizePlatformAiPage(null))
  const [viewState, setViewState] = useState('loading')
  const [reloadVersion, setReloadVersion] = useState(0)
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState(false)
  const [connectionDialogOpen, setConnectionDialogOpen] = useState(false)
  const [offeringConnectionKey, setOfferingConnectionKey] = useState(null)
  const [credentialConnectionKey, setCredentialConnectionKey] = useState(null)
  const [credential, setCredential] = useState('')
  const [deleteTarget, setDeleteTarget] = useState(null)

  useEffect(() => {
    let active = true
    setViewState('loading')
    api.getPage()
      .then(value => {
        if (!active) return
        setPage(normalizePlatformAiPage(value))
        setViewState('ready')
      })
      .catch(() => {
        if (active) setViewState('error')
      })
    return () => { active = false }
  }, [api, reloadVersion])

  const runtimeDisabled = viewState !== 'ready' || !page.runtimeAvailable || busy
  const runtimeLabel = status => t(`pages.platformAi.runtime.${status}`, { defaultValue: status })

  async function mutate(action, update) {
    setBusy(true)
    setSaveError(false)
    try {
      const result = await action()
      if (update) setPage(current => update(current, result))
      return true
    } catch {
      setSaveError(true)
      return false
    } finally {
      setBusy(false)
    }
  }

  async function createApiConfiguration({ connection: payload, apiKey, modelKey }) {
    setBusy(true)
    setSaveError(false)
    let connection = null
    try {
      connection = await api.createConnection(payload)
      await api.replaceCredentials(connection.connectionKey, apiKey)
      const offering = await api.createOffering(connection.connectionKey, modelKey, 0)
      setPage(current => ({
        ...current,
        connections: [...current.connections, {
          ...connection,
          credentialsConfigured: true,
          offerings: [offering],
        }],
      }))
      setConnectionDialogOpen(false)
    } catch {
      if (connection?.connectionKey) {
        try { await api.deleteConnection(connection.connectionKey) } catch { /* Keep the original error visible. */ }
      }
      setSaveError(true)
    } finally {
      setBusy(false)
    }
  }

  async function createOffering(modelKey, sortOrder) {
    const connectionKey = offeringConnectionKey
    const saved = await mutate(
      () => api.createOffering(connectionKey, modelKey, sortOrder),
      (current, result) => ({
        ...current,
        connections: current.connections.map(connection => connection.connectionKey === connectionKey
          ? { ...connection, offerings: result?.offeringKey
              ? [...connection.offerings, result]
              : connection.offerings }
          : connection),
      }),
    )
    if (saved) setOfferingConnectionKey(null)
  }

  async function replaceCredentials() {
    const connectionKey = credentialConnectionKey
    const saved = await mutate(() => api.replaceCredentials(connectionKey, credential), current => ({
      ...current,
      connections: current.connections.map(connection => connection.connectionKey === connectionKey
        ? { ...connection, credentialsConfigured: true }
        : connection),
    }))
    if (saved) {
      setCredential('')
      setCredentialConnectionKey(null)
    }
  }

  function updateConnectionEnabled(connection, enabled) {
    mutate(
      () => api.updateConnection(connection.connectionKey, {
        baseUrl: connection.baseUrl, enabled, sortOrder: connection.sortOrder,
      }),
      current => ({
        ...current,
        connections: current.connections.map(item => item.connectionKey === connection.connectionKey
          ? { ...item, enabled }
          : item),
      }),
    )
  }

  function updateOfferingEnabled(offering, enabled) {
    mutate(
      () => api.updateOffering(offering.offeringKey, {
        modelKey: offering.modelKey, enabled, sortOrder: offering.sortOrder,
      }),
      current => updateOfferingInPage(current, offering.offeringKey, { enabled }),
    )
  }

  function updateDefaultAccess(offering, enabled) {
    mutate(
      () => api.setDefaultAccess(offering.offeringKey, enabled),
      current => updateOfferingInPage(current, offering.offeringKey, { defaultAccess: enabled }),
    )
  }

  async function confirmDelete() {
    const target = deleteTarget
    const saved = await mutate(
      () => target.kind === 'connection'
        ? api.deleteConnection(target.key)
        : api.deleteOffering(target.key),
      current => target.kind === 'connection'
        ? { ...current, connections: current.connections.filter(item => item.connectionKey !== target.key) }
        : {
            ...current,
            connections: current.connections.map(connection => ({
              ...connection,
              offerings: connection.offerings.filter(item => item.offeringKey !== target.key),
            })),
          },
    )
    if (saved) setDeleteTarget(null)
  }

  const apiConnections = page.connections.filter(connection => connection.kind === 'API')

  return (
    <>
      <div className="mb-6 px-1">
        <h2 className="text-2xl font-bold text-app-label-primary">{t('pages.platformAi.title')}</h2>
        <p className="mt-2 text-sm text-app-label-tertiary">{t('pages.platformAi.description')}</p>
      </div>

      <section aria-labelledby="platform-ai-cli-title" className="mb-8">
        <div className="mb-3 px-1">
          <h3 id="platform-ai-cli-title" className="text-xl font-bold text-app-label-primary">
            {t('pages.cli.title')}
          </h3>
          <p className="mt-1 text-sm text-app-label-tertiary">{t('pages.cli.description')}</p>
        </div>
        <CodexAdminPage api={cliApi} embedded />
      </section>

      <section aria-labelledby="platform-ai-api-title" className="platform-ai-content-column">
        <div className="mb-4 flex items-start justify-between gap-4 px-1">
          <div>
            <h3 id="platform-ai-api-title" className="text-xl font-bold text-app-label-primary">
              {t('pages.platformAi.apiSectionTitle')}
            </h3>
            <p className="mt-1 text-sm text-app-label-tertiary">
              {t('pages.platformAi.apiSectionDescription')}
            </p>
          </div>
          <Button rounded outline disabled={runtimeDisabled} onClick={() => setConnectionDialogOpen(true)}>
            {t('pages.platformAi.addConnection')}
          </Button>
        </div>

        {viewState === 'loading' && (
          <Card raised outline className="!m-0 text-center">
            <Preloader />
            <p>{t('pages.platformAi.loading')}</p>
          </Card>
        )}

        {viewState === 'error' && (
          <Card raised outline className="!m-0" role="alert">
            <p className="font-semibold text-app-danger">{t('pages.platformAi.loadError')}</p>
            <Button className="mt-4" rounded outline onClick={() => setReloadVersion(value => value + 1)}>
              {t('pages.platformAi.retry')}
            </Button>
          </Card>
        )}

        {viewState === 'ready' && !page.runtimeAvailable && (
          <Card raised outline className="!mb-4 !mt-0" role="status">
            <p className="font-semibold text-app-warning">{t('pages.platformAi.runtimeUnavailable')}</p>
            <p className="mt-2 text-sm text-app-label-secondary">{t('pages.platformAi.runtimeUnavailableDescription')}</p>
          </Card>
        )}

        {saveError && (
          <Card raised outline className="!mb-4 !mt-0" role="alert">
            <p className="text-sm font-semibold text-app-danger">{t('pages.platformAi.saveError')}</p>
          </Card>
        )}

        {viewState === 'ready' && apiConnections.length === 0 && (
          <Card raised outline className="!m-0 text-center">
            <p className="font-semibold text-app-label-primary">{t('pages.platformAi.emptyApiTitle')}</p>
            <p className="mt-2 text-sm text-app-label-secondary">{t('pages.platformAi.emptyApiDescription')}</p>
          </Card>
        )}

        {viewState === 'ready' && apiConnections.map(connection => (
          <Card key={connection.connectionKey} raised outline header={connection.displayName || t('pages.platformAi.apiConnection')} className="!mb-5 !mt-0">
            <List inset strong outline className="!m-0">
              <ListItem
                title={connection.displayName || t('pages.platformAi.apiConnection')}
                subtitle={`${connection.baseUrl} · ${t('pages.platformAi.runtimeStatus', { status: runtimeLabel(page.runtimeStatus) })}`}
                after={(
                  <Toggle
                    checked={connection.enabled}
                    disabled={runtimeDisabled}
                    aria-label={t('pages.platformAi.connectionEnabledAria', {
                      name: connection.displayName || t('pages.platformAi.apiConnection'),
                    })}
                    onChange={event => updateConnectionEnabled(connection, event.target.checked)}
                  />
                )}
              />
              <ListItem
                title={t(connection.credentialsConfigured
                  ? 'pages.platformAi.credentialConfigured'
                  : 'pages.platformAi.credentialMissing')}
                after={(
                  <Button rounded outline disabled={runtimeDisabled} onClick={() => {
                    setCredential('')
                    setCredentialConnectionKey(connection.connectionKey)
                  }}>
                    {t('pages.platformAi.replaceCredential')}
                  </Button>
                )}
              />
            </List>

            {connection.offerings.map(offering => (
              <List key={offering.offeringKey} inset strong outline className="!mb-0 !mt-3">
                <ListItem
                  title={offering.modelKey}
                  subtitle={t('pages.platformAi.runtimeStatus', { status: runtimeLabel(offering.runtimeStatus) })}
                  after={(
                    <Toggle
                      checked={offering.enabled}
                      disabled={runtimeDisabled}
                      aria-label={t('pages.platformAi.offeringEnabledAria', { model: offering.modelKey })}
                      onChange={event => updateOfferingEnabled(offering, event.target.checked)}
                    />
                  )}
                />
                <ListItem
                  title={offering.defaultAccess
                    ? t('pages.platformAi.allUsers')
                    : t('pages.platformAi.selectedUsers')}
                  after={(
                    <Toggle
                      checked={offering.defaultAccess}
                      disabled={runtimeDisabled}
                      aria-label={t('pages.platformAi.defaultAccessAria', {
                        model: offering.modelKey,
                      })}
                      onChange={event => updateDefaultAccess(offering, event.target.checked)}
                    />
                  )}
                />
                <ListItem after={(
                  <Button
                    rounded
                    outline
                    className="text-app-danger"
                    disabled={runtimeDisabled}
                    onClick={() => setDeleteTarget({ kind: 'offering', key: offering.offeringKey })}
                  >{t('pages.platformAi.deleteOffering')}</Button>
                )} />
              </List>
            ))}

            <div className="mt-4 flex flex-wrap justify-end gap-3">
              <Button rounded outline disabled={runtimeDisabled} onClick={() => setOfferingConnectionKey(connection.connectionKey)}>
                {t('pages.platformAi.addOffering')}
              </Button>
              <Button
                rounded
                outline
                className="text-app-danger"
                disabled={runtimeDisabled}
                onClick={() => setDeleteTarget({ kind: 'connection', key: connection.connectionKey })}
              >{t('pages.platformAi.deleteConnection')}</Button>
            </div>
          </Card>
        ))}
      </section>

      {connectionDialogOpen && <PlatformConnectionDialog
        opened={connectionDialogOpen}
        disabled={runtimeDisabled}
        onClose={() => setConnectionDialogOpen(false)}
        onCreate={{
          discover: api.discoverModelsForConfiguration,
          save: createApiConfiguration,
        }}
      />}
      <PlatformOfferingDialog
        opened={Boolean(offeringConnectionKey)}
        connectionKey={offeringConnectionKey}
        disabled={runtimeDisabled}
        onClose={() => setOfferingConnectionKey(null)}
        onCreate={createOffering}
        onDiscover={() => api.discoverModels(offeringConnectionKey)}
      />

      <Dialog
        opened={Boolean(credentialConnectionKey)}
        onBackdropClick={runtimeDisabled ? undefined : () => setCredentialConnectionKey(null)}
        title={t('pages.platformAi.replaceCredential')}
        content={(
          <List inset strong outline className="!m-0">
            <AppListInput
              inputId="platform-ai-api-key"
              label={t('pages.platformAi.apiKey')}
              type="password"
              value={credential}
              disabled={runtimeDisabled}
              onChange={event => setCredential(event.target.value)}
            />
          </List>
        )}
        buttons={(
          <>
            <DialogButton disabled={runtimeDisabled} onClick={() => setCredentialConnectionKey(null)}>
              {t('pages.platformAi.cancel')}
            </DialogButton>
            <DialogButton strong disabled={runtimeDisabled || !credential} onClick={replaceCredentials}>
              {t('pages.platformAi.saveCredential')}
            </DialogButton>
          </>
        )}
      />

      <Dialog
        opened={Boolean(deleteTarget)}
        onBackdropClick={runtimeDisabled ? undefined : () => setDeleteTarget(null)}
        title={t('pages.platformAi.deleteConfirmTitle')}
        content={t(deleteTarget?.kind === 'connection'
          ? 'pages.platformAi.deleteConnectionConfirmBody'
          : 'pages.platformAi.deleteOfferingConfirmBody')}
        buttons={(
          <>
            <DialogButton disabled={runtimeDisabled} onClick={() => setDeleteTarget(null)}>
              {t('pages.platformAi.cancel')}
            </DialogButton>
            <DialogButton strong disabled={runtimeDisabled} className="text-app-danger" onClick={confirmDelete}>
              {t('pages.platformAi.deleteConfirmAction')}
            </DialogButton>
          </>
        )}
      />
    </>
  )
}

function updateOfferingInPage(page, offeringKey, changes) {
  return {
    ...page,
    connections: page.connections.map(connection => ({
      ...connection,
      offerings: connection.offerings.map(offering => offering.offeringKey === offeringKey
        ? { ...offering, ...changes }
        : offering),
    })),
  }
}
