import { getDistributionCardActiveClass } from '../../lib/practiceDistribution.js'
import { createDistributionDirectionState } from '../../lib/practiceSession.js'

export default function PracticeDistributionGrid({ distribution, frameRef, activeCardId, collapsed = false }) {
  const cards = distribution?.cards ?? []
  if (cards.length === 0) return null

  return (
    // frame 用 grid-template-rows 在 0fr/1fr 间过渡实现收缩；内层 overflow-hidden 裁掉收缩后溢出
    <div
      ref={frameRef}
      className="shrink-0 rounded-lg border border-app-separator bg-app-surface-secondary shadow-inner transition-[grid-template-rows] duration-300"
      style={{ display: 'grid', gridTemplateRows: collapsed ? '0fr' : '1fr' }}
    >
      <div className="min-h-0 overflow-hidden">
        <div className="p-3">
          <div className="grid auto-rows-min grid-cols-[repeat(auto-fill,minmax(45px,1fr))] gap-1.5">
            {cards.map(card => {
              const activeCardClass = getDistributionCardActiveClass(card.cardId, activeCardId)
              return (
                <div
                  key={card.cardId}
                  title={card.label}
                  className={`grid grid-cols-2 gap-1.5 rounded-md border border-app-separator bg-app-surface-primary p-1.5 ${activeCardClass}`}
                >
                  {['a2b', 'b2a'].map(direction => (
                    <DistributionDirectionSlot
                      key={direction}
                      directionState={card.directions[direction]}
                    />
                  ))}
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}

function DistributionDirectionSlot({ directionState }) {
  const state = directionState ?? createDistributionDirectionState()
  if (!state.present) {
    return (
      <div className="h-3 rounded-[5px] border border-dashed border-app-separator bg-app-fill-secondary" />
    )
  }

  if (!state.retryActive) {
    return (
      <div
        className={`h-3 rounded-[5px] border ${state.completed ? 'border-app-success bg-app-success-fill' : 'border-app-separator bg-app-surface-tertiary'}`}
      />
    )
  }

  return (
    <div className="grid h-3 gap-0.5" style={{ gridTemplateColumns: `repeat(${state.retryCompleted.length}, minmax(0, 1fr))` }}>
      {state.retryCompleted.map((completed, index) => (
        <div
          key={index}
          className={`rounded-[4px] border ${completed ? 'border-app-success bg-app-success-fill' : 'border-app-separator bg-app-surface-tertiary'}`}
        />
      ))}
    </div>
  )
}
