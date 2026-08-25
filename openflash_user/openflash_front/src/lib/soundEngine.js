let ctx = null
let enabled = true
let bgGain = null
let bgScheduled = false
let bgTimeouts = []
let bgStartToken = 0

// 判断当前运行环境是否支持 Web Audio。
function hasAudioSupport() {
  return typeof window !== 'undefined' && Boolean(window.AudioContext || window.webkitAudioContext)
}

// 懒创建音频上下文，并在被浏览器挂起时尝试恢复。
async function getCtx() {
  if (!hasAudioSupport()) return null
  if (!ctx) {
    ctx = new (window.AudioContext || window.webkitAudioContext)()
  }
  if (ctx.state === 'suspended') {
    try {
      await ctx.resume()
    } catch {
      return null
    }
  }
  return ctx
}

// 播放一个指定频率、时长、音色和目标节点的基础音调。
function playTone(ac, freq, startTime, duration, volume = 0.25, type = 'sine', destination = null) {
  const osc = ac.createOscillator()
  const gain = ac.createGain()
  osc.connect(gain)
  gain.connect(destination ?? ac.destination)
  osc.type = type
  osc.frequency.setValueAtTime(freq, startTime)
  gain.gain.setValueAtTime(volume, startTime)
  gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration)
  osc.start(startTime)
  osc.stop(startTime + duration)
}

// 设置页切换音效开关时更新内存缓存，后续点击立即按新值生效。
export function setSoundEnabled(value) {
  enabled = !!value
  if (!enabled) stopBgMusic()
}

// 播放所有普通按钮共用的短促点击音。
export async function playGenericClick() {
  if (!enabled) return
  const ac = await getCtx()
  if (!ac) return
  playTone(ac, 800, ac.currentTime, 0.05, 0.15, 'sine')
}

// 包装普通点击处理，让按钮先播放通用点击音，再执行原点击逻辑。
export function withGenericClick(handler) {
  return (...args) => {
    playGenericClick()
    return handler?.(...args)
  }
}

// 播放评分按钮音效，rating 为 0 到 3，对应从低沉到明亮上扬。
export async function playRatingSound(rating) {
  if (!enabled) return
  const ac = await getCtx()
  if (!ac) return
  const t = ac.currentTime
  if (rating === 0) {
    playTone(ac, 180, t, 0.35, 0.3, 'triangle')
  } else if (rating === 1) {
    playTone(ac, 330, t, 0.25, 0.25, 'sine')
  } else if (rating === 2) {
    playTone(ac, 550, t, 0.2, 0.25, 'sine')
  } else {
    playTone(ac, 660, t, 0.15, 0.25, 'sine')
    playTone(ac, 880, t + 0.12, 0.2, 0.25, 'sine')
  }
}

// 播放重练结果音效，记住为上升音，没记住为下降音。
export async function playRetrySound(passed) {
  if (!enabled) return
  const ac = await getCtx()
  if (!ac) return
  const osc = ac.createOscillator()
  const gain = ac.createGain()
  const t = ac.currentTime
  osc.connect(gain)
  gain.connect(ac.destination)
  osc.type = 'sine'
  if (passed) {
    osc.frequency.setValueAtTime(440, t)
    osc.frequency.linearRampToValueAtTime(660, t + 0.25)
    gain.gain.setValueAtTime(0.25, t)
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.3)
    osc.start(t)
    osc.stop(t + 0.3)
    return
  }
  osc.frequency.setValueAtTime(330, t)
  osc.frequency.linearRampToValueAtTime(180, t + 0.3)
  gain.gain.setValueAtTime(0.25, t)
  gain.gain.exponentialRampToValueAtTime(0.001, t + 0.35)
  osc.start(t)
  osc.stop(t + 0.35)
}

// 完成一张卡正反两面后播放三音上升庆祝音。
export async function playCelebration() {
  if (!enabled) return
  const ac = await getCtx()
  if (!ac) return
  const t = ac.currentTime
  playTone(ac, 523, t, 0.18, 0.3, 'sine')
  playTone(ac, 659, t + 0.14, 0.18, 0.3, 'sine')
  playTone(ac, 784, t + 0.28, 0.25, 0.3, 'sine')
}

// 进入练习结束统计页时播放五音胜利旋律。
export async function playVictory() {
  if (!enabled) return
  const ac = await getCtx()
  if (!ac) return
  const t = ac.currentTime
  ;[523, 659, 784, 659, 1047].forEach((freq, i) => {
    playTone(ac, freq, t + i * 0.15, 0.2, 0.3, 'sine')
  })
}

// 进入模式选择页时启动柔和循环背景音乐。
export async function startBgMusic() {
  if (!enabled || bgScheduled) return
  bgScheduled = true
  const token = ++bgStartToken
  const ac = await getCtx()
  if (!ac || !enabled || token !== bgStartToken || !bgScheduled) {
    if (token === bgStartToken) bgScheduled = false
    return
  }
  bgGain = ac.createGain()
  bgGain.gain.setValueAtTime(0, ac.currentTime)
  bgGain.gain.linearRampToValueAtTime(0.08, ac.currentTime + 0.5)
  bgGain.connect(ac.destination)
  bgScheduled = true

  const notes = [261, 330, 392, 523]
  const noteDuration = 0.5
  const loopDuration = notes.length * noteDuration

  function scheduleLoop(startTime) {
    if (!bgScheduled || !bgGain) return
    notes.forEach((freq, i) => {
      const start = startTime + i * noteDuration
      const osc = ac.createOscillator()
      const noteGain = ac.createGain()
      osc.type = 'sine'
      osc.frequency.setValueAtTime(freq, start)
      noteGain.gain.setValueAtTime(0.001, start)
      noteGain.gain.linearRampToValueAtTime(1, start + 0.05)
      noteGain.gain.exponentialRampToValueAtTime(0.001, start + noteDuration * 0.9)
      osc.connect(noteGain)
      noteGain.connect(bgGain)
      osc.start(start)
      osc.stop(start + noteDuration)
    })

    const delay = Math.max((startTime + loopDuration - ac.currentTime - 0.1) * 1000, 0)
    bgTimeouts.push(window.setTimeout(() => scheduleLoop(startTime + loopDuration), delay))
  }

  scheduleLoop(ac.currentTime + 0.1)
}

// 开始练习或离开模式选择页时停止背景音乐并淡出。
export async function stopBgMusic() {
  bgStartToken += 1
  if (!bgScheduled && !bgGain) return
  bgScheduled = false
  bgTimeouts.forEach(id => window.clearTimeout(id))
  bgTimeouts = []
  if (!bgGain) return
  const ac = await getCtx()
  if (!ac) {
    bgGain = null
    return
  }
  bgGain.gain.cancelScheduledValues(ac.currentTime)
  bgGain.gain.setValueAtTime(bgGain.gain.value, ac.currentTime)
  bgGain.gain.linearRampToValueAtTime(0.001, ac.currentTime + 0.5)
  bgGain = null
}
