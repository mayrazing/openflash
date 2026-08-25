import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, DialogButton, List } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import BottomActionBar from '../components/layout/BottomActionBar'
import {
  getAllDecks, createDeck, renameDeck, deleteDeck,
  exportDecks,
} from '../db/database'
import DeckCard from '../components/DeckCard'
import AppNavbar from '../components/konsta/AppNavbar'
import ListInput from '../components/konsta/AppListInput'
import KonstaConfirmDialog from '../components/konsta/KonstaConfirmDialog'
import KonstaDialogShell from '../components/konsta/KonstaDialogShell'
import { useSideScrollHints } from '../lib/sideScrollHints'
import { withGenericClick } from '../lib/soundEngine'
import { getErrorMessage } from '../lib/errorMessages'
import { useDragSelect } from '../hooks/useDragSelect.js'
import openFlashLogo from '../assets/openflash-logo.svg'

export default function Home() {
  const { t } = useTranslation()
  const [decks, setDecks] = useState([])
  const [newName, setNewName] = useState('')
  const [creating, setCreating] = useState(false)
  const [renaming, setRenaming] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [batchDeleteConfirm, setBatchDeleteConfirm] = useState(false)
  const [importFile, setImportFile] = useState(null)
  const [importError, setImportError] = useState('')
  const fileInputRef = useRef(null)
  const navigate = useNavigate()
  const { sideScrollHandlers, renderSideHints } = useSideScrollHints({
    onTop: scrollHomeToTop,
    onBottom: scrollHomeToBottom,
  })

  /**
   * 右侧向上双箭头：把主页滚动回顶部。
   */
  function scrollHomeToTop() {
    const root = document.getElementById('root')
    if (!root) return
    root.scrollTo({ top: 0, behavior: 'smooth' })
  }

  /**
   * 右侧向下双箭头：把主页滚动到当前内容底部。
   */
  function scrollHomeToBottom() {
    const root = document.getElementById('root')
    if (!root) return
    root.scrollTo({ top: root.scrollHeight, behavior: 'smooth' })
  }

  /**
   * 加载主页卡包列表；卡片数量由后端聚合返回，避免首页拉全量卡片。
   */
  async function load() {
    const all = await getAllDecks()
    setDecks(all)
  }

  useEffect(() => {
    load()
  }, [])

  async function handleCreate() {
    if (!newName.trim()) return
    await createDeck(newName)
    setNewName(''); setCreating(false); load()
  }

  async function handleRename() {
    if (!renaming.name.trim()) return
    await renameDeck(renaming.id, renaming.name)
    setRenaming(null); load()
  }

  async function handleDelete() {
    await deleteDeck(deletingId)
    setDeletingId(null); load()
  }

  function handleImportClick() { fileInputRef.current.click() }

  function handleFileSelected(e) {
    const file = e.target.files[0]
    if (!file) return
    setImportFile({ file })
    e.target.value = ''
  }

  async function handleImportConfirm() {
    const formData = new FormData()
    formData.append('file', importFile.file)
    const response = await fetch('/api/import/deck', {
      method: 'POST',
      body: formData,
      credentials: 'include',
    })
    const text = await response.text()
    let result = null
    try { result = text ? JSON.parse(text) : null } catch { result = null }
    if (!response.ok || result?.code !== 200) {
      setImportFile(null)
      setImportError(getErrorMessage(result?.code))
      return
    }
    setImportFile(null)
    setImportError('')
    load()
  }

  const {
    isSelectMode, selectedIds, setSelectedIds,
    enterEmptySelectMode, exitSelectMode, handleToggleSelect,
    handlePointerDown, handlePointerMove, handlePointerUp,
    handlePointerCancel, handlePointerLeave,
  } = useDragSelect({ items: decks, getId: d => d.id, dataAttr: 'data-deck-id' })

  /**
   * 选择全部当前卡包。
   */
  function handleSelectAllDecks() {
    setSelectedIds(decks.map(deck => String(deck.id)))
  }

  async function handleExportSelected() {
    if (selectedIds.length === 0) return
    await exportDecks(selectedIds)
    exitSelectMode()
  }

  /**
   * 打开批量删除确认弹窗，未选中时不做任何事。
   */
  function handleBatchDelete() {
    if (selectedIds.length === 0) return
    setBatchDeleteConfirm(true)
  }

  /**
   * 确认后逐个删除选中卡包，并刷新主页列表。
   */
  async function confirmBatchDelete() {
    const idsToDelete = [...selectedIds]
    setBatchDeleteConfirm(false)
    exitSelectMode()
    const results = await Promise.allSettled(idsToDelete.map(deckId => deleteDeck(deckId)))
    if (results.some(result => result.status === 'rejected')) {
      setImportError(t('home.deleteBatchError'))
    } else {
      setImportError('')
    }
    load()
  }

  const importConfirmMessage = t('home.importConfirm')

  return (
    <div>
      <AppPage
        bottomInset={isSelectMode ? 'selection' : 'none'}
        contentClassName="!pt-0"
        style={{ touchAction: 'manipulation' }}
        {...sideScrollHandlers}
      >
      <AppNavbar
        left={<div className="flex w-16 items-center justify-center"><img className="size-10 shrink-0" src={openFlashLogo} alt="" aria-hidden="true" /></div>}
        title={<h1>{t('home.title')}</h1>}
        right={(
          <Button
            inline
            small
            clear
            rounded
            onClick={withGenericClick(isSelectMode ? exitSelectMode : () => navigate('/settings'))}
            className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"
          >
            {isSelectMode ? t('home.cancelSelect') : t('home.globalSettings')}
          </Button>
        )}
        subnavbar={!isSelectMode ? (
          <div className="grid w-full grid-cols-3 gap-2">
            <Button small tonal rounded onClick={withGenericClick(handleImportClick)}>
              {t('home.importDeck')}
            </Button>
            <Button small tonal rounded onClick={withGenericClick(enterEmptySelectMode)}>
              {t('home.exportDeck')}
            </Button>
            <Button small tonal rounded onClick={withGenericClick(() => navigate('/marketplace'))}>
              {t('home.marketplace')}
            </Button>
          </div>
        ) : null}
      />

      <input ref={fileInputRef} type="file" accept=".zip" className="hidden" onChange={handleFileSelected} />
      {importError && <p className="mb-3 px-4 text-sm text-app-danger">{importError}</p>}

      {/* 卡包列表 */}
      <div className="mb-6 space-y-3 px-4">
        {decks.length === 0 && (
          <p className="py-8 text-center text-app-label-tertiary">{t('home.noDecks')}</p>
        )}
        {decks.map((deck) => (
          <div
            key={deck.id}
            data-deck-id={deck.id}
            className="long-press-select-surface"
            onPointerDown={(e) => handlePointerDown(e, deck.id)}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerLeave={handlePointerLeave}
            onPointerCancel={handlePointerCancel}
          >
            <DeckCard
              deck={deck}
              masteredCount={Number(deck.masteredCount ?? 0)}
              totalCount={Number(deck.activeCount ?? 0) + Number(deck.masteredCount ?? 0)}
              onClick={() => navigate(`/deck/${deck.id}`)}
              onRename={() => setRenaming({ id: deck.id, name: deck.name })}
              onDelete={() => setDeletingId(deck.id)}
              isSelectMode={isSelectMode}
              selected={selectedIds.includes(String(deck.id))}
              onToggleSelect={() => handleToggleSelect(deck.id)}
            />
          </div>
        ))}
      </div>

      {/* 新建卡包 */}
      {!isSelectMode && (
        creating ? (
          <div className="px-4">
            <List inset strong className="!mx-0 !my-0">
              <ListInput
                outline
                autoFocus
                placeholder={t('home.deckNamePlaceholder')}
                value={newName}
                onChange={(event) => setNewName(event.target.value)}
                onKeyDown={(event) => event.key === 'Enter' && handleCreate()}
              />
            </List>
            <div className="mt-3 grid grid-cols-2 gap-3">
              <Button large rounded className="app-primary-fill" onClick={withGenericClick(handleCreate)}>{t('home.createConfirm')}</Button>
              <Button
                large
                tonal
                rounded
                onClick={withGenericClick(() => { setCreating(false); setNewName('') })}
              >
                {t('common.cancel')}
              </Button>
            </div>
          </div>
        ) : (
          <div className="px-4">
            <Button
              large
              outline
              rounded
              onClick={withGenericClick(() => setCreating(true))}
              className="border-dashed"
            >
              {t('home.createDeck')}
            </Button>
          </div>
        )
      )}

      {/* 重命名弹窗 */}
      <KonstaDialogShell
        open={!!renaming}
        title={t('home.renameDeck')}
        onClose={() => setRenaming(null)}
        buttons={(
          <>
            <DialogButton onClick={withGenericClick(() => setRenaming(null))}>{t('common.cancel')}</DialogButton>
            <DialogButton strong onClick={withGenericClick(handleRename)}>{t('common.confirm')}</DialogButton>
          </>
        )}
      >
        {renaming && (
          <List className="!my-0">
            <ListInput
              outline
              autoFocus
              value={renaming.name}
              onChange={(event) => setRenaming({ ...renaming, name: event.target.value })}
              onKeyDown={(event) => event.key === 'Enter' && handleRename()}
            />
          </List>
        )}
      </KonstaDialogShell>

      <KonstaConfirmDialog
        open={!!deletingId}
        message={t('home.deleteDeckConfirm')}
        onConfirm={handleDelete} onCancel={() => setDeletingId(null)}
      />
      <KonstaConfirmDialog
        open={batchDeleteConfirm}
        message={t('home.deleteBatchConfirm', { count: selectedIds.length })}
        onConfirm={confirmBatchDelete}
        onCancel={() => setBatchDeleteConfirm(false)}
      />
      <KonstaConfirmDialog
        open={!!importFile}
        message={importConfirmMessage}
        destructive={false}
        confirmText={t('common.confirm')}
        onConfirm={handleImportConfirm} onCancel={() => setImportFile(null)}
      />
      </AppPage>
      {renderSideHints()}
      {isSelectMode && (
        <BottomActionBar innerClassName="flex items-center gap-2">
            <Button
              inline
              small
              tonal
              rounded
              onClick={withGenericClick(handleSelectAllDecks)}
              disabled={decks.length === 0}
              className="shrink-0"
            >
              {t('home.selectAll')}
            </Button>
            <div className="flex-1 text-center text-sm text-app-label-primary">
              {t('home.selectedDecks', { count: selectedIds.length })}
            </div>
            <Button
              inline
              small
              rounded
              onClick={withGenericClick(handleExportSelected)}
              disabled={selectedIds.length === 0}
              className="app-primary-fill"
            >
              {t('home.export')}
            </Button>
            <Button
              inline
              small
              rounded
              onClick={withGenericClick(handleBatchDelete)}
              disabled={selectedIds.length === 0}
              className="bg-app-danger-fill hover:bg-app-danger-hover active:bg-app-danger-pressed text-app-on-danger disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
            >
              {t('cardItem.delete')}
            </Button>
            <Button
              inline
              small
              tonal
              rounded
              onClick={withGenericClick(exitSelectMode)}
            >
              {t('common.cancel')}
            </Button>
        </BottomActionBar>
      )}
    </div>
  )
}
