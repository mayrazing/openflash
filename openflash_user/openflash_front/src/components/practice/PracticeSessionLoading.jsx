import { Card, Preloader } from 'konsta/react'
import AppNavbar from '../konsta/AppNavbar'
import NavbarBackLink from '../konsta/AppNavbarBackLink'

export default function PracticeSessionLoading({ onBack, t }) {
  const backLabel = t('common.back').replace(/^←\s*/, '')

  return (
    <div className="h-full overflow-y-auto bg-app-background">
      <div className="mx-auto flex min-h-full max-w-lg flex-col">
        <AppNavbar
          title={<h1>{t('practice.loadingProgress')}</h1>}
          left={<NavbarBackLink showText text={backLabel} onClick={onBack} />}
        />

        <div className="flex min-h-0 flex-1 items-center justify-center px-4 pb-10 pt-3 text-center">
          <Card raised outline className="!m-0 w-full max-w-xs" contentWrapPadding="px-6 py-8">
            <Preloader className="mb-5 !h-9 !w-9" />
            <h2 className="mb-2 text-lg font-semibold">{t('practice.loadingProgress')}</h2>
            <p className="text-sm text-app-label-secondary">{t('practice.loadingProgressDesc')}</p>
          </Card>
        </div>
      </div>
    </div>
  )
}
