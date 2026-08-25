import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { withGenericClick } from '../lib/soundEngine'

/** 设置面板里的插件入口，点击后直达插件市场对应插件。 */
export default function PluginSettingsLink({ pluginId }) {
  const { t } = useTranslation()
  const pluginName = t(`plugins.${pluginId}.name`)

  return (
    <Link
      to={`/marketplace?plugin=${encodeURIComponent(pluginId)}`}
      onClick={withGenericClick()}
      className="inline-flex shrink-0 items-center rounded-full bg-app-fill-secondary px-2.5 py-1 text-xs font-medium text-app-label-secondary transition-colors hover:bg-app-selected focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus"
    >
      {pluginName}
    </Link>
  )
}
