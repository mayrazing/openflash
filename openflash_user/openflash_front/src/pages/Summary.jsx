import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, Card, Chip } from 'konsta/react'
import AppPage from '../components/layout/AppPage'
import AppNavbar from '../components/konsta/AppNavbar'
import NavbarBackLink from '../components/konsta/AppNavbarBackLink'
import {
    getDeckLearningStats,
    getDeckSettings,
    getDynamicPendingPracticeSummary,
    hasCountableSavedPracticeSession,
    loadPracticeSession,
} from '../db/database'
import { playVictory, withGenericClick } from '../lib/soundEngine'

// ── 记忆结束后跳转进来时用的评分分布配置 ──────────────────────
const RATING_CONFIG = [
    { key: 'again', labelKey: 'summary.ratingAgain', color: 'text-app-practice-again', bar: 'bg-app-practice-again' },
    { key: 'hard', labelKey: 'summary.ratingHard', color: 'text-app-practice-hard', bar: 'bg-app-practice-hard' },
    { key: 'good', labelKey: 'summary.ratingGood', color: 'text-app-practice-good', bar: 'bg-app-practice-good' },
    { key: 'easy', labelKey: 'summary.ratingEasy', color: 'text-app-practice-easy', bar: 'bg-app-practice-easy' },
]
const FIREWORK_COLORS = ['#f87171', '#facc15', '#4ade80', '#60a5fa', '#c084fc', '#fb7185']
const FIREWORK_CELEBRATION_DURATION_MS = 3600

// ── 通用卡片组件 ──────────────────────────────────────────────
function StatCard({ value, label, valueColor = 'text-app-label-primary' }) {
    return (
        <Card raised outline className="!m-0 text-center" contentWrapPadding="px-4 py-4">
            <p className={`text-2xl font-bold ${valueColor}`}>{value}</p>
            <p className="mt-1 text-sm text-app-label-tertiary">{label}</p>
        </Card>
    )
}

// 显示当天复习压力，帮助用户理解为什么新卡数量会变少。
function ReviewPressureNote({ backlogCount, newCardsPaused, t }) {
    if (backlogCount <= 0 && !newCardsPaused) return null
    return (
        <div className="flex flex-wrap gap-2 mb-5">
            {backlogCount > 0 && (
                <Chip
                    className="!text-xs !font-medium"
                    colors={{
                        fillBgIos: 'bg-app-warning-tonal',
                        fillTextIos: 'text-app-warning',
                    }}
                >
                    {t('summary.backlog', { count: backlogCount })}
                </Chip>
            )}
            {newCardsPaused && (
                <Chip
                    className="!text-xs !font-medium"
                    colors={{
                        fillBgIos: 'bg-app-danger-tonal',
                        fillTextIos: 'text-app-danger',
                    }}
                >
                    {t('summary.highPressure')}
                </Chip>
            )}
        </div>
    )
}

// ── 主动进入时展示的历史累计统计 ─────────────────────────────
function OverallStats({ deckId, t }) {
    const [data, setData] = useState(null)

    useEffect(() => {
        let alive = true
        async function load() {
            const [deckSettings, savedSession] = await Promise.all([
                getDeckSettings(deckId),
                loadPracticeSession(deckId),
            ])
            const stats = await getDeckLearningStats(deckId, deckSettings.newCardsPerDay)
            const pending = hasCountableSavedPracticeSession(savedSession)
                ? await getDynamicPendingPracticeSummary(deckId, deckSettings.newCardsPerDay)
                : stats
            if (!alive) return
            setData({
                total: stats.total ?? 0,
                mastered: stats.mastered ?? 0,
                pendingTotal: pending.pendingTotal ?? 0,
                pendingNew: pending.pendingNew ?? 0,
                pendingReview: pending.pendingReview ?? 0,
                todayCompletedNew: stats.todayCompletedNew ?? 0,
                todayCompletedReview: stats.todayCompletedReview ?? 0,
                backlogCount: pending.pendingBacklog ?? stats.backlogCount ?? 0,
                newCardsPaused: Boolean(pending.newCardsPaused ?? stats.newCardsPaused),
                top5: stats.topCards ?? [],
            })
        }
        load()
        return () => {
            alive = false
        }
    }, [deckId])

    if (!data) return <p className="text-center text-app-label-tertiary py-8">{t('summary.loading')}</p>

    return (
        <>
            {/* 概览数字 */}
            <div className="grid grid-cols-2 gap-3 mb-5">
                <StatCard value={data.total} label={t('summary.totalCards')} />
                <StatCard value={data.mastered} label={t('summary.mastered')} valueColor="text-app-success" />
                <StatCard value={data.todayCompletedNew} label={t('summary.todayStudiedNew')} valueColor="text-app-accent" />
                <StatCard value={data.todayCompletedReview} label={t('summary.todayReviewed')} valueColor="text-app-familiar" />
                <StatCard value={data.pendingNew} label={t('summary.todayNew')} valueColor="text-app-accent" />
                <StatCard value={data.pendingTotal} label={t('summary.todayPending')} valueColor="text-app-warning" />
            </div>
            <ReviewPressureNote backlogCount={data.backlogCount} newCardsPaused={data.newCardsPaused} t={t} />

            {/* 最难记的卡片 */}
            {data.top5.length > 0 && (
                <Card raised outline className="!m-0" header={t('summary.mostReviewed')} headerDivider>
                    <div className="space-y-2">
                        {data.top5.map((card, i) => (
                            <div key={card.id} className="flex items-center gap-3">
                                <span className="text-xs text-app-label-tertiary w-4 shrink-0">{i + 1}</span>
                                <div className="flex-1 min-w-0">
                                    <p className="text-sm font-medium text-app-label-primary truncate">{card.sideA || t('common.noText')}</p>
                                    <p className="text-xs text-app-label-tertiary truncate">{card.sideB || t('common.noText')}</p>
                                </div>
                                <span className="text-sm text-app-label-tertiary shrink-0">{t('summary.timesUnit', { count: card.reps })}</span>
                            </div>
                        ))}
                    </div>
                </Card>
            )}
        </>
    )
}

function ratingLabels(t) {
    return [
        t('summary.ratingAgain'),
        t('summary.ratingHard'),
        t('summary.ratingGood'),
        t('summary.ratingEasy'),
    ]
}

// 展示本次练习每张卡的反应时间和评分降档情况，供用户验证时间调度是否生效。
function ResponseTimingSection({ timingLog, t }) {
    if (!timingLog || timingLog.length === 0) return null
    const labels = ratingLabels(t)
    return (
        <Card raised outline className="!mx-0 !mb-4 !mt-0" header={t('summary.responseTime')} headerDivider>
            <div className="space-y-2">
                {timingLog.map((entry, i) => (
                    <div key={i} className="flex items-center gap-2 text-sm min-w-0">
                        <span className="flex-1 min-w-0 truncate text-app-label-primary">
                            {entry.sideA || t('common.noText')}
                        </span>
                        {entry.timedOut ? (
                            <span className="text-app-warning shrink-0 text-xs font-medium">{t('summary.timeoutRetry')}</span>
                        ) : (
                            <>
                                <span className="text-app-label-tertiary shrink-0 text-xs">
                                    {entry.responseTimeSec !== null ? t('summary.responseTimeSec', { sec: entry.responseTimeSec }) : '—'}
                                </span>
                                {entry.originalRating !== entry.appliedRating ? (
                                    <span className="text-app-danger shrink-0 text-xs font-medium">
                                        {labels[entry.originalRating]} → {labels[entry.appliedRating]}
                                    </span>
                                ) : (
                                    <span className="text-app-label-tertiary shrink-0 text-xs">
                                        {labels[entry.originalRating]}
                                    </span>
                                )}
                            </>
                        )}
                    </div>
                ))}
            </div>
        </Card>
    )
}

// ── 记忆结束后的单次统计 ──────────────────────────────────────
function SessionStats({ stats, timingLog = [], t }) {
    const total = stats.newCount + stats.reviewCountStat
    return (
        <>
            <div className="grid grid-cols-3 gap-3 mb-5">
                <StatCard value={total} label={t('summary.totalCards')} />
                <StatCard value={stats.newCount} label={t('summary.newCards')} valueColor="text-app-accent" />
                <StatCard value={stats.reviewCountStat} label={t('summary.reviewCards')} valueColor="text-app-familiar" />
            </div>

            <Card raised outline className="!mx-0 !mb-4 !mt-0" header={t('summary.ratingDist')} headerDivider>
                <div className="space-y-2">
                    {RATING_CONFIG.map(({ key, labelKey, color, bar }) => {
                        const count = stats[key]
                        const pct = total > 0 ? Math.round((count / total) * 100) : 0
                        return (
                            <div key={key} className="flex items-center gap-3">
                                <span className={`text-sm w-20 shrink-0 ${color}`}>{t(labelKey)}</span>
                                <div className="flex-1 h-2 bg-app-fill-secondary rounded-full overflow-hidden">
                                    <div className={`h-full rounded-full ${bar}`}
                                        style={{ width: `${pct}%`, transition: 'width 0.4s ease' }} />
                                </div>
                                <span className="text-sm text-app-label-tertiary w-8 text-right shrink-0">{count}</span>
                            </div>
                        )
                    })}
                </div>
            </Card>

            {stats.masteredCount > 0 && (
                <Card
                    outline
                    className="!mx-0 !mb-5 !mt-0"
                    contentWrapPadding="px-4 py-3"
                    colors={{
                        bgIos: 'bg-app-success-tonal',
                        outlineIos: 'border-app-success',
                    }}
                >
                  <div className="flex items-center gap-3">
                    <span className="text-2xl">🏆</span>
                    <p className="text-sm text-app-success">
                        {t('summary.masteredNotice', { count: stats.masteredCount })}
                    </p>
                  </div>
                </Card>
            )}
            <ResponseTimingSection timingLog={timingLog} t={t} />
        </>
    )
}

// 生成一组从屏幕两侧散开的烟花粒子。
function createFirework(width, height, side) {
    const originX = side === 'left' ? width * 0.18 : width * 0.82
    const originY = height * 0.72
    return Array.from({ length: 42 }, () => {
        const angle = Math.random() * Math.PI * 2
        const speed = 2.2 + Math.random() * 3.8
        return {
            x: originX,
            y: originY,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed - 2,
            life: 1,
            decay: 0.012 + Math.random() * 0.012,
            size: 2 + Math.random() * 2.5,
            color: FIREWORK_COLORS[Math.floor(Math.random() * FIREWORK_COLORS.length)],
        }
    })
}

// 原地推进粒子状态，避免动画期间每帧重建整批粒子对象。
function updateFireworkParticles(particles) {
    for (let index = particles.length - 1; index >= 0; index--) {
        const particle = particles[index]
        particle.x += particle.vx
        particle.y += particle.vy
        particle.vy += 0.06
        particle.life -= particle.decay
        if (particle.life <= 0) {
            particles.splice(index, 1)
        }
    }
}

// 练习结束跳入统计页后短暂显示烟花，到时主动释放画布和帧循环。
function FireworksCanvas({ active }) {
    const canvasRef = useRef(null)
    const [visible, setVisible] = useState(active)

    useEffect(() => {
        if (!active) {
            setVisible(false)
            return undefined
        }

        setVisible(true)
        const canvas = canvasRef.current
        if (!canvas) return undefined
        const context = canvas.getContext('2d')
        if (!context) return undefined

        let frameId = 0
        let disposed = false
        let particles = []
        let lastBurstAt = 0
        let stopTimerId = 0

        // 同步画布尺寸，避免不同屏幕上烟花位置偏移。
        function resizeCanvas() {
            const ratio = window.devicePixelRatio || 1
            canvas.width = Math.floor(window.innerWidth * ratio)
            canvas.height = Math.floor(window.innerHeight * ratio)
            canvas.style.width = `${window.innerWidth}px`
            canvas.style.height = `${window.innerHeight}px`
            context.setTransform(ratio, 0, 0, ratio, 0, 0)
        }

        // 页面离开或动画关闭时释放监听和帧循环。
        function dispose() {
            if (disposed) return
            disposed = true
            window.cancelAnimationFrame(frameId)
            window.clearTimeout(stopTimerId)
            window.removeEventListener('resize', resizeCanvas)
        }

        // 从左右两侧补一轮烟花，保持当前页面持续有粒子出现。
        function addBurst(width, height, now) {
            particles.push(
                ...createFirework(width, height, 'left'),
                ...createFirework(width, height, 'right')
            )
            lastBurstAt = now
        }

        // 推进烟花动画，当前页面未离开时循环播放。
        function animate(now) {
            if (disposed) return
            const width = window.innerWidth
            const height = window.innerHeight
            context.clearRect(0, 0, width, height)

            if (particles.length === 0 || now - lastBurstAt >= 850) {
                addBurst(width, height, now)
            }

            updateFireworkParticles(particles)

            particles.forEach((particle) => {
                context.globalAlpha = Math.max(particle.life, 0)
                context.fillStyle = particle.color
                context.beginPath()
                context.arc(particle.x, particle.y, particle.size, 0, Math.PI * 2)
                context.fill()
            })
            context.globalAlpha = 1

            frameId = window.requestAnimationFrame(animate)
        }

        resizeCanvas()
        addBurst(window.innerWidth, window.innerHeight, performance.now())
        window.addEventListener('resize', resizeCanvas)
        frameId = window.requestAnimationFrame(animate)
        stopTimerId = window.setTimeout(() => {
            dispose()
            setVisible(false)
        }, FIREWORK_CELEBRATION_DURATION_MS)

        return () => {
            dispose()
        }
    }, [active])

    if (!visible) return null

    return (
        <canvas
            ref={canvasRef}
            className="fixed inset-0 z-40 pointer-events-none"
            aria-hidden="true"
        />
    )
}

// ── 页面主体 ──────────────────────────────────────────────────
export default function Summary() {
    const { id } = useParams()
    const navigate = useNavigate()
    const location = useLocation()
    const { t } = useTranslation()
    const sessionStats = location.state?.stats   // 有值 = 记忆结束跳转，无值 = 主动进入
    const timingLog = location.state?.timingLog ?? []
    const handleBack = withGenericClick(() => navigate(`/deck/${id}`))
    const handleReturnDeck = withGenericClick(() => navigate(`/deck/${id}`))
    const backLabel = t('common.back').replace(/^←\s*/, '')

    useEffect(() => {
        if (!sessionStats) return
        playVictory()
    }, [sessionStats])

    return (
        <AppPage contentClassName="!pt-0">
            <FireworksCanvas active={Boolean(sessionStats)} />
            <AppNavbar
                title={<h1>{sessionStats ? t('summary.titleSession') : t('summary.titleDeck')}</h1>}
                left={(
                    <NavbarBackLink
                        showText
                        text={backLabel}
                        onClick={handleBack}
                    />
                )}
            />

            <div className="pt-3">
                {sessionStats
                    ? <SessionStats stats={sessionStats} timingLog={timingLog} t={t} />
                    : <OverallStats deckId={id} t={t} />
                }

                <Button large rounded className="app-primary-fill !mt-5" onClick={handleReturnDeck}>
                    {t('summary.returnToDeck')}
                </Button>
            </div>
        </AppPage>
    )
}
