import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Dialog,
  DialogButton,
  List,
  ListItem,
  Preloader,
  Segmented,
  SegmentedButton,
  Toggle,
} from 'konsta/react'
import { useTranslation } from 'react-i18next'
import AppListInput from '../components/AppListInput.jsx'
import {
  deleteUser,
  listUsers,
  updateUserBanned,
  updateUserCliAccess,
  updateUserOfferingAccess,
  updateUserRole,
} from './api.js'
import { createInitialUsersState, createUsersCoordinator } from './state.js'

function accountMutationErrorKey(error) {
  if (error.code === 40901) return 'pages.users.lastAdminError'
  if (error.code === 40902) return 'pages.users.selfAccountMutationError'
  if (error.code === 50301) return 'pages.users.userServiceUnavailableError'
  return 'pages.users.accountActionError'
}

export default function UsersPage({ currentAdminId }) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [usersState, setUsersState] = useState(createInitialUsersState)
  const [coordinator] = useState(() => createUsersCoordinator(setUsersState))
  const [viewState, setViewState] = useState('loading')
  const [reloadVersion, setReloadVersion] = useState(0)
  const [mutationErrorKey, setMutationErrorKey] = useState(null)
  const [clis, setClis] = useState([])
  const [offerings, setOfferings] = useState([])
  const [runtimeAvailable, setRuntimeAvailable] = useState(true)
  const [managedUserId, setManagedUserId] = useState(null)
  const [deleteTargetId, setDeleteTargetId] = useState(null)
  const { pendingByUserId, users } = usersState

  useEffect(() => {
    let active = true
    const request = coordinator.startListRequest()
    setViewState('loading')

    listUsers(query)
      .then(result => {
        if (!active) return
        if (request.succeed(result.users)) {
          setClis(Array.isArray(result.clis) ? result.clis : [])
          setOfferings(Array.isArray(result.offerings) ? result.offerings : [])
          setRuntimeAvailable(result.runtimeAvailable !== false)
          setViewState('ready')
        }
      })
      .catch(() => {
        if (active && request.fail()) setViewState('error')
      })

    return () => {
      active = false
    }
  }, [coordinator, query, reloadVersion])

  async function changeRole(userId, role) {
    const mutation = coordinator.startMutation(userId, 'role', role)
    if (!mutation) return

    setMutationErrorKey(null)
    const result = await mutation.run(() => updateUserRole(userId, role))
    if (result.status === 'failed') {
      setMutationErrorKey(accountMutationErrorKey(result.error))
    }
  }

  async function changeCliAccess(userId, cliKey, enabled) {
    const mutation = coordinator.startMutation(userId, 'cliAccess', enabled, cliKey)
    if (!mutation) return

    setMutationErrorKey(null)
    const result = await mutation.run(() => updateUserCliAccess(userId, cliKey, enabled))
    if (result.status === 'failed') {
      setMutationErrorKey('pages.users.updateError')
    }
  }

  async function changeOfferingAccess(userId, offeringKey, enabled) {
    const mutation = coordinator.startMutation(userId, 'offeringAccess', enabled, offeringKey)
    if (!mutation) return

    setMutationErrorKey(null)
    const result = await mutation.run(() => updateUserOfferingAccess(userId, offeringKey, enabled))
    if (result.status === 'failed') {
      setMutationErrorKey('pages.users.updateError')
    }
  }

  async function changeBanned(userId, banned) {
    const mutation = coordinator.startMutation(userId, 'banned', banned)
    if (!mutation) return

    setMutationErrorKey(null)
    const result = await mutation.run(() => updateUserBanned(userId, banned))
    if (result.status === 'failed') {
      setMutationErrorKey(accountMutationErrorKey(result.error))
    }
  }

  async function confirmDelete() {
    if (!deleteTargetId) return
    const mutation = coordinator.startMutation(deleteTargetId, 'deleted', true)
    if (!mutation) return

    setMutationErrorKey(null)
    const result = await mutation.run(() => deleteUser(deleteTargetId))
    if (result.status === 'succeeded') {
      setDeleteTargetId(null)
    } else {
      setMutationErrorKey(accountMutationErrorKey(result.error))
    }
  }

  const managedUser = users.find(user => user.id === managedUserId) ?? null
  const deleteTarget = users.find(user => user.id === deleteTargetId) ?? null
  const deleteSaving = Boolean(deleteTargetId
    && pendingByUserId[String(deleteTargetId)])
  const visibleUsers = users.filter(user => !user.deleted)
  const accessItems = offerings.filter(offering => offering.source === 'PLATFORM')

  return (
    <>
      <div className="mb-4 px-1">
        <h2 className="text-2xl font-bold text-app-label-primary">{t('pages.users.title')}</h2>
        <p className="mt-2 text-sm text-app-label-tertiary">{t('pages.users.description')}</p>
      </div>

      <List inset strong outline className="!mb-4 !mt-0">
        <AppListInput
          label={t('pages.users.searchLabel')}
          placeholder={t('pages.users.searchPlaceholder')}
          value={query}
          onChange={event => setQuery(event.target.value)}
        />
      </List>

      {!runtimeAvailable && (
        <Card raised outline className="!mb-4 !mt-0" role="status">
          <p className="font-semibold text-app-warning">{t('pages.users.runtimeUnavailable')}</p>
          <p className="mt-2 text-sm text-app-label-secondary">
            {t('pages.users.runtimeUnavailableDescription')}
          </p>
        </Card>
      )}

      {mutationErrorKey && (
        <Card raised outline className="!mb-4 !mt-0" role="alert">
          <p className="text-sm font-semibold text-app-danger">{t(mutationErrorKey)}</p>
        </Card>
      )}

      {viewState === 'loading' && (
        <Card raised outline className="!m-0 text-center">
          <Preloader className="mx-auto" />
          <p className="mt-3 text-sm text-app-label-secondary">{t('pages.users.loading')}</p>
        </Card>
      )}

      {viewState === 'error' && (
        <Card raised outline className="!m-0">
          <p className="text-sm font-semibold text-app-danger">{t('pages.users.loadError')}</p>
          <p className="mt-2 text-sm text-app-label-secondary">{t('pages.users.loadErrorDescription')}</p>
          <Button className="mt-4" rounded outline onClick={() => setReloadVersion(current => current + 1)}>
            {t('pages.users.retry')}
          </Button>
        </Card>
      )}

      {viewState === 'ready' && visibleUsers.length === 0 && (
        <Card raised outline className="!m-0 text-center">
          <p className="font-semibold text-app-label-primary">{t('pages.users.emptyTitle')}</p>
          <p className="mt-2 text-sm text-app-label-secondary">{t('pages.users.emptyDescription')}</p>
        </Card>
      )}

      {viewState === 'ready' && visibleUsers.length > 0 && (
        <List inset strong outline className="!mb-5 !mt-0">
          {visibleUsers.map(user => {
            const isSaving = Boolean(pendingByUserId[String(user.id)])
            const isSelf = currentAdminId === user.id
            return (
              <ListItem
                key={user.id}
                title={user.nickname || user.username}
                subtitle={`@${user.username}`}
                after={(
                  <span className="flex flex-wrap justify-end gap-2">
                    <span className={`inline-flex rounded-full bg-app-surface-secondary px-2.5 py-1 text-xs font-semibold ${
                      user.role === 'ADMIN' ? 'text-app-accent' : 'text-app-label-secondary'
                    }`}>
                      {user.role}
                    </span>
                    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${user.banned
                      ? 'bg-app-danger-tonal text-app-danger'
                      : 'bg-app-success-tonal text-app-success'}`}
                    >
                      {t(user.banned ? 'pages.users.bannedStatus' : 'pages.users.activeStatus')}
                    </span>
                  </span>
                )}
                innerChildren={(
                  <div className="admin-responsive-grid admin-responsive-grid--user-controls">
                    <div>
                      <p className="mb-2 text-xs font-semibold text-app-label-secondary">
                        {t('pages.users.roleLabel')}
                      </p>
                      <Segmented strong rounded>
                        <SegmentedButton
                          active={user.role === 'USER'}
                          disabled={isSaving}
                          aria-label={t('pages.users.roleAria', { username: user.username, role: 'USER' })}
                          onClick={() => changeRole(user.id, 'USER')}
                        >USER</SegmentedButton>
                        <SegmentedButton
                          active={user.role === 'ADMIN'}
                          disabled={isSaving}
                          aria-label={t('pages.users.roleAria', { username: user.username, role: 'ADMIN' })}
                          onClick={() => changeRole(user.id, 'ADMIN')}
                        >ADMIN</SegmentedButton>
                      </Segmented>
                    </div>
                    <div className="flex items-center justify-between gap-4">
                      <span>
                        <span className="block text-sm font-medium text-app-label-primary">
                          {t('pages.users.cliAccess')}
                        </span>
                        <span className="mt-1 block text-xs text-app-label-tertiary">
                          {isSaving
                            ? t('pages.users.saving')
                            : t('pages.users.cliAccessSummary', {
                                allowed: accessItems.filter(offering => offering.offeringKey
                                  ? user.offeringAccess?.[offering.offeringKey]
                                  : user.cliAccess?.[offering.cliKey]).length,
                                total: accessItems.length,
                              })}
                        </span>
                      </span>
                      <Button
                        rounded
                        outline
                        disabled={isSaving}
                        onClick={() => setManagedUserId(user.id)}
                      >
                        {t('pages.users.manageCliAccess')}
                      </Button>
                    </div>
                    <div className="flex flex-wrap items-center justify-end gap-3">
                      <Button
                        rounded
                        outline
                        disabled={isSaving || isSelf}
                        title={isSelf ? t('pages.users.selfAccountActionDisabled') : undefined}
                        onClick={() => changeBanned(user.id, !user.banned)}
                      >
                        {t(user.banned ? 'pages.users.unbanUser' : 'pages.users.banUser')}
                      </Button>
                      <Button
                        rounded
                        outline
                        disabled={isSaving || isSelf}
                        className="text-app-danger"
                        colors={{
                          textIos: 'text-app-danger',
                          textMaterial: 'text-app-danger',
                          outlineBorderIos: 'border-app-danger',
                          outlineBorderMaterial: 'border-app-danger',
                        }}
                        title={isSelf ? t('pages.users.selfAccountActionDisabled') : undefined}
                        onClick={() => setDeleteTargetId(user.id)}
                      >
                        {t('pages.users.deleteUser')}
                      </Button>
                      {isSelf && (
                        <p className="basis-full text-right text-xs text-app-label-tertiary">
                          {t('pages.users.selfAccountActionDisabled')}
                        </p>
                      )}
                    </div>
                  </div>
                )}
              />
            )
          })}
        </List>
      )}

      {managedUser && (
        <Dialog
          opened
          role="dialog"
          aria-modal="true"
          onBackdropClick={() => setManagedUserId(null)}
          title={t('pages.users.cliDialogTitle', {
            user: managedUser.nickname || managedUser.username,
          })}
          content={(
            <div className="divide-y divide-app-separator">
              {accessItems.map(offering => {
                const cli = offering.cliKey
                  ? clis.find(item => item.cliKey === offering.cliKey)
                  : null
                const enabled = Boolean(offering.offeringKey
                  ? managedUser.offeringAccess?.[offering.offeringKey]
                  : managedUser.cliAccess?.[offering.cliKey])
                const available = runtimeAvailable && (!cli || cli.runtimeStatus === 'AVAILABLE')
                const isSaving = Boolean(pendingByUserId[String(managedUser.id)])
                const displayName = offering.cliKey
                  ? t(`pages.users.cliNames.${offering.cliKey}`, { defaultValue: offering.cliKey })
                  : offering.modelKey
                return (
                  <label
                    key={offering.offeringKey ?? offering.cliKey}
                    className="flex items-center justify-between gap-4 py-3"
                  >
                    <span>
                      <span className="block font-semibold text-app-label-primary">
                        {displayName}
                      </span>
                      <span className="mt-1 block text-xs text-app-accent">
                        {t('pages.users.platformProvided')}
                      </span>
                      {cli && (
                        <span className={`mt-1 block text-xs ${available
                          ? 'text-app-success'
                          : 'text-app-label-tertiary'}`}
                        >
                          {t(`pages.users.cliRuntime.${cli.runtimeStatus}`, {
                            defaultValue: cli.runtimeStatus,
                          })}
                        </span>
                      )}
                    </span>
                    <Toggle
                      checked={enabled}
                      disabled={isSaving || !runtimeAvailable || (!available && !enabled)}
                      aria-label={t('pages.users.cliAria', {
                        cli: displayName,
                        username: managedUser.username,
                      })}
                      onChange={event => offering.offeringKey
                        ? changeOfferingAccess(managedUser.id, offering.offeringKey, event.target.checked)
                        : changeCliAccess(managedUser.id, offering.cliKey, event.target.checked)}
                    />
                  </label>
                )
              })}
            </div>
          )}
          buttons={(
            <DialogButton onClick={() => setManagedUserId(null)}>
              {t('pages.users.closeCliDialog')}
            </DialogButton>
          )}
        />
      )}

      {deleteTarget && (
        <Dialog
          opened
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="delete-user-title"
          aria-describedby="delete-user-description"
          onBackdropClick={() => {
            if (!deleteSaving) setDeleteTargetId(null)
          }}
          title={(
            <span id="delete-user-title">{t('pages.users.deleteConfirmTitle')}</span>
          )}
          content={(
            <p id="delete-user-description">
              {t('pages.users.deleteConfirmBody', {
                user: deleteTarget.nickname || deleteTarget.username,
              })}
            </p>
          )}
          buttons={(
            <>
              <DialogButton
                disabled={deleteSaving}
                onClick={() => setDeleteTargetId(null)}
              >
                {t('pages.users.deleteCancel')}
              </DialogButton>
              <DialogButton
                strong
                disabled={deleteSaving}
                colors={{
                  fillBgIos: 'bg-app-danger-fill active:opacity-80',
                  fillBgMaterial: 'bg-app-danger-fill active:opacity-80',
                  fillTextIos: 'text-white',
                  fillTextMaterial: 'text-white',
                }}
                onClick={confirmDelete}
              >
                {t(deleteSaving ? 'pages.users.deleting' : 'pages.users.deleteConfirmAction')}
              </DialogButton>
            </>
          )}
        />
      )}
    </>
  )
}
