import { Button, Card } from 'konsta/react'
import { practiceModeLabel, TODAY_REPRACTICE_MODE } from '../../lib/practiceQueue'
import AppNavbar from '../konsta/AppNavbar'
import NavbarBackLink from '../konsta/AppNavbarBackLink'

export default function PracticeModeSelect({
  availableModes,
  resumePrompt,
  todayRepracticeCards,
  onBack,
  onResume,
  onStartFresh,
  onStartTodayRepractice,
  t,
}) {
  const savedMode = resumePrompt?.payload?.mode ?? null
  const savedModeLabel = savedMode ? practiceModeLabel(savedMode, t, availableModes) : null
  const savedTodayRepractice = savedMode === TODAY_REPRACTICE_MODE
  const savedModeIsAvailable = savedMode
    ? availableModes.some(m => m.value === savedMode)
    : false
  const backLabel = t('common.back').replace(/^←\s*/, '')

  return (
    <div className="h-full overflow-y-auto bg-app-background">
      <div className="mx-auto min-h-full max-w-lg">
        <AppNavbar
          title={<h1>{t('practice.selectMode')}</h1>}
          left={<NavbarBackLink showText text={backLabel} onClick={onBack} />}
        />

        <div className="px-4 pb-10 pt-3">
          <Card raised outline className="!m-0" contentWrapPadding="p-4">
            <p className="mb-5 text-center text-sm text-app-label-secondary">{t('practice.selectModeDesc')}</p>
            <div className="space-y-3">
              {availableModes.map((m) => {
                const isSaved = savedModeIsAvailable && m.value === savedMode
                const label = practiceModeLabel(m.value, t, availableModes)
                return (
                  <Button
                    key={m.value}
                    large
                    rounded
                    tonal={!isSaved}
                    raised={isSaved}
                    className={isSaved ? 'app-primary-fill' : 'text-app-accent'}
                    onClick={isSaved ? onResume : () => onStartFresh(m.value)}
                  >
                    {isSaved ? `${label}${t('practice.continueSuffix')}` : label}
                  </Button>
                )
              })}
              {savedTodayRepractice && (
                <Button
                  large
                  rounded
                  tonal
                  onClick={onResume}
                  colors={{
                    tonalBgIos: 'bg-app-warning-tonal active:bg-app-warning-fill',
                    tonalTextIos: 'text-app-warning active:text-app-on-warning',
                  }}
                >
                  {t('practice.todayRepracticeContinue')}
                </Button>
              )}
              {!savedTodayRepractice && todayRepracticeCards.length > 0 && (
                <Button
                  large
                  rounded
                  tonal
                  onClick={onStartTodayRepractice}
                  colors={{
                    tonalBgIos: 'bg-app-warning-tonal active:bg-app-warning-fill',
                    tonalTextIos: 'text-app-warning active:text-app-on-warning',
                  }}
                >
                  {t('practice.todayRepractice')}
                </Button>
              )}
            </div>

            {savedMode && (savedModeIsAvailable || savedTodayRepractice) && (
              <p className="mt-6 text-center text-xs leading-relaxed text-app-label-tertiary">
                {savedTodayRepractice
                  ? t('practice.resumeHintRepractice', { mode: savedModeLabel })
                  : t('practice.resumeHintNormal', { mode: savedModeLabel })}
              </p>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}
