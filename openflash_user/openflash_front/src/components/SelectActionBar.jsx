import { useTranslation } from 'react-i18next'
import { Button, List, ListButton } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'
import BottomActionBar from './layout/BottomActionBar'

/**
 * 详情页底部多选操作栏。isSelectMode=false 时不渲染。
 * 含全选下拉菜单（当前页/全部）、批量迁移、批量重置、批量删除、取消。
 */
export default function SelectActionBar({
  isSelectMode,
  selectedIds,
  selectMenuOpen,
  onToggleMenu,
  onSelectAllCurrent,
  onSelectAllLoad,
  onBatchMove,
  onBatchReset,
  onBatchDelete,
  onCancel,
}) {
  const { t } = useTranslation()
  if (!isSelectMode) return null

  return (
    <BottomActionBar innerClassName="flex items-center gap-2">
        <div className="relative shrink-0">
          <Button
            small
            tonal
            rounded
            inline
            onClick={withGenericClick((e) => {
              e.stopPropagation()
              onToggleMenu(open => !open)
            })}
            className="shrink-0"
          >
            {t('deckDetail.selectAllToggle')}
          </Button>
          {selectMenuOpen && (
            <List inset strong outline className="absolute bottom-10 left-0 !m-0 w-40 shadow-xl">
              <ListButton
                onClick={withGenericClick((e) => {
                  e.stopPropagation()
                  onSelectAllCurrent()
                })}
              >
                {t('deckDetail.selectAllCurrent')}
              </ListButton>
              <ListButton
                onClick={withGenericClick((e) => {
                  e.stopPropagation()
                  void onSelectAllLoad()
                  onToggleMenu(false)
                })}
              >
                {t('deckDetail.selectAllAll')}
              </ListButton>
            </List>
          )}
        </div>
        <div className="ml-auto flex min-w-0 items-center gap-2 overflow-x-auto whitespace-nowrap">
          <div className="shrink-0 text-center text-sm text-app-label-primary">
            {t('deckDetail.selectedCards', { count: selectedIds.length })}
          </div>
          <Button
            inline
            small
            rounded
            onClick={withGenericClick(onBatchMove)}
            disabled={selectedIds.length === 0}
            className="app-primary-fill shrink-0"
          >
            {t('deckDetail.moveCards')}
          </Button>
          <Button
            inline
            small
            rounded
            onClick={withGenericClick(onBatchReset)}
            disabled={selectedIds.length === 0}
            className="shrink-0 bg-app-warning-fill hover:bg-app-warning-hover active:bg-app-warning-pressed text-app-on-warning disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
          >
            {t('cardItem.reset')}
          </Button>
          <Button
            inline
            small
            rounded
            onClick={withGenericClick(onBatchDelete)}
            disabled={selectedIds.length === 0}
            className="shrink-0 bg-app-danger-fill hover:bg-app-danger-hover active:bg-app-danger-pressed text-app-on-danger disabled:bg-app-disabled-fill disabled:text-app-disabled-label"
          >
            {t('cardItem.delete')}
          </Button>
          <Button
            inline
            small
            tonal
            rounded
            onClick={withGenericClick(onCancel)}
            className="shrink-0"
          >
            {t('common.cancel')}
          </Button>
        </div>
    </BottomActionBar>
  )
}
