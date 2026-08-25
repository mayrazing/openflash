import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, Card, Segmented, SegmentedButton } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import { getPluginCatalog, getAllDecks, getInstalledPlugins, savePluginInstall } from '../db/database'
import PluginInstallDialog from '../components/PluginInstallDialog'

/** 解析目录条目的 config JSON（desc/icon/category），失败时返回空对象。 */
function parseConfig(config) {
  try { return config ? JSON.parse(config) : {} } catch { return {} }
}

/** 插件市场页：两栏（全部/已安装），按卡包安装/卸载插件。 */
export default function Marketplace() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [tab, setTab] = useState('all')
  const [catalog, setCatalog] = useState([])
  const [decks, setDecks] = useState([])
  const [installMap, setInstallMap] = useState({}) // pluginId -> deckId[]
  const [dialogPlugin, setDialogPlugin] = useState(null)
  const [disabled, setDisabled] = useState(false) // 市场总开关关闭（后端 50301）时置 true
  const openedTargetRef = useRef(null)

  async function reload() {
    try {
      const [cat, allDecks] = await Promise.all([getPluginCatalog(), getAllDecks()])
      // 逐卡包查已装并按插件聚合（卡包数通常很小）。
      const perDeck = await Promise.all(allDecks.map(d => getInstalledPlugins(d.id)))
      const map = {}
      allDecks.forEach((d, i) => perDeck[i].forEach(pid => { (map[pid] ||= []).push(d.id) }))
      // 先写入已安装关系，再显示插件；定向打开弹窗时预勾数据才是完整的。
      setInstallMap(map)
      setDecks(allDecks)
      setCatalog(cat)
    } catch (err) {
      // 市场被全局关闭：后端返回 FEATURE_DISABLED(50301)，前台明确提示「功能已关闭」而非空白/报错。
      if (err?.code === 50301) setDisabled(true)
      else throw err
    }
  }

  useEffect(() => { reload() }, [])

  const installedPlugins = useMemo(
    () => catalog.filter(p => (installMap[p.pluginId] || []).length > 0),
    [catalog, installMap],
  )
  const shown = tab === 'all' ? catalog : installedPlugins

  useEffect(() => {
    const targetPluginId = searchParams.get('plugin')
    if (!targetPluginId || openedTargetRef.current === targetPluginId) return
    const targetPlugin = catalog.find(plugin => plugin.pluginId === targetPluginId)
    if (!targetPlugin) return
    openedTargetRef.current = targetPluginId
    setTab('all')
    setDialogPlugin(targetPlugin)
  }, [catalog, searchParams])

  function closePluginDialog() {
    setDialogPlugin(null)
    openedTargetRef.current = null
    if (searchParams.has('plugin')) navigate('/marketplace', { replace: true })
  }

  async function handleConfirm(installDeckIds, uninstallDeckIds) {
    await savePluginInstall(dialogPlugin.pluginId, installDeckIds, uninstallDeckIds)
    closePluginDialog()
    reload()
  }

  const backLabel = t('common.back').replace(/^←\s*/, '')

  return (
    <AppPage contentClassName="!pt-0">
      <AppNavbar
        title={<h1>{t('marketplace.title')}</h1>}
        left={(
          <NavbarBackLink
            showText
            text={backLabel}
            onClick={() => navigate(-1)}
          />
        )}
      />

      {disabled ? (
        <Card raised outline className="!mx-4 !mt-3 text-center text-app-label-tertiary">
          {t('marketplace.disabled')}
        </Card>
      ) : (
      <>
      <div className="mx-4 mb-4 mt-3">
        <Segmented strong rounded role="tablist">
          <SegmentedButton
            active={tab === 'all'}
            role="tab"
            aria-selected={tab === 'all'}
            onClick={() => setTab('all')}
          >
            {t('marketplace.tabAll')}
          </SegmentedButton>
          <SegmentedButton
            active={tab === 'installed'}
            role="tab"
            aria-selected={tab === 'installed'}
            onClick={() => setTab('installed')}
          >
            {t('marketplace.tabInstalled')}
          </SegmentedButton>
        </Segmented>
      </div>

      <div className="space-y-3 px-4">
        {shown.map(p => {
          const cfg = parseConfig(p.config)
          const count = (installMap[p.pluginId] || []).length
          return (
            <Card key={p.pluginId} raised outline className="!m-0">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-app-fill-secondary text-xl">
                  {cfg.icon || '🧩'}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="font-semibold">{t(`plugins.${p.pluginId}.name`, { defaultValue: p.name })}</div>
                  <div className="mt-0.5 text-sm text-app-label-secondary">{t(`plugins.${p.pluginId}.desc`, { defaultValue: cfg.desc })}</div>
                  <div className="mt-1 text-xs text-app-label-tertiary">{count > 0 ? t('marketplace.installedOnDecks', { count }) : t('marketplace.notInstalled')}</div>
                </div>
                <Button inline small rounded tonal={count > 0} onClick={() => setDialogPlugin(p)} className="app-primary-fill shrink-0">
                  {count > 0 ? t('marketplace.manage') : t('marketplace.install')}
                </Button>
              </div>
            </Card>
          )
        })}
      </div>

      {dialogPlugin && (
        <PluginInstallDialog
          plugin={dialogPlugin}
          decks={decks}
          preinstalledDeckIds={installMap[dialogPlugin.pluginId] || []}
          onConfirm={handleConfirm}
          onCancel={closePluginDialog}
        />
      )}
      </>
      )}
    </AppPage>
  )
}
