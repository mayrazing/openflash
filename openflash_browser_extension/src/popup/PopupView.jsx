import { useState } from 'react'
import { App } from 'konsta/react'
import { buildDeckRows } from '../defaultDeckState.js'
import { createServiceUrlHandlers } from './serviceUrlHandlers.js'
import { createDeckActionsClick } from './deckActions.js'

function StatusText({ children, level = 'status' }) {
  if (!children) return null
  return (
    <p className={`helper${level === 'alert' ? ' alert' : ''}`} role={level}>
      {children}
    </p>
  )
}

function PanelHeading({ action, children }) {
  return (
    <div className="panel-heading">
      <h2>{children}</h2>
      {action}
    </div>
  )
}

function ServiceUrlCard({ serviceUrl, actions, t, loggedIn }) {
  const serviceUrlHandlers = createServiceUrlHandlers(actions.setServiceUrl)
  return (
    <section className="panel">
      <div className="panel-body">
        <label className="field-label" htmlFor="serviceUrl">
          {t('popup.serviceUrl')}
        </label>
        <input
          aria-label={t('popup.serviceUrl')}
          className="input"
          id="serviceUrl"
          {...serviceUrlHandlers}
          value={serviceUrl}
        />
        {!loggedIn && (
          <div className="auth-actions">
            <button className="primary-button" onClick={actions.login} type="button">
              {t('popup.login')}
            </button>
            <button className="text-button" onClick={actions.refresh} type="button">
              {t('popup.refresh')}
            </button>
          </div>
        )}
      </div>
    </section>
  )
}

function DeckRow({ actions, deck, t }) {
  return (
    <article
      className={`deck-row${deck.selected ? ' selected' : ''}`}
      data-testid={`deck-row-${deck.id}`}
    >
      <button
        aria-current={deck.selected ? 'true' : undefined}
        className="deck-main"
        data-testid={`deck-main-${deck.id}`}
        onClick={() => actions.selectDeck(deck.id)}
        type="button"
      >
        <span className="deck-name" title={deck.name}>{deck.name}</span>
        {deck.selected && (
          <span className="selected-badge">
            <span aria-hidden="true">✓</span>
            {t('popup.selectedDeck')}
          </span>
        )}
      </button>
      <div
        className="deck-actions"
        data-testid={`deck-actions-${deck.id}`}
        onClick={createDeckActionsClick(actions.selectDeck, deck.id)}
      >
        <button
          className="text-button"
          onClick={() => actions.toggleDefaultDeck(deck.id)}
          type="button"
        >
          {deck.defaultDeck ? t('popup.unsetDefaultDeck') : t('popup.setDefaultDeck')}
        </button>
        <button
          className="text-button danger"
          onClick={() => actions.requestDeleteDeck(deck.id)}
          type="button"
        >
          {t('popup.deleteDeck')}
        </button>
      </div>
    </article>
  )
}

function DeckCard({ state, actions, t }) {
  const [newDeckName, setNewDeckName] = useState('')
  const deckRows = buildDeckRows(state.decks, state.selectedDeckId, state.defaultDeckId)

  async function submitDeck(event) {
    event.preventDefault()
    if (!newDeckName.trim()) return
    await actions.createDeck(newDeckName)
    setNewDeckName('')
  }

  return (
    <section className="panel">
      <div className="panel-body">
        <PanelHeading>{t('popup.decksTitle')}</PanelHeading>
        <form className="create-row" onSubmit={submitDeck}>
          <input
            aria-label={t('popup.deckName')}
            className="input"
            id="newDeckName"
            onChange={(event) => setNewDeckName(event.currentTarget.value)}
            placeholder={t('popup.deckName')}
            value={newDeckName}
          />
          <button className="primary-button" type="submit">
            {t('popup.createDeck')}
          </button>
        </form>

        <div className="deck-list">
          {deckRows.map((deck) => (
            <DeckRow actions={actions} deck={deck} key={deck.id} t={t} />
          ))}
        </div>

        <StatusText>
          {state.defaultDeckId ? t('popup.defaultDeckSet') : t('popup.defaultDeckMissing')}
        </StatusText>
      </div>
    </section>
  )
}

function ShortcutCard({ actions, shortcutText, t }) {
  return (
    <section className="panel" aria-labelledby="shortcut-title">
      <div className="panel-body">
        <PanelHeading
          action={(
            <button className="text-button" onClick={actions.openShortcutSettings} type="button">
              {t('popup.shortcutSettingsAction')}
            </button>
          )}
        >
          <span id="shortcut-title">{t('popup.shortcutsTitle')}</span>
        </PanelHeading>
      </div>
      <div className="list-row">
        <span className="list-title">{t('popup.shortcutImportDefault')}</span>
        <kbd>{shortcutText('openflash-import-default')}</kbd>
      </div>
      <div className="list-row">
        <span className="list-title">{t('popup.shortcutManualCard')}</span>
        <span className="status">{shortcutText('openflash-manual-card')}</span>
      </div>
    </section>
  )
}

function AiSettingsCard({ state, actions, t }) {
  const selectedDeck = state.decks.find((deck) => String(deck.id) === String(state.selectedDeckId))
  const title = selectedDeck
    ? t('popup.aiPromptTitleWithDeck', {
      title: t('popup.aiPromptTitle'),
      deckName: selectedDeck.name,
    })
    : t('popup.aiPromptTitle')
  const disabled = Boolean(state.aiSettingsError)
  const enabled = Boolean(state.aiSettings.aiCompletionEnabled)

  return (
    <section className="panel" aria-labelledby="ai-title">
      <div className="panel-body">
        <PanelHeading
          action={disabled ? (
            <span className="status" role="alert" title={state.aiSettingsError}>
              {t('popup.aiPluginDisabled')}
            </span>
          ) : null}
        >
          <span id="ai-title">{title}</span>
        </PanelHeading>
        <label className="field-label" htmlFor="aiCompletionPrompt">
          {t('popup.aiCompletionPrompt')}
        </label>
        <textarea
          aria-label={t('popup.aiCompletionPrompt')}
          className="textarea"
          disabled={disabled}
          id="aiCompletionPrompt"
          onChange={(event) => actions.setAiCompletionPrompt(event.currentTarget.value)}
          placeholder={t('popup.aiCompletionPlaceholder')}
          value={state.aiSettings.aiCompletionPrompt || ''}
        />
      </div>
      <div className="list-row">
        <span className="list-title">{t('popup.aiCompletionEnabled')}</span>
        <button
          aria-label={t('popup.aiCompletionEnabled')}
          aria-pressed={enabled}
          className="toggle"
          disabled={disabled}
          onClick={() => actions.setAiCompletionEnabled(!enabled)}
          type="button"
        />
      </div>
      <div className="panel-body">
        <button className="full-button" disabled={disabled} onClick={actions.saveAiSettings} type="button">
          {t('popup.saveAi')}
        </button>
        <StatusText>{state.message}</StatusText>
        <StatusText level="alert">{state.error}</StatusText>
      </div>
    </section>
  )
}

// 删除卡包不可撤销, 点删除后先渲染这层确认框, 只有点框内的删除才真的发请求。
function DeleteDeckDialog({ actions, deck, t }) {
  return (
    <div className="dialog-backdrop">
      <div aria-labelledby="delete-deck-title" className="dialog" role="alertdialog">
        <h2 className="dialog-title" id="delete-deck-title">{t('popup.deleteDeckConfirmTitle')}</h2>
        <p className="dialog-body">{t('popup.deleteDeckConfirmBody', { deckName: deck.name })}</p>
        <div className="dialog-actions">
          <button className="text-button" onClick={actions.cancelDeleteDeck} type="button">
            {t('popup.cancel')}
          </button>
          <button className="text-button danger" onClick={actions.confirmDeleteDeck} type="button">
            {t('popup.deleteDeck')}
          </button>
        </div>
      </div>
    </div>
  )
}

function PopupHeader({ accountName, actions, loggedIn, t }) {
  return (
    <header className="navbar" data-testid="popup-header">
      <div className="identity" data-testid="popup-identity">
        <h1>{t('popup.title')}</h1>
        {loggedIn && (
          <span className="account" aria-label={accountName} title={accountName}>
            {accountName}
          </span>
        )}
      </div>
      {loggedIn && (
        <button className="text-button" onClick={actions.logout} type="button">
          {t('popup.logout')}
        </button>
      )}
    </header>
  )
}

export default function PopupView({ state, actions, t, shortcutText }) {
  const accountName = state.user?.nickname || state.user?.username || ''
  const viewState = {
    ...state,
    decks: state.decks || [],
    aiSettings: state.aiSettings || {},
  }
  const loggedIn = Boolean(state.user)
  // 待删卡包可能已被刷新掉, 找不到就不弹框, 免得弹出一个没名字的确认框。
  const pendingDeleteDeck = viewState.decks.find(
    (deck) => String(deck.id) === String(state.pendingDeleteDeckId),
  )

  return (
    <App className="popup" dark safeAreas={false} style={{ width: '380px' }} theme="ios">
      <PopupHeader accountName={accountName} actions={actions} loggedIn={loggedIn} t={t} />
      <main className="content">
        <ServiceUrlCard
          actions={actions}
          loggedIn={loggedIn}
          serviceUrl={state.serviceUrl || ''}
          t={t}
        />
        <StatusText level={state.lastImportStatus?.level === 'success' ? 'status' : 'alert'}>
          {state.lastImportStatus?.message}
        </StatusText>
        {loggedIn ? (
          <>
            <DeckCard actions={actions} state={viewState} t={t} />
            <ShortcutCard actions={actions} shortcutText={shortcutText} t={t} />
            <AiSettingsCard actions={actions} state={viewState} t={t} />
          </>
        ) : (
          <StatusText level="alert">{state.error}</StatusText>
        )}
      </main>
      {pendingDeleteDeck && (
        <DeleteDeckDialog actions={actions} deck={pendingDeleteDeck} t={t} />
      )}
    </App>
  )
}
