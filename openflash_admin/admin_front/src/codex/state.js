const ACTIVE_LOGIN_STATES = new Set(['STARTING', 'PENDING'])

function initialState() {
  return {
    accountBusy: false,
    error: null,
    loginBusy: false,
    refreshBusy: false,
    snapshot: null,
    toggleBusy: false,
    viewState: 'loading',
  }
}

export function createCodexCoordinator({
  api,
  clearSchedule = clearTimeout,
  onChange = () => {},
  schedule = setTimeout,
}) {
  let active = false
  let enabledGeneration = 0
  let loginGeneration = 0
  let pollTimer = null
  let state = initialState()

  function update(patch) {
    state = { ...state, ...patch }
    onChange(state)
  }

  function clearPoll() {
    if (pollTimer !== null) clearSchedule(pollTimer)
    pollTimer = null
  }

  function schedulePoll() {
    clearPoll()
    if (!active || !ACTIVE_LOGIN_STATES.has(state.snapshot?.login.state)) return
    pollTimer = schedule(() => {
      pollTimer = null
      return poll()
    }, 1000)
  }

  function applyLogin(login) {
    if (!state.snapshot) return
    update({ snapshot: { ...state.snapshot, login } })
    schedulePoll()
  }

  async function poll() {
    const generation = ++loginGeneration
    try {
      const incoming = await api.getSnapshot()
      if (!active || generation !== loginGeneration || !state.snapshot) return
      update({
        error: null,
        snapshot: {
          ...state.snapshot,
          runtimeStatus: incoming.runtimeStatus,
          login: incoming.login,
          globalChangeMaxDelaySeconds: incoming.globalChangeMaxDelaySeconds,
        },
      })
      schedulePoll()
    } catch {
      if (!active || generation !== loginGeneration || !state.snapshot) return
      update({
        error: 'load',
        snapshot: {
          ...state.snapshot,
          runtimeStatus: 'ERROR',
          login: { state: 'FAILED', verificationUrl: '', userCode: '' },
        },
      })
      clearPoll()
    }
  }

  async function loadSnapshot(initial) {
    const enabledGenerationAtStart = enabledGeneration
    const toggleBusyAtStart = state.toggleBusy
    const generation = ++loginGeneration
    clearPoll()
    update(initial
      ? { error: null, viewState: 'loading' }
      : { error: null, refreshBusy: true })
    try {
      const incoming = await api.getSnapshot()
      if (!active || generation !== loginGeneration) return false
      const preserveEnabled = !initial && state.snapshot && (
        toggleBusyAtStart
        || state.toggleBusy
        || enabledGenerationAtStart !== enabledGeneration
      )
      update({
        error: null,
        refreshBusy: false,
        snapshot: preserveEnabled
          ? { ...incoming, enabled: state.snapshot.enabled }
          : incoming,
        viewState: 'ready',
      })
      schedulePoll()
      return true
    } catch {
      if (!active || generation !== loginGeneration) return false
      update(initial
        ? { error: 'load', refreshBusy: false, snapshot: null, viewState: 'error' }
        : { error: 'load', refreshBusy: false })
      return false
    }
  }

  async function runLoginAction(action) {
    if (!active || state.accountBusy || state.loginBusy || !state.snapshot) return false
    const generation = ++loginGeneration
    clearPoll()
    update({ error: null, loginBusy: true })
    try {
      const login = await action()
      if (!active || generation !== loginGeneration) return false
      applyLogin(login)
      return true
    } catch {
      if (!active || generation !== loginGeneration) return false
      applyLogin({ state: 'FAILED', verificationUrl: '', userCode: '' })
      update({ error: 'login' })
      return false
    } finally {
      if (active && generation === loginGeneration) update({ loginBusy: false })
    }
  }

  return {
    getState() {
      return state
    },
    mount() {
      active = true
      return loadSnapshot(true)
    },
    refresh() {
      if (!active || state.accountBusy || state.refreshBusy || state.loginBusy) {
        return Promise.resolve(false)
      }
      return loadSnapshot(false)
    },
    startLogin() {
      return runLoginAction(api.startLogin)
    },
    cancelLogin() {
      return runLoginAction(api.cancelLogin)
    },
    async logoutAccount() {
      if (!active || state.accountBusy || state.refreshBusy
          || state.loginBusy || !state.snapshot) return false
      update({ accountBusy: true, error: null })
      try {
        await api.logoutAccount()
        if (!active) return false
        update({
          snapshot: {
            ...state.snapshot,
            runtimeStatus: 'NOT_LOGGED_IN',
            login: { state: 'IDLE', verificationUrl: '', userCode: '' },
          },
        })
        await loadSnapshot(false)
        if (active) update({ accountBusy: false })
        return true
      } catch {
        if (active) update({ accountBusy: false, error: 'logout' })
        return false
      }
    },
    async setEnabled(enabled) {
      if (!active || state.toggleBusy || !state.snapshot) return false
      const previousEnabled = state.snapshot.enabled
      enabledGeneration += 1
      update({
        error: null,
        snapshot: { ...state.snapshot, enabled },
        toggleBusy: true,
      })
      try {
        await api.setEnabled(enabled)
        return true
      } catch {
        if (active) {
          update({
            error: 'toggle',
            snapshot: { ...state.snapshot, enabled: previousEnabled },
          })
        }
        return false
      } finally {
        if (active) update({ toggleBusy: false })
      }
    },
    unmount() {
      active = false
      loginGeneration += 1
      clearPoll()
    },
  }
}
