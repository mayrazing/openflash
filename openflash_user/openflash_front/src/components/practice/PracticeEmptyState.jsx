import { Card } from 'konsta/react'
import AppNavbar from '../konsta/AppNavbar'
import NavbarBackLink from '../konsta/AppNavbarBackLink'

export default function PracticeEmptyState({ onBack, t }) {
  const backLabel = t('common.back').replace(/^←\s*/, '')

  return (
    <div className="h-full overflow-y-auto bg-app-background">
      <div className="mx-auto flex min-h-full max-w-lg flex-col">
        <AppNavbar
          title={<h1>{t('practice.noCards')}</h1>}
          left={<NavbarBackLink showText text={backLabel} onClick={onBack} />}
        />

        <div className="flex min-h-0 flex-1 items-center justify-center px-4 pb-10 pt-3 text-center">
          <Card raised outline className="!m-0 w-full max-w-sm" contentWrapPadding="px-6 py-8">
            <p className="text-base font-semibold">{t('practice.noCards')}</p>
            <p className="mt-2 text-sm text-app-label-tertiary">{t('practice.noCardsDesc')}</p>
          </Card>
        </div>
      </div>
    </div>
  )
}
