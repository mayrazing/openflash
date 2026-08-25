import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, Card, List } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import ListInput from '../components/konsta/AppListInput'
import { searchMasteredCards, removeFromMastered } from '../db/database'
import ConfirmDialog from '../components/ConfirmDialog'
import TextWithBreaks from '../components/TextWithBreaks'
import { withGenericClick } from '../lib/soundEngine'
import { stripImageTokens } from '../lib/richFaceOrder'

export default function Mastered() {
    const navigate = useNavigate()
    const { t } = useTranslation()
    const [cards, setCards] = useState([])
    const [keyword, setKeyword] = useState('')
    const [deletingId, setDeletingId] = useState(null)

    async function load(kw = '') { setCards(await searchMasteredCards(kw)) }
    useEffect(() => { load() }, [])

    function handleSearch(kw) { setKeyword(kw); load(kw) }

    async function handleRemove() {
        await removeFromMastered(deletingId); setDeletingId(null); load(keyword)
    }

    const backLabel = t('common.back').replace(/^←\s*/, '')

    return (
        <AppPage contentClassName="!pt-0">
            <AppNavbar
                title={<h1>{t('mastered.title')}</h1>}
                left={(
                    <NavbarBackLink
                        showText
                        text={backLabel}
                        onClick={withGenericClick(() => navigate('/'))}
                    />
                )}
                right={<span className="px-2 text-sm text-app-label-secondary">{t('mastered.countUnit', { count: cards.length })}</span>}
            />

            <List inset strong outline className="!mb-4 !mt-3">
                <ListInput
                    outline
                    type="search"
                    inputId="mastered-search"
                    clearButton
                    placeholder={t('mastered.searchPlaceholder')}
                    value={keyword}
                    onChange={(event) => handleSearch(event.target.value)}
                    onClear={() => handleSearch('')}
                />
            </List>

            <div className="space-y-3 px-4">
                {cards.length === 0 && (
                    <p className="py-8 text-center text-app-label-tertiary">
                        {keyword ? t('mastered.noMatchSearch') : t('mastered.noMastered')}
                    </p>
                )}
                {cards.map((card) => {
                    const thumb = card.sideAImage?.[0] ?? card.sideBImage?.[0] ?? null
                    return (
                        <Card key={card.id} raised outline className="!m-0">
                          <div className="flex items-start gap-3">
                            {thumb && <img src={thumb} alt="" className="w-12 h-12 rounded-lg object-cover border border-app-separator shrink-0" />}
                            <div className="flex-1 min-w-0">
                                <p className="text-base font-medium"><TextWithBreaks text={stripImageTokens(card.sideA) || t('common.noText')} /></p>
                                <p className="mt-0.5 text-sm text-app-label-secondary"><TextWithBreaks text={stripImageTokens(card.sideB) || t('common.noText')} /></p>
                                <p className="mt-1 text-xs text-app-label-tertiary">{t('mastered.fromDeck', { deckName: card.deckName ?? t('mastered.deckDefault'), date: card.masteredAt })}</p>
                            </div>
                            <Button
                                small
                                clear
                                rounded
                                onClick={withGenericClick(() => setDeletingId(card.id))}
                                className="k-color-brand-danger shrink-0"
                            >
                                {t('mastered.moveBack')}
                            </Button>
                          </div>
                        </Card>
                    )
                })}
            </div>

            <ConfirmDialog
                open={!!deletingId}
                message={t('mastered.moveBackConfirm')}
                onConfirm={handleRemove} onCancel={() => setDeletingId(null)}
            />
        </AppPage>
    )
}
