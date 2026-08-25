import { useTranslation } from 'react-i18next'
import { Button } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'
import { formatStatCount } from '../lib/deckCardUtils.js'

/**
 * 卡片状态筛选栏（新/学习/明天/今天）+ backlog/highPressure 提示。
 */
export default function DeckFilterBar({ stats, filter, onFilterChange }) {
  const { t } = useTranslation()

  return (
    <>
      <div className={`flex gap-2 ${stats.backlog > 0 || stats.newPaused ? 'mb-2' : 'mb-4'}`}>
        {[
          { key: 'new', label: t('deckDetail.filterNew'), count: stats.new },
          { key: 'learning', label: t('deckDetail.filterLearning'), count: stats.learning },
          { key: 'tomorrow', label: t('deckDetail.filterTomorrow'), count: stats.tomorrow },
          { key: 'today', label: t('deckDetail.filterToday'), count: stats.today },
        ].map(({ key, label, count }) => (
          <Button
            tonal
            rounded
            key={key}
            onClick={withGenericClick(() => onFilterChange(key))}
            className={`!h-auto flex-1 flex-col px-3 py-2 text-center shadow-md ${filter === key ? 'bg-app-selected text-app-accent' : 'bg-app-fill-secondary text-app-label-secondary'}`}
          >
            <div className="text-lg font-bold">{formatStatCount(count)}</div>
            <div className="text-xs">{label}</div>
          </Button>
        ))}
      </div>
      {(stats.backlog > 0 || stats.newPaused) && (
        <div className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1">
          {stats.backlog > 0 && (
            <span className="text-xs text-app-warning">
              {t('summary.backlog', { count: formatStatCount(stats.backlog) })}
            </span>
          )}
          {stats.newPaused && (
            <span className="text-xs text-app-danger">
              {t('summary.highPressure')}
            </span>
          )}
        </div>
      )}
    </>
  )
}
